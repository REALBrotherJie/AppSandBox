package com.example.appsandbox.binder

import android.os.Build
import com.example.appsandbox.identity.RuntimeIdentity
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

enum class BinderRoute { VIRTUAL, PHYSICAL, SYSTEM, UNSUPPORTED }
enum class IdentityDecision { KEEP_LOGICAL, USE_PHYSICAL, VIRTUALIZE, PASSTHROUGH, CUSTOM }
enum class UnknownMethodBehavior { PHYSICAL_PASSTHROUGH, EXPLICIT_BLOCK }

data class BinderCallContext(
    val serviceName: String,
    val interfaceName: String,
    val method: Method,
    val identity: RuntimeIdentity,
    val args: Array<Any?>
) {
    val apiLevel: Int = Build.VERSION.SDK_INT
    val methodName: String get() = method.name
    val processName: String get() = "${identity.hostPackageName}:p${identity.processSlot}"
}

data class BinderCallResult(val value: Any?, val route: BinderRoute, val identityDecision: IdentityDecision)

fun interface BinderMethodPolicy {
    fun invoke(context: BinderCallContext, physical: (Array<Any?>) -> Any?): BinderCallResult
}

class MethodPolicyRegistry(
    private val unknownBehavior: UnknownMethodBehavior = UnknownMethodBehavior.PHYSICAL_PASSTHROUGH,
    /** Applied to every method without its own policy; for interfaces whose methods hidden-API filtering hides. */
    private val fallback: BinderMethodPolicy? = null
) {
    private val policies = ConcurrentHashMap<String, BinderMethodPolicy>()

    fun register(methodName: String, policy: BinderMethodPolicy): MethodPolicyRegistry = apply {
        policies[methodName] = policy
    }

    fun find(methodName: String): BinderMethodPolicy? = policies[methodName]

    fun invoke(context: BinderCallContext, physical: (Array<Any?>) -> Any?): BinderCallResult {
        val policy = find(context.methodName)
        if (policy != null) return policy.invoke(context, physical)
        if (fallback != null) return fallback.invoke(context, physical)
        if (unknownBehavior == UnknownMethodBehavior.EXPLICIT_BLOCK) {
            throw UnsupportedOperationException("No Binder policy for ${context.serviceName}.${context.methodName}")
        }
        return BinderCallResult(physical(context.args), BinderRoute.PHYSICAL, IdentityDecision.PASSTHROUGH)
    }
}

interface BinderServiceAdapter {
    val serviceName: String
    val interfaceName: String
    fun supports(api: Int = Build.VERSION.SDK_INT): Boolean = api >= 28
    fun install(): AdapterInstallResult
}

data class AdapterInstallResult(val service: String, val installed: Boolean, val alreadyInstalled: Boolean = false, val failureReason: String? = null)

class BinderAdapterRegistry(adapters: List<BinderServiceAdapter> = emptyList()) {
    private val installed = AtomicBoolean(false)
    private val adapters = LinkedHashMap<String, BinderServiceAdapter>()

    init { adapters.forEach(::register) }

    fun register(adapter: BinderServiceAdapter) {
        require(this.adapters.putIfAbsent(adapter.serviceName, adapter) == null) { "duplicate Binder adapter ${adapter.serviceName}" }
    }

    fun serviceNames(): Set<String> = adapters.keys.toSet()

    fun installAll(api: Int = Build.VERSION.SDK_INT): List<AdapterInstallResult> {
        if (!installed.compareAndSet(false, true)) return adapters.values.map { AdapterInstallResult(it.serviceName, true, true) }
        return adapters.values.map { adapter ->
            if (adapter.supports(api)) adapter.install()
            else AdapterInstallResult(adapter.serviceName, false, failureReason = "unsupported api=$api")
        }
    }
}

object BinderCallStats {
    private val counts = ConcurrentHashMap<String, AtomicLong>()
    fun record(service: String, method: String) { counts.computeIfAbsent("$service.$method") { AtomicLong() }.incrementAndGet() }
    fun snapshot(): Map<String, Long> = counts.mapValues { it.value.get() }
    fun clear() = counts.clear()
}
