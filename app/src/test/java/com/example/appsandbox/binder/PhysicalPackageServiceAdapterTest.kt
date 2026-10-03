package com.example.appsandbox.binder

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PhysicalPackageServiceAdapterTest {
    @Test fun onlyExactGuestPackageIsReplaced() {
        // ISessionManager.createSession(packageName, callback, tag, extras, userId)
        val args = arrayOf<Any?>("com.ss.android.ugc.aweme.lite", null, "com.ss.android.ugc.aweme.lite.session", 0)
        assertArrayEquals(
            arrayOf<Any?>("com.example.appsandbox", null, "com.ss.android.ugc.aweme.lite.session", 0),
            PhysicalPackageServiceAdapter.physicalPackageArgs(args, "com.ss.android.ugc.aweme.lite", "com.example.appsandbox")
        )
    }
}
