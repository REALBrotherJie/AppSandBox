package com.example.appsandbox.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RuntimeIdentityTest {
    @Test fun physicalAndLogicalIdentityRemainDistinct() {
        val identity = RuntimeIdentity("com.host", 12345, "com.guest", "com.guest:i0", "i0", 2)
        val bridge = SystemIdentityBridge(identity)
        assertEquals("com.guest", bridge.logicalPackageName())
        assertEquals("com.host", bridge.physicalPackageName())
        assertEquals(12345, bridge.physicalUid())
        assertNotEquals(bridge.logicalUid(), bridge.physicalUid().toString())
    }

    @Test fun twoInstancesKeepDistinctLogicalUids() {
        val first = RuntimeIdentity("com.host", 12345, "com.guest", "com.guest:i0", "i0", 0)
        val second = RuntimeIdentity("com.host", 12345, "com.guest", "com.guest:i1", "i1", 1)
        assertNotEquals(first.virtualUid, second.virtualUid)
        assertEquals(first.hostUid, second.hostUid)
    }
}
