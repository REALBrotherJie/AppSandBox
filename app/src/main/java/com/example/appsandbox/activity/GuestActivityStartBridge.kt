package com.example.appsandbox.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.stub.StubActivities
import com.example.appsandbox.virtual.LaunchEnvelope
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

class GuestActivityStartBridge(
    private val context: Context,
    private val identity: RuntimeIdentity,
    private val manager: VirtualActivityManager
) {
    fun install() {
        val type = Class.forName("android.app.ActivityTaskManager")
        val singleton = type.getDeclaredField("IActivityTaskManagerSingleton").apply { isAccessible = true }.get(null)
        val singletonType = Class.forName("android.util.Singleton")
        val field = singletonType.getDeclaredField("mInstance").apply { isAccessible = true }
        val original = field.get(singleton) ?: singletonType.getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
        if (original == null || Proxy.isProxyClass(original.javaClass)) return
        val iface = Class.forName("android.app.IActivityTaskManager")
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, rawArgs ->
            val args = rawArgs ?: emptyArray()
            try {
                if (method.name.startsWith("startActivit") && rewrite(method, args)) return@newProxyInstance 3
                method.invoke(original, *args)
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        }
        field.set(singleton, proxy)
        Log.i(TAG, "activity-start-bridge installed api=${android.os.Build.VERSION.SDK_INT} instance=${identity.instanceId}")
    }

    private fun rewrite(method: Method, args: Array<Any?>): Boolean {
        var deliveredLocally = false
        args.indices.forEach { index ->
            if (args[index] == identity.guestPackageName) args[index] = identity.hostPackageName
        }
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
