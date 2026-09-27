package com.example.appsandbox.experiments.act005

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Act005P1AdapterTest {
    private val request = Act005Request("launch", "11111111-1111-4111-8111-111111111111", "revision", "a".repeat(64),
        "com.example.GuestActivity", "com.example/.HostStub")

    @Test fun apiAdaptersRejectVersionMismatchIndependently() {
        assertEquals("API_MISMATCH", Act005Api31Adapter().capability(request.copy(forcedApi = 36)).access)
        assertEquals("API_MISMATCH", Act005Api36Adapter().capability(request.copy(forcedApi = 31)).access)
    }

    @Test fun accessDeniedIsStructuredAndNeverSupported() {
        val denied31 = Act005Api31Adapter().capability(request.copy(forcedApi = 31, denyAccess = true))
        val denied36 = Act005Api36Adapter().capability(request.copy(forcedApi = 36, denyAccess = true))
        assertEquals("ACCESS_DENIED", denied31.access); assertFalse(denied31.supported)
        assertEquals("ACCESS_DENIED", denied36.access); assertFalse(denied36.supported)
    }

    @Test fun invalidAndStaleMappingsRejectBeforeClassSelection() {
        val selector = Act005P1Selector(File("build/test-act005"))
        val invalid = selector.select(request.copy(launchId = ""), null, null)
        val stale = selector.select(request.copy(launchId = "stale"), null, null)
        assertEquals(Act005Reason.INVALID_INPUT, invalid.reason)
        assertEquals(Act005Reason.STALE_MAPPING, stale.reason)
        assertTrue(invalid.hostFallback); assertTrue(stale.hostFallback)
        assertFalse(invalid.phases.contains(Act005Phase.CLASS_SELECTION_PENDING))
    }

    @Test fun rollbackIsIdempotentAndFlagsRemainFalse() {
        val result = Act005Result(listOf(Act005Phase.RECEIVED, Act005Phase.FALLBACK_HOST), Act005Reason.ACCESS_DENIED,
            Act005Capability(31, "API31", false, "ACCESS_DENIED", "x"))
        assertSame(result, Act005P1Selector(File("build/test-act005")).rollbackAgain(result))
        assertFalse(result.guestObjectConstructed); assertFalse(result.guestAttached); assertFalse(result.guestLifecycle)
    }
}
