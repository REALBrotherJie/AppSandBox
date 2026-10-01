package com.example.appsandbox.binder

import android.util.Log
import com.example.appsandbox.BuildConfig
import com.example.appsandbox.identity.RuntimeIdentity
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal object ProxySupport {
    fun create(
        original: Any,
        iface: Class<*>,
        serviceName: String,
        identity: RuntimeIdentity,
        registry: MethodPolicyRegistry
    ): Any = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxy, method, rawArgs ->
        objectMethod(proxy, method, rawArgs, serviceName, original)?.let { return@newProxyInstance it }
        val args = rawArgs ?: emptyArray()
        val context = BinderCallContext(serviceName, iface.name, method, identity, args)
        BinderCallStats.record(serviceName, method.name)
        try {
            val result = registry.invoke(context) { values -> method.invoke(original, *values) }
            if (BuildConfig.DEBUG) runCatching { Log.i("AppSandbox.M6", "VBINDER api=${context.apiLevel} service=$serviceName " +
                "interface=${iface.simpleName} method=${method.name} package=${identity.guestPackageName} instance=${identity.instanceId} " +
                "virtualUid=${identity.virtualUidNumber} route=${result.route} identity=${result.identityDecision} result=PASS") }
            result.value
        } catch (error: InvocationTargetException) {
            throw error.targetException
        } catch (error: Throwable) {
            runCatching { Log.e("AppSandbox.M6", "VBINDER api=${context.apiLevel} service=$serviceName method=${method.name} " +
                "instance=${identity.instanceId} result=FAIL error=${error.javaClass.name}:${error.message}") }
            throw error
        }
    }

    private fun objectMethod(proxy: Any, method: Method, args: Array<out Any?>?, service: String, original: Any): Any? = when {
        method.name == "equals" && method.parameterCount == 1 -> proxy === args?.firstOrNull()
        method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(proxy)
        method.name == "toString" && method.parameterCount == 0 -> "VirtualBinderProxy($service -> $original)"
        else -> null
    }
}
