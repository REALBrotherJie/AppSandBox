package com.example.appsandbox.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestFrameworkCompatibilityPolicyTest {
    @Test fun guestNotExportedReceiverPermissionMapsToHostOwnPermission() {
        assertEquals("com.example.appsandbox.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            GuestFrameworkCompatibilityPolicy.receiverPermission(
                "com.qiyi.video.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "com.qiyi.video", "com.example.appsandbox"))
    }

    @Test fun otherReceiverPermissionsAreUnchanged() {
        // Another package's not-exported permission and ordinary permissions keep their meaning.
        listOf("com.other.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "android.permission.BROADCAST_SMS", null).forEach {
            assertEquals(it, GuestFrameworkCompatibilityPolicy.receiverPermission(it, "com.qiyi.video", "com.example.appsandbox"))
        }
    }

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
