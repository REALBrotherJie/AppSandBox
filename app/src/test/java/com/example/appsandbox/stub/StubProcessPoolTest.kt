package com.example.appsandbox.stub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StubProcessPoolTest {
    @Test fun allocationConflictIsExplicit() {
        val pool = StubProcessPool(2)
        assertEquals(0, pool.allocate("a", 0))
        val failure = runCatching { pool.allocate("b", 0) }.exceptionOrNull()
        assertEquals("stub slot p0 is already assigned", failure?.message)
    }

    @Test fun processDeathReclaimsAssignment() {
        val pool = StubProcessPool(2)
        pool.allocate("a", 1)
        assertEquals(setOf("a"), pool.onProcessDied(1))
        assertNull(pool.query("a"))
        assertEquals(1, pool.allocate("b", 1))
    }

    @Test fun exhaustionIsExplicit() {
        val pool = StubProcessPool(1)
        pool.allocate("a")
        assertEquals("stub process pool exhausted (1 slots)", runCatching { pool.allocate("b") }.exceptionOrNull()?.message)
    }
}
