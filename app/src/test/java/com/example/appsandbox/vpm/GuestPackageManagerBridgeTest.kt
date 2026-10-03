package com.example.appsandbox.vpm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuestPackageManagerBridgeTest {
    @Test fun hiddenPackagePermissionCheckIsDeniedNotNull() {
        // IPackageManager.checkPermission returns a primitive int; null crashes the proxy while unboxing.
        assertEquals(-1, GuestPackageManagerBridge.hiddenPackageResult("checkPermission"))
    }

    @Test fun hiddenPackageObjectQueriesLookUninstalled() {
        listOf("getPackageInfo", "getApplicationInfo", "getActivityInfo", "getPackagesForUid").forEach {
            assertNull(it, GuestPackageManagerBridge.hiddenPackageResult(it))
        }
    }
}
