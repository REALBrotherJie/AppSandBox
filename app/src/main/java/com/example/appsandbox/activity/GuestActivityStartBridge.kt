package com.example.appsandbox.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import com.example.appsandbox.binder.AdapterInstallResult
import com.example.appsandbox.binder.BinderCallResult
import com.example.appsandbox.binder.BinderRoute
import com.example.appsandbox.binder.BinderServiceAdapter
import com.example.appsandbox.binder.IdentityDecision
import com.example.appsandbox.binder.MethodPolicyRegistry
import com.example.appsandbox.binder.ProxySupport
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.stub.StubActivities
import com.example.appsandbox.virtual.LaunchEnvelope
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

class GuestActivityStartBridge(
    private val context: Context,
    private val identity: RuntimeIdentity,
    private val manager: VirtualActivityManager
) : BinderServiceAdapter {
    override val serviceName = "activity_task"
    override val interfaceName = "android.app.IActivityTaskManager"

    override fun install(): AdapterInstallResult = runCatching {
        val type = Class.forName("android.app.ActivityTaskManager")
        val singleton = type.getDeclaredField("IActivityTaskManagerSingleton").apply { isAccessible = true }.get(null)
        val singletonType = Class.forName("android.util.Singleton")
        val field = singletonType.getDeclaredField("mInstance").apply { isAccessible = true }
        val original = field.get(singleton) ?: singletonType.getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
        requireNotNull(original) { "IActivityTaskManager unavailable" }
        if (Proxy.isProxyClass(original.javaClass) && original.toString().startsWith("VirtualBinderProxy(")) {
            return AdapterInstallResult(serviceName, true, true)
        }
        val iface = Class.forName(interfaceName)
        val registry = MethodPolicyRegistry()
        val startMethods = (iface.methods.asSequence() + original.javaClass.methods.asSequence())
            .map { it.name }.filter { it.startsWith("startActivit") }.toSet() +
            setOf("startActivity", "startActivities", "startActivityAsUser", "startActivityAndWait", "startActivityWithConfig")
        startMethods.forEach { name ->
            registry.register(name) { call, physical ->
                val args = call.args.copyOf()
                val reused = rewrite(call.method, args)
                BinderCallResult(if (reused) 3 else physical(args), BinderRoute.VIRTUAL, IdentityDecision.CUSTOM)
            }
        }
        val proxy = ProxySupport.create(original, iface, serviceName, identity, registry)
        field.set(singleton, proxy)
        Log.i(TAG, "activity-start-bridge installed api=${android.os.Build.VERSION.SDK_INT} instance=${identity.instanceId}")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }

    private fun rewrite(method: Method, args: Array<Any?>): Boolean {
        var deliveredLocally = false
        val intentIndex = method.parameterTypes.indices.firstOrNull { type ->
            Intent::class.java.isAssignableFrom(method.parameterTypes[type]) || method.parameterTypes[type].componentType == Intent::class.java
        } ?: args.size
        method.parameterTypes.indices.firstOrNull { index ->
            index < intentIndex && method.parameterTypes[index] == String::class.java && args[index] == identity.guestPackageName
        }?.let { args[it] = identity.hostPackageName }
        args.forEachIndexed { index, value ->
            when (value) {
                is Intent -> virtualize(value, method, args, index)?.let { result ->
                    if (result.reused) deliveredLocally = true else args[index] = result.intent
                }
                is Array<*> -> if (value.all { it is Intent }) {
                    @Suppress("UNCHECKED_CAST")
                    args[index] = (value as Array<Intent>).map { virtualize(it, method, args, index)?.intent ?: it }.toTypedArray()
                }
            }
        }
        return deliveredLocally
    }

    private data class RoutedIntent(val intent: Intent, val reused: Boolean)

    private fun virtualize(original: Intent, method: Method, args: Array<Any?>, intentIndex: Int): RoutedIntent? {
        val resolved = resolveGuest(original) ?: return null
        val target = ComponentName(identity.guestPackageName, resolved.name)
        val root = File(context.filesDir, "virtual/instances/${identity.instanceId}").canonicalFile
        val envelope = LaunchEnvelope(identity.guestPackageName, identity.instanceId, target, Intent(original), resolved,
            identity.processSlot, UUID.randomUUID().toString(), root.path)
        val stub = StubActivities.intent(context, identity.processSlot, resolved.launchMode).apply {
            action = original.action
            data = original.data
            type = original.type
            flags = original.flags
            clipData = original.clipData
            original.categories?.forEach(::addCategory)
        }
        envelope.putReferenceInto(stub)
        val tokenIndex = method.parameterTypes.indices.firstOrNull { it > intentIndex && method.parameterTypes[it] == android.os.IBinder::class.java }
        val requestIndex = method.parameterTypes.indices.firstOrNull { it > intentIndex && method.parameterTypes[it] == Int::class.javaPrimitiveType }
        val caller = tokenIndex?.let { args[it]?.toString() }
        val requestCode = requestIndex?.let { args[it] as? Int } ?: -1
        if (manager.deliverToReusable(envelope)) {
            Log.i(TAG, "VACTIVITY_ROUTE instance=${identity.instanceId} route=VIRTUAL_REUSE original=${original.toUri(0)} " +
                "target=${target.flattenToShortString()} launchMode=${resolved.launchMode}")
            return RoutedIntent(original, true)
        }
        manager.requested(envelope, requireNotNull(stub.component), caller, requestCode)
        Log.i(TAG, "VACTIVITY_ROUTE instance=${identity.instanceId} route=VIRTUAL original=${original.toUri(0)} " +
            "target=${target.flattenToShortString()} stub=${stub.component?.flattenToShortString()} launchMode=${resolved.launchMode}")
        return RoutedIntent(stub, false)
    }

    private fun resolveGuest(intent: Intent): ActivityInfo? {
        if (intent.component != null && intent.component?.packageName != identity.guestPackageName) return null
        if (intent.`package` != null && intent.`package` != identity.guestPackageName) return null
        val scoped = Intent(intent).apply { if (component == null) setPackage(identity.guestPackageName) }
        return context.packageManager.resolveActivity(scoped, 0)?.activityInfo
            ?.takeIf { it.packageName == identity.guestPackageName }
    }

    companion object { private const val TAG = "AppSandbox.M4" }
}
