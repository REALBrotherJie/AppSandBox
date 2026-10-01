package com.example.appsandbox.service

import android.content.ComponentName
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualServiceRuntimeModelTest {
    @Test fun serviceKeySeparatesInstancesAndComponents() {
        val a = VirtualServiceKey("guest", "a", ComponentName("guest", "guest.Service"))
        val b = a.copy(instanceId = "b")
        assertNotEquals(a.instanceId, b.instanceId)
        assertEquals(a.instanceId, a.copy().instanceId)
    }
}
