package com.example.appsandbox.binder

import android.os.IBinder
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.identity.SystemIdentityBridge
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class ContentProviderIdentityAdapter(
    private val identity: RuntimeIdentity,
    private val identityBridge: SystemIdentityBridge
) {
    private val policy = IdentityPolicy(identity)

    fun wrapHolder(holder: Any): Any {
        val providerField = generateSequence(holder.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("provider") }.getOrNull() }.firstOrNull() ?: return holder
        providerField.isAccessible = true
        val provider = providerField.get(holder) ?: return holder
        providerField.set(holder, wrap(provider))
        return holder
    }

    fun wrap(provider: Any): Any {
        if (Proxy.isProxyClass(provider.javaClass) && provider.toString().startsWith("VirtualBinderProxy(")) return provider
        val iface = Class.forName("android.content.IContentProvider")
        val registry = MethodPolicyRegistry()
        val providerMethods = (provider.javaClass.methods.asSequence() + iface.methods.asSequence())
            .map { it.name }.toSet() + setOf("call", "query", "insert", "bulkInsert", "delete", "update", "openFile",
            "openAssetFile", "openTypedAssetFile", "getType", "getStreamTypes", "canonicalize", "uncanonicalize", "refresh")
        providerMethods.filterNot { it == "asBinder" }.forEach { name -> registry.register(name) { call, physical ->
            val rewritten = policy.rewriteAttributionArgs(call.args)
            identityBridge.logIdentityRewrite("content", name, "PHYSICAL_FOR_SYSTEM")
            BinderCallResult(physical(rewritten), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
        } }
        lateinit var virtualProvider: Any
        registry.register("asBinder") { _, physical ->
            val physicalBinder = physical(emptyArray()) as IBinder
            val binderProxy = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { proxy, method, args ->
                when (method.name) {
                    "queryLocalInterface" -> virtualProvider
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "VirtualContentProviderBinder($physicalBinder)"
                    else -> try {
                        method.invoke(physicalBinder, *(args ?: emptyArray()))
                    } catch (error: InvocationTargetException) {
                        throw error.targetException
                    }
                }
            } as IBinder
            BinderCallResult(binderProxy, BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
        }
        virtualProvider = ProxySupport.create(provider, iface, "content", identity, registry)
        return virtualProvider
    }
}
