package com.example.appsandbox.receiver

import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.util.concurrent.ConcurrentHashMap

/** Instance-scoped receiver ownership. Delivery is intentionally delegated to Android. */
class VirtualReceiverManager {
    data class Key(val instanceId: String, val token: Any)
    data class Registration(
        val key: Key,
        val packageName: String,
        val virtualUid: String,
        val receiver: BroadcastReceiver,
        val filter: IntentFilter,
        val flags: Int,
        val permission: String?
    )

    private val registrations = ConcurrentHashMap<Key, Registration>()

    fun register(registration: Registration): Registration {
        registrations[registration.key] = registration
        return registration
    }

    fun unregister(instanceId: String, receiver: BroadcastReceiver): Registration? {
        val match = registrations.entries.firstOrNull { it.key.instanceId == instanceId && it.value.receiver === receiver }
        match?.let { registrations.remove(it.key, it.value) }
        return match?.value
    }

    fun registrations(instanceId: String): List<Registration> = registrations.values.filter { it.key.instanceId == instanceId }

    fun removeInstance(instanceId: String): Int {
        val keys = registrations.keys.filter { it.instanceId == instanceId }
        keys.forEach(registrations::remove)
        return keys.size
    }

    fun size(instanceId: String? = null): Int = if (instanceId == null) registrations.size else registrations.keys.count { it.instanceId == instanceId }
}
