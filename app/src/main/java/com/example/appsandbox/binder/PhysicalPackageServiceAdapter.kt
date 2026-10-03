package com.example.appsandbox.binder

import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * For system services that only check "calling package belongs to the calling UID": Guest code runs
 * under the Host UID, so the exact Guest package argument is sent as the Host package. Installed as a
 * ServiceManager cache entry before the Guest creates its manager, so the framework's own
 * Stub.asInterface() picks up the translating proxy without touching manager internals.
 */
class PhysicalPackageServiceAdapter(
    private val identity: RuntimeIdentity,
    override val serviceName: String,
    override val interfaceName: String,
    /** An optional facade that cannot be installed (service absent on this build) does not block the Guest. */
    private val optional: Boolean = false
) : BinderServiceAdapter {
    override fun install(): AdapterInstallResult = runCatching {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val cacheField = serviceManager.getDeclaredField("sCache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(null) as MutableMap<String, IBinder>
        cache[serviceName]?.let { if (it.toString().startsWith(FACADE)) return AdapterInstallResult(serviceName, true, true) }
        val raw = serviceManager.getDeclaredMethod("getService", String::class.java).invoke(null, serviceName) as? IBinder
            ?: error("$serviceName unavailable")
        val iface = Class.forName(interfaceName)
        val original = Class.forName("$interfaceName\$Stub").getDeclaredMethod("asInterface", IBinder::class.java)
            .invoke(null, raw) ?: error("$interfaceName.Stub.asInterface returned null")
        // Hidden-API filtering can hide the interface's methods from reflection, so translate every call.
        val registry = MethodPolicyRegistry(fallback = { call, physical ->
            val args = physicalPackageArgs(call.args, identity.guestPackageName, identity.hostPackageName)
            if (!args.contentEquals(call.args)) {
                Log.i("AppSandbox.M2.1", "CONTEXT_SYSTEM_IDENTITY service=$serviceName method=${call.methodName} instance=${identity.instanceId} " +
                    "logical=${identity.guestPackageName} physical=${identity.hostPackageName}/${identity.hostUid}")
            }
            BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
        })
        val service = ProxySupport.create(original, iface, serviceName, identity, registry) as IInterface
        cache[serviceName] = facade(service, raw)
        Log.i("AppSandbox.M7", "VSERVICE $serviceName physical-package facade installed")
        AdapterInstallResult(serviceName, true)
    }.getOrElse {
        if (optional) {
            Log.w("AppSandbox.M7", "VSERVICE $serviceName optional facade skipped: $it")
            AdapterInstallResult(serviceName, true, failureReason = it.toString())
        } else AdapterInstallResult(serviceName, false, failureReason = it.toString())
    }

    private fun facade(service: IInterface, original: IBinder): IBinder =
        Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { proxy, method, args ->
            when {
                method.name == "queryLocalInterface" -> service
                method.name == "equals" && method.parameterCount == 1 -> proxy === args?.firstOrNull()
                method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(proxy)
                method.name == "toString" && method.parameterCount == 0 -> "$FACADE$serviceName($original)"
                else -> try {
                    method.invoke(original, *(args ?: emptyArray()))
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            }
        } as IBinder

    companion object {
        private const val FACADE = "PhysicalPackageBinder:"

        fun physicalPackageArgs(source: Array<Any?>, guestPackage: String, hostPackage: String): Array<Any?> =
            source.copyOf().also { args ->
                args.indices.filter { args[it] is String && args[it] == guestPackage }.forEach { args[it] = hostPackage }
            }
    }
}
