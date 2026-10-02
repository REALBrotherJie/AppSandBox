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

    @Test fun self_provider_requires_instance_package_and_virtual_uid() {
        val manager = VirtualProviderManager()
        val record = VirtualProviderManager.Record(
            VirtualProviderManager.Key("instance-a", "guest.authority"),
            "guest.package", "virtual-uid-a", ProviderInfo(), Any()
        )
        manager.install(record)

        assertEquals(record, manager.findSelfProvider("instance-a", "guest.package", "virtual-uid-a", "guest.authority"))
        assertEquals(null, manager.findSelfProvider("instance-b", "guest.package", "virtual-uid-a", "guest.authority"))
        assertEquals(null, manager.findSelfProvider("instance-a", "other.package", "virtual-uid-a", "guest.authority"))
        assertEquals(null, manager.findSelfProvider("instance-a", "guest.package", "virtual-uid-b", "guest.authority"))
    }

    @Test fun matching_self_provider_is_installed_lazily_once() {
        val manager = VirtualProviderManager()
        val key = VirtualProviderManager.Key("instance-a", "guest.authority")
        var installs = 0
        manager.registerSelfProvider(key, "guest.package", "virtual-uid-a") {
            installs++
            VirtualProviderManager.Record(key, "guest.package", "virtual-uid-a", ProviderInfo(), Any())
        }

        assertEquals(null, manager.findSelfProvider("instance-a", "other.package", "virtual-uid-a", "guest.authority"))
        val first = manager.findSelfProvider("instance-a", "guest.package", "virtual-uid-a", "guest.authority")
        assertEquals(first, manager.findSelfProvider("instance-a", "guest.package", "virtual-uid-a", "guest.authority"))
        assertEquals(1, installs)
    }
}
