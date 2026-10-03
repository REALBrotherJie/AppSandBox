package com.example.appsandbox.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StubServiceAllocatorTest {
    @Test
    fun runningServicesNeverShareAStub() {
        val allocator = StubServiceAllocator<String>(32)
        val indexes = (0 until 32).map { allocator.allocate("s$it") }
        assertEquals(32, indexes.toSet().size)
        assertEquals(indexes[5], allocator.allocate("s5"))
    }

    @Test(expected = IllegalStateException::class)
    fun exhaustionWithOnlyRunningServicesFails() {
        val allocator = StubServiceAllocator<String>(2)
        allocator.allocate("a")
        allocator.allocate("b")
        allocator.allocate("c")
    }

    @Test
    fun restartAfterDestroyKeepsTheSameStub() {
        val allocator = StubServiceAllocator<String>(2)
        val index = allocator.allocate("a")
        allocator.allocate("b")
        assertTrue(allocator.destroyed("a", index))
        assertFalse(allocator.isLive("a"))
        // A start that raced the destroy must find the stub its pending CREATE will target.
        assertEquals(index, allocator.allocate("a"))
        assertTrue(allocator.isLive("a"))
    }

    @Test
    fun aFullPoolReclaimsTheOldestDestroyedStubOnly() {
        val allocator = StubServiceAllocator<String>(3)
        val a = allocator.allocate("a")
        val b = allocator.allocate("b")
        allocator.allocate("c")
        allocator.destroyed("b", b)
        allocator.destroyed("a", a)
        assertEquals(b, allocator.allocate("d"))
        assertTrue(allocator.isLive("c"))
        // "a" still owns its stub until another Service needs it.
        assertEquals(a, allocator.allocate("a"))
    }

    @Test
    fun aStaleDestroyDoesNotFreeANewerAllocation() {
        val allocator = StubServiceAllocator<String>(2)
        val a = allocator.allocate("a")
        allocator.allocate("b")
        allocator.destroyed("a", a)
        val d = allocator.allocate("d")
        assertEquals(a, d)
        assertFalse(allocator.destroyed("a", a))
        assertTrue(allocator.isLive("d"))
        assertFalse(allocator.isLive("a"))
    }
}
