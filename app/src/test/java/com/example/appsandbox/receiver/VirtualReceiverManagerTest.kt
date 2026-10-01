package com.example.appsandbox.receiver

import android.content.BroadcastReceiver
import android.content.IntentFilter
import org.junit.Test
import org.junit.Assert.assertEquals

class VirtualReceiverManagerTest {
    @Test fun registrations_are_instance_scoped_and_unregister_is_identity_based() {
        val manager = VirtualReceiverManager()
        val a = object : BroadcastReceiver() { override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {} }
        val b = object : BroadcastReceiver() { override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {} }
        manager.register(VirtualReceiverManager.Registration(VirtualReceiverManager.Key("a", a), "p", "u", a, IntentFilter("x"), 0, null))
        manager.register(VirtualReceiverManager.Registration(VirtualReceiverManager.Key("b", b), "p", "u", b, IntentFilter("x"), 0, null))
        assertEquals(1, manager.size("a")); assertEquals(1, manager.size("b"))
        manager.unregister("a", a)
        assertEquals(0, manager.size("a")); assertEquals(1, manager.size("b"))
    }
}
