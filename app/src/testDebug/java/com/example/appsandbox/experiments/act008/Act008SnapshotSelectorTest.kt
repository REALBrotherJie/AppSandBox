package com.example.appsandbox.experiments.act008

import org.junit.Assert.*
import org.junit.Test

class Act008SnapshotSelectorTest {
    @Test fun readByRunIdReturnsExactRecoveredFailureNotAnotherRun() {
        val old = Act008SessionSnapshot("", "op-a", "run-a", "instance", Act008SessionState.FAILED, Act008Failure.CRASH_RECOVERY)
        val newer = Act008SessionSnapshot("", "op-b", "run-b", "instance", Act008SessionState.RUNNING)
        val result = Act008SnapshotSelector.exact(listOf(old, newer), "instance", "run-a")
        assertEquals(Act008Failure.CRASH_RECOVERY, result?.failure)
        assertEquals("run-a", result?.runId)
    }
}
