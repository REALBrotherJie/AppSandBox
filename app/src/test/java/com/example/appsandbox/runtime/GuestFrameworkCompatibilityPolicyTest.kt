package com.example.appsandbox.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestFrameworkCompatibilityPolicyTest {
    @Test fun legacyOrdinaryReceiverGetsHostValidExportedRepresentation() {
        assertEquals(GuestFrameworkCompatibilityPolicy.RECEIVER_EXPORTED,
            GuestFrameworkCompatibilityPolicy.receiverFlags(true, 36, 28, 0, true, false))
    }

    @Test fun modernGuestKeepsModernEnforcement() {
        assertEquals(0, GuestFrameworkCompatibilityPolicy.receiverFlags(true, 36, 36, 0, true, false))
    }

    @Test fun hostContextCallIsAlwaysUnchanged() {
        assertEquals(0, GuestFrameworkCompatibilityPolicy.receiverFlags(false, 36, 28, 0, true, false))
    }

    @Test fun explicitStickyProtectedAndOldPlatformCallsAreUnchanged() {
        val notExported = GuestFrameworkCompatibilityPolicy.RECEIVER_NOT_EXPORTED
        assertEquals(notExported, GuestFrameworkCompatibilityPolicy.receiverFlags(true, 36, 28, notExported, true, false))
        assertEquals(0, GuestFrameworkCompatibilityPolicy.receiverFlags(true, 36, 28, 0, false, false))
        assertEquals(0, GuestFrameworkCompatibilityPolicy.receiverFlags(true, 36, 28, 0, true, true))
        assertEquals(0, GuestFrameworkCompatibilityPolicy.receiverFlags(true, 31, 28, 0, true, false))
    }
}
