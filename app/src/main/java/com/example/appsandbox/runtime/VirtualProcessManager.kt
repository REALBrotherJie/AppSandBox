package com.example.appsandbox.runtime

import java.util.concurrent.atomic.AtomicLong

data class VirtualProcessKey(
    val packageRevision: Long,
    val packageName: String,
    val instanceId: String,
    val logicalProcessName: String
) {
    init {
        require(packageRevision >= 0)
        require(packageName.isNotBlank() && instanceId.isNotBlank())
        require(logicalProcessName == packageName || logicalProcessName.startsWith("$packageName:"))
    }

    companion object {
        fun canonicalProcessName(packageName: String, declared: String?): String = when {
            declared.isNullOrBlank() -> packageName
            declared.startsWith(":") -> packageName + declared
            else -> declared
        }
    }
}

enum class VirtualProcessState { UNBOUND, STARTING, BOUND, RUNNING, DYING, DEAD }

data class VirtualProcessBinding(
    val key: VirtualProcessKey,
    val slot: Int,
    val generation: Long,
    val state: VirtualProcessState,
    val pid: Int? = null,
    val webViewSuffix: String? = null
)

/** Authoritative in-memory owner for logical Guest process to physical slot routing. */
class VirtualProcessManager(private val slotCount: Int) {
    private val generation = AtomicLong(0)
    private val bindings = linkedMapOf<VirtualProcessKey, VirtualProcessBinding>()

    init { require(slotCount > 0) }

    @Synchronized
    fun allocate(key: VirtualProcessKey, preferredSlot: Int? = null): VirtualProcessBinding {
        bindings[key]?.takeUnless { it.state == VirtualProcessState.DEAD }?.let { return it }
        val liveSlots = bindings.values.filter { it.state !in setOf(VirtualProcessState.DEAD, VirtualProcessState.UNBOUND) }.map { it.slot }.toSet()
        val slot = preferredSlot?.also { require(it in 0 until slotCount); check(it !in liveSlots) } ?: (0 until slotCount).firstOrNull { it !in liveSlots }
            ?: error("virtual process pool exhausted ($slotCount slots)")
        return VirtualProcessBinding(key, slot, generation.incrementAndGet(), VirtualProcessState.STARTING).also { bindings[key] = it }
    }

    @Synchronized
    fun transition(
        key: VirtualProcessKey,
        expectedGeneration: Long,
        state: VirtualProcessState,
        pid: Int? = null,
        webViewSuffix: String? = null
    ): VirtualProcessBinding {
        val current = requireNotNull(bindings[key]) { "virtual process is not allocated" }
        check(current.generation == expectedGeneration) { "stale process generation" }
        return current.copy(state = state, pid = pid ?: current.pid, webViewSuffix = webViewSuffix ?: current.webViewSuffix).also { bindings[key] = it }
    }

    @Synchronized fun markDead(slot: Int, generation: Long): VirtualProcessBinding? {
        val current = bindings.values.firstOrNull { it.slot == slot && it.generation == generation } ?: return null
        return current.copy(state = VirtualProcessState.DEAD, pid = null).also { bindings[current.key] = it }
    }

    @Synchronized fun snapshot() = bindings.values.toList()
}
