package com.example.appsandbox.storage

import com.example.appsandbox.model.GuestInstanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GuestInstanceStoreModelTest {
    @Test fun modelPreservesIdentityAndRoots() {
        val record = GuestInstanceRecord("123e4567-e89b-12d3-a456-426614174000", "rev", "pkg", "/tmp/base.apk", "sha", "/tmp/instance", 1L, 2L)
        assertEquals("rev", record.guestRevisionId)
        assertEquals("sha", record.guestSha256)
        assertNotEquals("a", record.instanceId)
        assertNotEquals("/tmp/a", record.dataRoot)
    }
}
