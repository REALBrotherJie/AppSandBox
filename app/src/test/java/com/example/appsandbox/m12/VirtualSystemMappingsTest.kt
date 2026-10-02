package com.example.appsandbox.m12

import org.junit.Assert.*
import org.junit.Test

class VirtualSystemMappingsTest {
    private fun key(instance: String, id: String = "x") = VirtualObjectKey("r1", "com.guest", instance, null, id)
    private fun pi(instance: String, requestCode: Int) = VirtualPendingIntentKey("r1", "com.guest", instance,
        requestCode, "broadcast", "ACTION", "com.guest/.Receiver", null, 0)

    @Test fun permissionStateIsInstanceScopedAndManifestGated() {
        val manager = VirtualPermissionManager()
        val p0 = key("i0"); val p1 = key("i1")
        manager.declare(p0, "android.permission.CAMERA", VirtualPermissionCategory.DANGEROUS)
        manager.declare(p1, "android.permission.CAMERA", VirtualPermissionCategory.DANGEROUS)
        manager.grant(p0, "android.permission.CAMERA")
        assertEquals(VirtualPermissionState.GRANTED, manager.check(p0, "android.permission.CAMERA"))
        assertEquals(VirtualPermissionState.DENIED, manager.check(p1, "android.permission.CAMERA"))
        assertFails { manager.grant(p0, "android.permission.RECORD_AUDIO") }
        manager.deny(p0, "android.permission.CAMERA")
        assertTrue(manager.shouldShowRationale(p0, "android.permission.CAMERA"))
    }

    @Test fun pendingIntentAndNotificationNamespacesDoNotCollide() {
        val intents = VirtualPendingIntentRegistry()
        val i0 = intents.getOrCreate(pi("i0", 1), 1)
        val i1 = intents.getOrCreate(pi("i1", 1), 1)
        assertNotEquals(i0.hostToken, i1.hostToken)
        val notifications = VirtualNotificationRegistry()
        val n0 = VirtualNotificationKey("r1", "com.guest", "i0", "messages", 1)
        val n1 = n0.copy(instanceId = "i1")
        notifications.put(VirtualNotificationRecord(n0, notifications.physicalTag(n0), "messages", i0.key))
        notifications.put(VirtualNotificationRecord(n1, notifications.physicalTag(n1), "messages", i1.key))
        assertEquals(2, notifications.snapshot().size)
        notifications.remove(n0)
        assertEquals(1, notifications.snapshot().size)
    }

    @Test fun jobIdsAreCollisionSafeAndCancelIsInstanceScoped() {
        val jobs = VirtualJobRegistry()
        val k0 = VirtualJobKey("r1", "com.guest", "i0", 7)
        val k1 = k0.copy(instanceId = "i1")
        val j0 = VirtualJobRecord(k0, jobs.hostJobId(k0), "com.guest/.Job", 1)
        val j1 = VirtualJobRecord(k1, jobs.hostJobId(k1), "com.guest/.Job", 1)
        assertNotEquals(j0.hostJobId, j1.hostJobId)
        jobs.put(j0); jobs.put(j1); jobs.remove(k0)
        assertNull(jobs.get(k0)); assertNotNull(jobs.get(k1))
    }

    @Test fun sharedCleanupRemovesAllInstanceMappings() {
        val registries = M12RuntimeRegistries
        val p = key("i0")
        registries.permissions.declare(p, "android.permission.CAMERA", VirtualPermissionCategory.DANGEROUS)
        val k = pi("i0", 2)
        registries.pendingIntents.getOrCreate(k, 1)
        registries.removeInstance("com.guest", "i0")
        assertTrue(registries.permissions.snapshot().none { it.key.instanceId == "i0" })
        assertTrue(registries.pendingIntents.snapshot().none { it.key.instanceId == "i0" })
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); fail("expected failure") } catch (_: IllegalStateException) { }
    }
}
