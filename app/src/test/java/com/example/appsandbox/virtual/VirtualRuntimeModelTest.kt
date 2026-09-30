package com.example.appsandbox.virtual

import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualRuntimeModelTest {
    @Test fun virtualIdentityIncludesPackageAndInstance() {
        val instance = VirtualInstance("com.example.app", "instance-a", 7, 2, "/data/instance-a")
        assertEquals("com.example.app:instance-a", instance.virtualUid)
    }
}
