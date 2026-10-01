package com.example.appsandbox.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VirtualProcessManagerTest {
    private fun key(instance: String, process: String = "pkg") = VirtualProcessKey(7, "pkg", instance, process)

    @Test fun canonicalizesPrivateProcessNames() {
        assertEquals("pkg", VirtualProcessKey.canonicalProcessName("pkg", null))
        assertEquals("pkg:remote", VirtualProcessKey.canonicalProcessName("pkg", ":remote"))
    }

    @Test fun concurrentKeysReceiveDifferentSlotsAndGenerations() {
        val manager = VirtualProcessManager(2)
        val first = manager.allocate(key("a"))
        val second = manager.allocate(key("a", "pkg:remote"))
        assertNotEquals(first.slot, second.slot)
        assertNotEquals(first.generation, second.generation)
    }

    @Test fun staleGenerationCannotMutateReusedBinding() {
        val manager = VirtualProcessManager(1)
        val first = manager.allocate(key("a"), 0)
        manager.markDead(0, first.generation)
        val second = manager.allocate(key("b"), 0)
        assertNotEquals(first.generation, second.generation)
        runCatching { manager.transition(key("b"), first.generation, VirtualProcessState.RUNNING) }
            .onSuccess { throw AssertionError("stale generation accepted") }
    }

    @Test fun webViewSuffixIsStableAndIdentityScoped() {
        val first = VirtualWebViewProcessPolicy.stableSuffix(key("a"))
        assertEquals(first, VirtualWebViewProcessPolicy.stableSuffix(key("a")))
        assertNotEquals(first, VirtualWebViewProcessPolicy.stableSuffix(key("b")))
        assertNotEquals(first, VirtualWebViewProcessPolicy.stableSuffix(key("a", "pkg:remote")))
    }
}
