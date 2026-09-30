package com.example.appsandbox.stub

class StubProcessPool(private val size: Int) {
    private val assignments = linkedMapOf<String, Int>()

    init { require(size > 0) { "stub pool must not be empty" } }

    @Synchronized
    fun allocate(instanceId: String, preferredSlot: Int? = null): Int {
        require(instanceId.isNotBlank()) { "instanceId is blank" }
        assignments[instanceId]?.let { existing ->
            if (preferredSlot != null && preferredSlot != existing) error("instance already assigned to p$existing")
            return existing
        }
        val used = assignments.values.toSet()
        val slot = preferredSlot?.also {
            require(it in 0 until size) { "stub slot p$it is out of range" }
            check(it !in used) { "stub slot p$it is already assigned" }
        } ?: (0 until size).firstOrNull { it !in used }
            ?: error("stub process pool exhausted ($size slots)")
        assignments[instanceId] = slot
        return slot
    }

    @Synchronized fun query(instanceId: String): Int? = assignments[instanceId]
    @Synchronized fun release(instanceId: String): Int? = assignments.remove(instanceId)

    @Synchronized
    fun onProcessDied(slot: Int): Set<String> {
        val affected = assignments.filterValues { it == slot }.keys
        affected.forEach(assignments::remove)
        return affected
    }
}
