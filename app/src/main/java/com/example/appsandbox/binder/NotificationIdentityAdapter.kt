package com.example.appsandbox.binder

import android.app.NotificationManager
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import java.lang.reflect.Proxy

/** Physical package translation needed by a real Guest foreground Service; not notification virtualization. */
class NotificationIdentityAdapter(private val identity: RuntimeIdentity) : BinderServiceAdapter {
    override val serviceName = "notification"
    override val interfaceName = "android.app.INotificationManager"

    override fun install(): AdapterInstallResult = runCatching {
        val field = NotificationManager::class.java.getDeclaredField("sService").apply { isAccessible = true }
        val getter = NotificationManager::class.java.getDeclaredMethod("getService").apply { isAccessible = true }
        val original = field.get(null) ?: getter.invoke(null) ?: error("INotificationManager unavailable")
        if (Proxy.isProxyClass(original.javaClass) && original.toString().startsWith("VirtualBinderProxy(")) {
            return AdapterInstallResult(serviceName, true, true)
        }
        val registry = MethodPolicyRegistry()
        val iface = Class.forName(interfaceName)
        listOf("getNotificationChannel", "createNotificationChannels", "enqueueNotificationWithTag", "cancelNotificationWithTag").forEach { name ->
            registry.register(name) { call, physical ->
                val args = call.args.copyOf()
                args.indices.filter { args[it] is String && args[it] == identity.guestPackageName }.forEach { args[it] = identity.hostPackageName }
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        field.set(null, ProxySupport.create(original, iface, serviceName, identity, registry))
        Log.i("AppSandbox.M7", "VSERVICE notification identity adapter installed")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }
}
