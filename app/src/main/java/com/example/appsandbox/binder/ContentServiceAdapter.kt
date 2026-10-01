package com.example.appsandbox.binder

import android.content.AttributionSource
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.identity.SystemIdentityBridge
import com.example.appsandbox.vpm.VirtualPackageManagerService
import com.example.appsandbox.provider.VirtualProviderManager
import java.lang.reflect.Proxy

/** Keeps ContentService calls physical while local Guest providers remain in ActivityThread. */
class ContentServiceAdapter(
    private val context: Context,
    private val identity: RuntimeIdentity,
    private val identityBridge: SystemIdentityBridge,
    private val packageService: VirtualPackageManagerService
) : BinderServiceAdapter {
    override val serviceName = "content_service"
    override val interfaceName = "android.content.IContentService"

    override fun install(): AdapterInstallResult = runCatching {
        val resolver = ContentResolver::class.java
        val field = resolver.getDeclaredField("sContentService").apply { isAccessible = true }
        val getter = resolver.getDeclaredMethod("getContentService").apply { isAccessible = true }
        val original = field.get(null) ?: getter.invoke(null) ?: error("IContentService unavailable")
        if (Proxy.isProxyClass(original.javaClass) && original.toString().startsWith("VirtualBinderProxy(")) {
            return AdapterInstallResult(serviceName, true, true)
        }
        val iface = Class.forName(interfaceName)
        val policy = IdentityPolicy(identity)
        val registry = MethodPolicyRegistry()
        val names = iface.methods.map { it.name }.toMutableSet().apply {
            add("notifyChange")
            add("registerContentObserver")
        }
        names.forEach { name ->
            registry.register(name, BinderMethodPolicy { call, physical ->
                val uris = call.args.flatMap { value ->
                    when (value) {
                        is Uri -> listOf(value)
                        is Array<*> -> value.filterIsInstance<Uri>()
                        is Collection<*> -> value.filterIsInstance<Uri>()
                        else -> emptyList()
                    }
                }
                if ((name == "notifyChange" || name == "registerContentObserver") && uris.any {
                        packageService.ownsProviderAuthority(it.authority) ||
                            VirtualProviderManager.GLOBAL.ownsAuthority(identity.instanceId, it.authority)
                    }) {
                    Log.i("AppSandbox.M8", "VPROVIDER event=LOCAL_CONTENT_SERVICE method=$name authority=${uris.mapNotNull { it.authority }.distinct()} instance=${identity.instanceId}")
                    return@BinderMethodPolicy BinderCallResult(null, BinderRoute.VIRTUAL, IdentityDecision.KEEP_LOGICAL)
                }
                val args = policy.rewriteAttributionArgs(call.args.copyOf()).also { rewritten ->
                    rewritten.indices.filter { rewritten[it] == identity.guestPackageName }
                        .forEach { rewritten[it] = identity.hostPackageName }
                }
                if (call.args.any { it is AttributionSource } || call.args.any { it == identity.guestPackageName }) {
                    identityBridge.logIdentityRewrite(serviceName, name, "PHYSICAL_FOR_SYSTEM")
                }
                BinderCallResult(physical(args), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
            })
        }
        val proxy = ProxySupport.create(original, iface, serviceName, identity, registry)
        field.set(null, proxy)
        generateSequence<Class<*>>(context.contentResolver.javaClass) { it.superclass }.forEach { type ->
            type.declaredFields.filter { iface.isAssignableFrom(it.type) }.forEach { serviceField ->
                serviceField.isAccessible = true
                runCatching { serviceField.set(context.contentResolver, proxy) }
            }
        }
        Log.i("AppSandbox.M6", "VBINDER_INSTALL service=$serviceName interface=$interfaceName instance=${identity.instanceId} result=PASS")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }
}
