package com.example.appsandbox.experiments.act007.api31

import org.junit.Assert.assertEquals
import org.junit.Test

class Act007SessionResultTest {
    @Test fun rejectedResultHasZeroLifecycleCounters() {
        val r = Act007SessionResult("REJECTED", "MISSING_INSTANCE", "missing")
        assertEquals(0, r.constructed); assertEquals(0, r.onCreateAttempted); assertEquals(0, r.onCreateCompleted)
    }
}
