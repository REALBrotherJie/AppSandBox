package com.example.appsandbox.binder

import android.app.NotificationManager
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.hidden.HiddenApiAccess
import java.lang.reflect.InvocationTargetException
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
        listOf(
            "getNotificationChannel",
            "createNotificationChannels",
            "enqueueNotificationWithTag",
            "cancelNotificationWithTag",
            "enqueueToast",
            "enqueueTextToast",
            "cancelToast",
            "finishToken"
        ).forEach { name ->
            registry.register(name) { call, physical ->
                val args = NotificationIdentityPolicy(identity).physicalArgs(call.args)
                if (!args.contentEquals(call.args)) {
                    Log.i(
                        "AppSandbox.M2.1",
                        "CONTEXT_SYSTEM_IDENTITY service=notification method=$name instance=${identity.instanceId} " +
                            "logical=${identity.guestPackageName}/${identity.virtualUidNumber} " +
                            "physical=${identity.hostPackageName}/${identity.hostUid}"
                    )
                }
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            }
        }
        val proxy = ProxySupport.create(original, iface, serviceName, identity, registry)
        field.set(null, proxy)
        HiddenApiAccess.probe().getOrThrow()
        installServiceManagerFacade(proxy as IInterface, (original as IInterface).asBinder())
        Log.i("AppSandbox.M7", "VSERVICE notification identity adapter installed")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }

    private fun installServiceManagerFacade(service: IInterface, original: IBinder) {
        val binder = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { proxy, method, args ->
            when {
                method.name == "queryLocalInterface" -> service
                method.name == "equals" && method.parameterCount == 1 -> proxy === args?.firstOrNull()
                method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(proxy)
                method.name == "toString" && method.parameterCount == 0 -> "NotificationIdentityBinder($original)"
                else -> try {
                    method.invoke(original, *(args ?: emptyArray()))
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            }
        } as IBinder
        val cacheField = Class.forName("android.os.ServiceManager").getDeclaredField("sCache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(null) as MutableMap<String, IBinder>
        cache[serviceName] = binder
        Log.i("AppSandbox.M7", "VSERVICE notification ServiceManager facade installed")
    }
}

internal class NotificationIdentityPolicy(private val identity: RuntimeIdentity) {
    fun physicalArgs(source: Array<Any?>): Array<Any?> = source.copyOf().also { args ->
        args.indices.filter { args[it] is String && args[it] == identity.guestPackageName }
            .forEach { args[it] = identity.hostPackageName }
    }
}
