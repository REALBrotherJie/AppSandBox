package com.example.appsandbox.binder

import android.app.AppOpsManager
import android.content.Context
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.identity.SystemIdentityBridge
import com.example.appsandbox.vpm.VirtualPackageManagerService
import java.lang.reflect.Proxy

class ActivityManagerAdapter(
    private val context: Context,
    private val identity: RuntimeIdentity,
    private val identityBridge: SystemIdentityBridge,
    private val providerAdapter: ContentProviderIdentityAdapter,
    private val packageService: VirtualPackageManagerService
) : BinderServiceAdapter {
    override val serviceName = "activity"
    override val interfaceName = "android.app.IActivityManager"

    override fun install(): AdapterInstallResult = runCatching {
        val manager = Class.forName("android.app.ActivityManager")
        val singleton = manager.getDeclaredField("IActivityManagerSingleton").apply { isAccessible = true }.get(null)
        val singletonClass = Class.forName("android.util.Singleton")
        val instanceField = singletonClass.getDeclaredField("mInstance").apply { isAccessible = true }
        val original = instanceField.get(singleton) ?: singletonClass.getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
        requireNotNull(original) { "IActivityManager unavailable" }
        if (Proxy.isProxyClass(original.javaClass) && original.toString().startsWith("VirtualBinderProxy(")) {
            return AdapterInstallResult(serviceName, true, true)
        }
        val iface = Class.forName(interfaceName)
        val registry = MethodPolicyRegistry()
        registry.register("getContentProvider") { context, physical ->
            val rewritten = IdentityPolicy(identity).rewritePackageUidAt(context.args, setOf(1), emptySet())
            val result = physical(rewritten)
            if (result != null) providerAdapter.wrapHolder(result)
            identityBridge.logIdentityRewrite(serviceName, context.methodName, "PHYSICAL_FOR_SYSTEM")
            BinderCallResult(result, BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
        }
        registry.register("checkPermission") { context, physical ->
            val permission = context.args.firstOrNull() as? String
            val uid = context.args.getOrNull(2) as? Int
            if (permission != null && (uid == identity.hostUid || uid == identity.virtualUidNumber)) {
                BinderCallResult(packageService.checkPermission(permission, identity.guestPackageName), BinderRoute.VIRTUAL, IdentityDecision.VIRTUALIZE)
            } else {
                val rewritten = IdentityPolicy(identity).rewritePackageUidAt(context.args, emptySet(), setOf(2))
                identityBridge.logIdentityRewrite(serviceName, context.methodName, "PHYSICAL_UID_FOR_SYSTEM")
                BinderCallResult(physical(rewritten), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        setOf("startService", "startForegroundService", "stopService", "bindService", "bindServiceInstance", "bindIsolatedService").forEach { name ->
            registry.register(name) { call, physical ->
                val intentIndex = call.args.indexOfFirst { it is android.content.Intent }
                val originalIntent = call.args.getOrNull(intentIndex) as? android.content.Intent
                val routed = originalIntent?.let { com.example.appsandbox.service.VirtualServiceRuntime.route(context, identity, packageService, it) }
                if (routed == null) return@register BinderCallResult(physical(call.args), BinderRoute.PHYSICAL, IdentityDecision.PASSTHROUGH)
                val args = call.args.copyOf().apply { this[intentIndex] = routed }
                call.method.parameterTypes.indices.filter { call.method.parameterTypes[it] == String::class.java && args[it] == identity.guestPackageName }
                    .forEach { args[it] = identity.hostPackageName }
                if (name.startsWith("bind")) {
                    val connectionIndex = call.method.parameterTypes.indexOfFirst { it.name == "android.app.IServiceConnection" }
                    val connection = args.getOrNull(connectionIndex)
                    if (connectionIndex >= 0) args[connectionIndex] = com.example.appsandbox.service.VirtualServiceRuntime.wrapConnection(
                        connection, requireNotNull(originalIntent.component), call.method.parameterTypes[connectionIndex])
                }
                identityBridge.logIdentityRewrite(serviceName, name, "PHYSICAL_FOR_SYSTEM")
                BinderCallResult(com.example.appsandbox.service.VirtualServiceRuntime.logicalResult(physical(args), routed), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        setOf("stopServiceToken", "setServiceForeground", "publishService", "unbindFinished").forEach { name ->
            registry.register(name) { call, physical ->
                val args = com.example.appsandbox.service.VirtualServiceRuntime.restoreCallbackArgs(name, call.args)
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        registry.register("unbindService") { call, physical ->
            val args = call.args.copyOf()
            val index = call.method.parameterTypes.indexOfFirst { it.name == "android.app.IServiceConnection" }
            if (index >= 0) args[index] = com.example.appsandbox.service.VirtualServiceRuntime.facadeFor(args[index])
            BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
        }
        setOf("getIntentSender", "getIntentSenderWithFeature").forEach { name ->
            registry.register(name) { call, physical ->
                val args = call.args.copyOf()
                args.indices.filter { args[it] is String && args[it] == identity.guestPackageName }.forEach { args[it] = identity.hostPackageName }
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        val proxy = ProxySupport.create(original, iface, serviceName, identity, registry)
        instanceField.set(singleton, proxy)
        Log.i("AppSandbox.M6", "VBINDER_INSTALL service=$serviceName interface=$interfaceName instance=${identity.instanceId} result=PASS")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }
}

class AppOpsAdapter(private val context: Context, private val identity: RuntimeIdentity, private val identityBridge: SystemIdentityBridge) : BinderServiceAdapter {
    override val serviceName = "appops"
    override val interfaceName = "com.android.internal.app.IAppOpsService"

    override fun install(): AdapterInstallResult = runCatching {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: error("AppOpsManager unavailable")
        val field = generateSequence<Class<*>>(manager.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mService") }.getOrNull() }.first().apply { isAccessible = true }
        val original = field.get(manager) ?: error("IAppOpsService unavailable")
        if (Proxy.isProxyClass(original.javaClass) && original.toString().startsWith("VirtualBinderProxy(")) {
            return AdapterInstallResult(serviceName, true, true)
        }
        val iface = Class.forName(interfaceName)
        val policy = IdentityPolicy(identity)
        val registry = MethodPolicyRegistry()
        setOf("checkOperation", "checkOperationRaw", "noteOperation", "startOperation", "finishOperation", "checkPackage").forEach { name ->
            registry.register(name) { call, physical ->
                val uidIndex = when (name) {
                    "checkPackage" -> 0
                    "startOperation", "finishOperation" -> 2
                    else -> 1
                }
                val packageIndex = when (name) {
                    "checkPackage" -> 1
                    "startOperation", "finishOperation" -> 3
                    else -> 2
                }
                val packageIndexes = setOf(packageIndex).filterTo(mutableSetOf()) { it in call.args.indices && call.method.parameterTypes[it] == String::class.java }
                val uidIndexes = setOf(uidIndex).filterTo(mutableSetOf()) { it in call.args.indices && call.method.parameterTypes[it] == Int::class.javaPrimitiveType }
                val rewritten = policy.rewritePackageUidAt(call.args, packageIndexes, uidIndexes)
                identityBridge.logIdentityRewrite(serviceName, name, "PHYSICAL_FOR_SYSTEM")
                BinderCallResult(physical(rewritten), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        field.set(manager, ProxySupport.create(original, iface, serviceName, identity, registry))
        Log.i("AppSandbox.M6", "VBINDER_INSTALL service=$serviceName interface=$interfaceName instance=${identity.instanceId} result=PASS")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }
}
