package com.example.appsandbox.binder

import android.app.AppOpsManager
import android.content.Context
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.identity.SystemIdentityBridge
import com.example.appsandbox.vpm.VirtualPackageManagerService
import java.lang.reflect.Proxy
import com.example.appsandbox.stub.StubReceivers
import com.example.appsandbox.receiver.BroadcastSessionClient
import com.example.appsandbox.receiver.BroadcastSessionProvider
import com.example.appsandbox.runtime.VirtualProcessCoordinatorClient
import com.example.appsandbox.runtime.VirtualProcessKey
import com.example.appsandbox.provider.VirtualProviderManager
import com.example.appsandbox.runtime.GuestFrameworkCompatibilityPolicy

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
        setOf("registerReceiver", "registerReceiverWithFeature").forEach { name ->
            registry.register(name) { call, physical ->
                val guestCall = call.args.any { value ->
                    value == identity.guestPackageName ||
                        (value is android.content.AttributionSource && value.packageName == identity.guestPackageName)
                }
                val args = IdentityPolicy(identity).rewriteAttributionArgs(call.args.copyOf()).also { rewritten ->
                    rewritten.indices
                        .filter { call.method.parameterTypes[it] == String::class.java && rewritten[it] == identity.guestPackageName }
                        .forEach { rewritten[it] = identity.hostPackageName }
                    rewritten.indices
                        .filter { call.method.parameterTypes[it] == String::class.java }
                        .forEach {
                            rewritten[it] = GuestFrameworkCompatibilityPolicy.receiverPermission(
                                rewritten[it] as String?, identity.guestPackageName, identity.hostPackageName
                            )
                        }
                }
                val receiverIndex = call.method.parameterTypes.indexOfFirst { it.name == "android.content.IIntentReceiver" }
                val filterIndex = call.method.parameterTypes.indexOfFirst { it == android.content.IntentFilter::class.java }
                val flagsIndex = call.method.parameterTypes.indices.lastOrNull {
                    call.method.parameterTypes[it] == Int::class.javaPrimitiveType
                } ?: -1
                if (flagsIndex >= 0) {
                    val filter = args.getOrNull(filterIndex) as? android.content.IntentFilter
                    val protectedOnly = filter != null && filter.countActions() > 0 &&
                        (0 until filter.countActions()).all { isProtectedBroadcast(filter.getAction(it)) }
                    val originalFlags = args[flagsIndex] as Int
                    val translatedFlags = GuestFrameworkCompatibilityPolicy.receiverFlags(
                        guestCall,
                        android.os.Build.VERSION.SDK_INT,
                        packageService.guestTargetSdk,
                        originalFlags,
                        args.getOrNull(receiverIndex) != null,
                        protectedOnly
                    )
                    args[flagsIndex] = translatedFlags
                    Log.i(
                        "AppSandbox.M8",
                        "VRECEIVER event=COMPAT_FLAGS instance=${identity.instanceId} " +
                            "guestCall=$guestCall guestTarget=${packageService.guestTargetSdk} " +
                            "before=$originalFlags after=$translatedFlags " +
                            "hasReceiver=${args.getOrNull(receiverIndex) != null} protectedOnly=$protectedOnly"
                    )
                }
                val before = call.args.mapIndexedNotNull { index, value ->
                    if (value is String || value is android.content.AttributionSource) "$index=$value" else null
                }
                val after = args.mapIndexedNotNull { index, value ->
                    if (value is String || value is android.content.AttributionSource) "$index=$value" else null
                }
                Log.i("AppSandbox.M8", "VRECEIVER event=REGISTER method=$name before=$before after=$after instance=${identity.instanceId}")
                identityBridge.logIdentityRewrite(serviceName, name, "PHYSICAL_FOR_SYSTEM")
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        registry.register("getContentProvider") { context, physical ->
            val authority = context.args.firstOrNull { it is String && packageService.ownsProviderAuthority(it) } as? String
            val providerInfo = authority?.let(packageService::getProviderInfo)
            val remoteInfo = providerInfo?.takeIf {
                VirtualProcessKey.canonicalProcessName(identity.guestPackageName, it.processName) != identity.guestPackageName
            }
            if (remoteInfo != null) {
                val route = VirtualProcessCoordinatorClient.ensureProvider(this.context, identity.instanceId, remoteInfo)
                val binder = requireNotNull(route.providerBinder) { "remote provider returned no Transport Binder" }
                val holderType = Class.forName("android.app.ContentProviderHolder")
                val holder = holderType.getDeclaredConstructor(android.content.pm.ProviderInfo::class.java)
                    .apply { isAccessible = true }.newInstance(remoteInfo)
                val providerFactory = sequenceOf("android.content.ContentProviderNative", "android.content.IContentProvider\$Stub")
                    .mapNotNull { runCatching { Class.forName(it) }.getOrNull() }
                    .mapNotNull { type -> runCatching { type.getDeclaredMethod("asInterface", android.os.IBinder::class.java).apply { isAccessible = true } }.getOrNull() }
                    .firstOrNull() ?: error("IContentProvider Binder factory unavailable")
                val provider = providerFactory.invoke(null, binder)
                holderType.getDeclaredField("provider").apply { isAccessible = true }.set(holder, provider)
                runCatching { holderType.getDeclaredField("noReleaseNeeded").apply { isAccessible = true }.setBoolean(holder, true) }
                Log.i("AppSandbox.M10", "REMOTE_PROVIDER_ROUTE authority=$authority slot=${route.slot} pid=${route.pid} generation=${route.generation}")
                return@register BinderCallResult(providerAdapter.wrapHolder(holder), BinderRoute.VIRTUAL, IdentityDecision.VIRTUALIZE)
            }
            if (providerInfo != null) {
                val selfAuthority = requireNotNull(authority)
                val record = VirtualProviderManager.GLOBAL.findSelfProvider(
                    identity.instanceId, identity.guestPackageName, identity.virtualUid, selfAuthority
                )
                if (record != null) {
                    Log.i("AppSandbox.M8", "VPROVIDER event=SELF_ROUTE authority=$selfAuthority instance=${identity.instanceId} " +
                        "package=${identity.guestPackageName} virtualUid=${identity.virtualUid}")
                    return@register BinderCallResult(
                        providerAdapter.wrapHolder(record.provider), BinderRoute.VIRTUAL, IdentityDecision.VIRTUALIZE
                    )
                }
                Log.w("AppSandbox.M8", "VPROVIDER event=SELF_ROUTE_MISS authority=$selfAuthority instance=${identity.instanceId} " +
                    "package=${identity.guestPackageName} virtualUid=${identity.virtualUid}")
            }
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
        setOf("broadcastIntent", "broadcastIntentWithFeature", "broadcastIntentWithFeatureAsUser").forEach { name ->
            registry.register(name) { call, physical ->
                val index = call.args.indexOfFirst { it is android.content.Intent }
                val original = call.args.getOrNull(index) as? android.content.Intent
                if (original == null || index < 0) {
                    BinderCallStats.record(serviceName, name)
                    return@register BinderCallResult(physical(call.args), BinderRoute.PHYSICAL, IdentityDecision.PASSTHROUGH)
                }
                val component = original.component
                val guestScoped = component?.packageName == identity.guestPackageName || original.`package` == identity.guestPackageName
                if (!guestScoped) {
                    BinderCallStats.record(serviceName, name)
                    return@register BinderCallResult(physical(call.args), BinderRoute.PHYSICAL, IdentityDecision.PASSTHROUGH)
                }
                val resolved = if (component != null) listOf(component) else {
                    @Suppress("DEPRECATION")
                    context.packageManager.queryBroadcastReceivers(original, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
                        .mapNotNull { it.activityInfo?.let { info -> android.content.ComponentName(info.packageName, info.name) } }
                }
                if (resolved.isEmpty()) {
                    val args = IdentityPolicy(identity).rewriteAttributionArgs(call.args.copyOf()).apply {
                        this[index] = android.content.Intent(original).setPackage(identity.hostPackageName)
                        indices.filter { call.method.parameterTypes[it] == String::class.java && this[it] == identity.guestPackageName }
                            .forEach { this[it] = identity.hostPackageName }
                    }
                    Log.i("AppSandbox.M8", "VRECEIVER event=DYNAMIC_ROUTE method=$name instance=${identity.instanceId} action=${original.action} physicalPackage=${identity.hostPackageName}")
                    return@register BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
                }
                val infos = resolved.mapNotNull(packageService::getReceiverInfo)
                if (infos.isEmpty() || infos.size > StubReceivers.MAX_RANKS) {
                    Log.e("AppSandbox.M8", "VRECEIVER event=UNSUPPORTED_RECEIVER_COUNT count=${infos.size} action=${original.action}")
                    return@register BinderCallResult(physical(call.args), BinderRoute.PHYSICAL, IdentityDecision.PASSTHROUGH)
                }
                val ordered = call.method.parameterTypes.indices
                    .firstOrNull { call.method.parameterTypes[it] == Boolean::class.javaPrimitiveType }
                    ?.let { call.args[it] as? Boolean } == true
                val logicalProcesses = infos.map { VirtualProcessKey.canonicalProcessName(identity.guestPackageName, it.processName) }.distinct()
                val targetSlots = infos.map { info ->
                    if (VirtualProcessKey.canonicalProcessName(identity.guestPackageName, info.processName) != identity.guestPackageName)
                        VirtualProcessCoordinatorClient.ensureReceiver(context, identity.instanceId, info).slot
                    else identity.processSlot
                }
                val sessionId = BroadcastSessionClient.create(context, identity, original, infos, ordered, targetSlots)
                val routed = android.content.Intent().apply {
                    if (infos.size == 1) this.component = StubReceivers.component(targetSlots.single(), 1, 0)
                    else {
                        action = if (targetSlots.distinct().size == 1) StubReceivers.action(targetSlots.first(), infos.size)
                            else StubReceivers.multiProcessAction(infos.size)
                        setPackage(identity.hostPackageName)
                    }
                    putExtra(BroadcastSessionProvider.EXTRA_SESSION_ID, sessionId)
                }
                val args = IdentityPolicy(identity).rewriteAttributionArgs(call.args.copyOf()).apply {
                    this[index] = routed
                    indices.filter { call.method.parameterTypes[it] == String::class.java && this[it] == identity.guestPackageName }
                        .forEach { this[it] = identity.hostPackageName }
                }
                Log.i("AppSandbox.M8", "VRECEIVER event=SESSION_ROUTE method=$name instance=${identity.instanceId} session=$sessionId ordered=$ordered slots=$targetSlots logicalProcesses=$logicalProcesses guests=${infos.map { it.name }} action=${routed.action} component=${routed.component}")
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
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
        registry.register("getRunningAppProcesses") { call, physical ->
            @Suppress("UNCHECKED_CAST")
            val all = physical(call.args) as? List<android.app.ActivityManager.RunningAppProcessInfo>
            // The Host UID's processes are the Host and every stub slot. A Guest only sees itself,
            // under its logical process name; Apps (Tinker killSubsProcess) kill "other" names.
            val visible = all?.let {
                GuestFrameworkCompatibilityPolicy.guestRunningProcesses(
                    it, android.os.Process.myPid(), android.app.Application.getProcessName(), identity.guestPackageName
                )
            }
            BinderCallResult(visible, BinderRoute.VIRTUAL, IdentityDecision.VIRTUALIZE)
        }
        registry.register("getHistoricalProcessExitReasons") { call, physical ->
            if (call.args.firstOrNull() != identity.guestPackageName) {
                return@register BinderCallResult(physical(call.args), BinderRoute.PHYSICAL, IdentityDecision.PASSTHROUGH)
            }
            // AMS keys exit history by the Host UID, so it would either demand DUMP (Guest package) or
            // expose every clone's exits (Host package). The Guest has no recorded exits of its own.
            val slice = call.method.returnType.declaredConstructors
                .first { it.parameterTypes.contentEquals(arrayOf(List::class.java)) }
                .apply { isAccessible = true }.newInstance(emptyList<Any>())
            BinderCallResult(slice, BinderRoute.VIRTUAL, IdentityDecision.VIRTUALIZE)
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

    private fun isProtectedBroadcast(action: String): Boolean = runCatching {
        val globals = Class.forName("android.app.AppGlobals")
        val pm = globals.getDeclaredMethod("getPackageManager").invoke(null)
        pm.javaClass.methods.first { it.name == "isProtectedBroadcast" && it.parameterCount == 1 }
            .invoke(pm, action) as Boolean
    }.getOrDefault(false)
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
