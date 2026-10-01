package com.example.appsandbox.provider

import android.content.pm.ProviderInfo
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class VirtualProviderManagerTest {
    @Test fun authority_is_keyed_by_instance_and_install_is_once() {
        val manager = VirtualProviderManager(); val info = ProviderInfo()
        val first = VirtualProviderManager.Record(VirtualProviderManager.Key("a", "x"), "p", "u", info, Any())
        assertTrue(manager.install(first)); assertFalse(manager.install(first));
        assertEquals(first, manager.find("a", "x")); assertEquals(null, manager.find("b", "x"))
        manager.removeInstance("a"); assertEquals(0, manager.size("a"))
    }
}
