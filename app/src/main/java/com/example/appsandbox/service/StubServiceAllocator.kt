package com.example.appsandbox.service

/**
 * Stub Service indexes of one stub process. A Guest Service keeps its index across destroy and restart,
 * so a start racing the framework's destroy still finds its stub mapping. A destroyed Service's index is
 * handed to another Guest Service only when no index was ever unused, oldest destroy first.
 */
class StubServiceAllocator<K : Any>(private val capacity: Int) {
    private class Entry(val index: Int, var live: Boolean)

    private val byKey = LinkedHashMap<K, Entry>()

    /** The index for [key]; marks it live. */
    @Synchronized
    fun allocate(key: K): Int {
        byKey.remove(key)?.let { entry ->
            entry.live = true
            byKey[key] = entry
            return entry.index
        }
        val used = byKey.values.mapTo(HashSet()) { it.index }
        val index = (0 until capacity).firstOrNull { it !in used } ?: reclaim()
            ?: throw IllegalStateException("all $capacity stub Services are held by running Guest Services")
        byKey[key] = Entry(index, live = true)
        return index
    }

    /** Whether [key] holds a stub whose Guest Service has not been destroyed. */
    @Synchronized
    fun isLive(key: K): Boolean = byKey[key]?.live == true

    /** The framework destroyed [key]'s Service on [index]. */
    @Synchronized
    fun destroyed(key: K, index: Int): Boolean {
        val entry = byKey[key]?.takeIf { it.index == index } ?: return false
        entry.live = false
        // Most recently destroyed last, so reclaim takes the oldest destroy.
        byKey.remove(key)
        byKey[key] = entry
        return true
    }

    @Synchronized
    fun liveCount(): Int = byKey.values.count { it.live }

    private fun reclaim(): Int? {
        val victim = byKey.entries.firstOrNull { !it.value.live } ?: return null
        byKey.remove(victim.key)
        return victim.value.index
    }
}
