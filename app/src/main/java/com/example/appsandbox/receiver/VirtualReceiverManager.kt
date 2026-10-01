package com.example.appsandbox.receiver

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Instance-scoped receiver ownership. Delivery is intentionally delegated to Android. */
class VirtualReceiverManager {
    companion object { val GLOBAL = VirtualReceiverManager() }
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
    data class Delivery(val id: String, val instanceId: String, val guestIntent: Intent, val guestInfo: ActivityInfo, val stub: ComponentName)

    private val registrations = ConcurrentHashMap<Key, Registration>()
    private val deliveries = ConcurrentHashMap<String, Delivery>()

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

    fun createDelivery(instanceId: String, intent: Intent, info: ActivityInfo, stub: ComponentName): Delivery {
        val delivery = Delivery(UUID.randomUUID().toString(), instanceId, Intent(intent), ActivityInfo(info), stub)
        deliveries[delivery.id] = delivery
        return delivery
    }
    fun delivery(id: String, instanceId: String): Delivery? = deliveries[id]?.takeIf { it.instanceId == instanceId }
    fun consume(id: String): Delivery? = deliveries[id]
    fun finishDelivery(id: String) { deliveries.remove(id) }
    fun pending(instanceId: String? = null): Int = if (instanceId == null) deliveries.size else deliveries.values.count { it.instanceId == instanceId }
    fun removeInstanceDeliveries(instanceId: String): Int {
        val ids = deliveries.values.filter { it.instanceId == instanceId }.map { it.id }
        ids.forEach(deliveries::remove); return ids.size
    }
}
