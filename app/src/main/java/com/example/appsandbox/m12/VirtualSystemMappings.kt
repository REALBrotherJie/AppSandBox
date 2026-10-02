package com.example.appsandbox.m12

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class VirtualObjectKey(val packageRevision: String, val packageName: String, val instanceId: String,
    val logicalProcess: String? = null, val logicalId: String)

enum class VirtualPermissionState { GRANTED, DENIED }
enum class VirtualPermissionCategory { NORMAL, DANGEROUS, SIGNATURE, SPECIAL_APP_ACCESS, UNKNOWN }

data class VirtualPermissionRecord(val key: VirtualObjectKey, val permission: String,
    val category: VirtualPermissionCategory, val requested: Boolean,
    val state: VirtualPermissionState, val requestedBefore: Boolean = false,
    val deniedOnce: Boolean = false, val dontAskAgain: Boolean = false)

class VirtualPermissionManager {
    private val records = ConcurrentHashMap<Pair<VirtualObjectKey, String>, VirtualPermissionRecord>()

    fun declare(key: VirtualObjectKey, permission: String, category: VirtualPermissionCategory): VirtualPermissionRecord =
        records.computeIfAbsent(key to permission) { VirtualPermissionRecord(key, permission, category, true,
            if (category == VirtualPermissionCategory.NORMAL) VirtualPermissionState.GRANTED else VirtualPermissionState.DENIED) }

    fun check(key: VirtualObjectKey, permission: String): VirtualPermissionState =
        records[key to permission]?.takeIf { it.requested }?.state ?: VirtualPermissionState.DENIED

    fun request(key: VirtualObjectKey, permission: String): VirtualPermissionRecord = update(key, permission, true, false, false)

    fun grant(key: VirtualObjectKey, permission: String): VirtualPermissionRecord = update(key, permission, true, false, false)

    fun deny(key: VirtualObjectKey, permission: String, dontAskAgain: Boolean = false): VirtualPermissionRecord =
        update(key, permission, false, true, dontAskAgain)

    fun shouldShowRationale(key: VirtualObjectKey, permission: String): Boolean = records[key to permission]?.let {
        it.requested && it.state == VirtualPermissionState.DENIED && it.requestedBefore && !it.dontAskAgain
    } ?: false

    fun removeInstance(packageName: String, instanceId: String) {
        records.keys.removeIf { it.first.packageName == packageName && it.first.instanceId == instanceId }
    }

    fun snapshot(): List<VirtualPermissionRecord> = records.values.toList()

    private fun update(key: VirtualObjectKey, permission: String, granted: Boolean, deniedOnce: Boolean,
        dontAskAgain: Boolean): VirtualPermissionRecord {
        val existing = records[key to permission] ?: error("permission not declared by guest manifest")
        check(existing.requested) { "permission not declared by guest manifest" }
        return existing.copy(state = if (granted) VirtualPermissionState.GRANTED else VirtualPermissionState.DENIED,
            requestedBefore = true, deniedOnce = deniedOnce || existing.deniedOnce,
            dontAskAgain = dontAskAgain || existing.dontAskAgain).also { records[key to permission] = it }
    }
}

data class VirtualPendingIntentKey(val packageRevision: String, val packageName: String, val instanceId: String,
    val requestCode: Int, val kind: String, val action: String?, val component: String?, val data: String?, val flags: Int)

data class VirtualPendingIntent(val key: VirtualPendingIntentKey, val hostToken: String, val generation: Long)

class VirtualPendingIntentRegistry {
    private val next = AtomicInteger(1)
    private val values = ConcurrentHashMap<VirtualPendingIntentKey, VirtualPendingIntent>()
    fun getOrCreate(key: VirtualPendingIntentKey, generation: Long): VirtualPendingIntent = values.computeIfAbsent(key) {
        VirtualPendingIntent(it, "m12-pi-${next.getAndIncrement()}-${it.instanceId}", generation)
    }
    fun remove(key: VirtualPendingIntentKey) = values.remove(key)
    fun removeInstance(packageName: String, instanceId: String) { values.keys.removeIf { it.packageName == packageName && it.instanceId == instanceId } }
    fun contains(key: VirtualPendingIntentKey) = values.containsKey(key)
    fun snapshot() = values.values.toList()
}

data class VirtualNotificationKey(val packageRevision: String, val packageName: String, val instanceId: String, val tag: String?, val id: Int)
data class VirtualNotificationRecord(val key: VirtualNotificationKey, val physicalTag: String, val channelId: String?, val pendingIntent: VirtualPendingIntentKey?)
data class VirtualNotificationChannelKey(val packageRevision: String, val packageName: String, val instanceId: String, val guestChannelId: String)
data class VirtualNotificationChannel(val key: VirtualNotificationChannelKey, val physicalChannelId: String, val name: String, val importance: Int)

class VirtualNotificationRegistry {
    private val values = ConcurrentHashMap<VirtualNotificationKey, VirtualNotificationRecord>()
    private val channels = ConcurrentHashMap<VirtualNotificationChannelKey, VirtualNotificationChannel>()
    fun physicalTag(key: VirtualNotificationKey) = "m12:${key.instanceId}:${key.tag ?: "_"}"
    fun physicalChannelId(key: VirtualNotificationChannelKey) = "m12:${key.instanceId}:${key.guestChannelId}"
    fun put(record: VirtualNotificationRecord) { values[record.key] = record }
    fun putChannel(channel: VirtualNotificationChannel) { channels[channel.key] = channel }
    fun getChannel(key: VirtualNotificationChannelKey) = channels[key]
    fun remove(key: VirtualNotificationKey) = values.remove(key)
    fun removeChannel(key: VirtualNotificationChannelKey) = channels.remove(key)
    fun removeInstance(packageName: String, instanceId: String) {
        values.keys.removeIf { it.packageName == packageName && it.instanceId == instanceId }
        channels.keys.removeIf { it.packageName == packageName && it.instanceId == instanceId }
    }
    fun snapshot() = values.values.toList()
    fun channelSnapshot() = channels.values.toList()
}

data class VirtualAlarmKey(val packageRevision: String, val packageName: String, val instanceId: String, val pendingIntent: VirtualPendingIntentKey)
data class VirtualAlarmRecord(val key: VirtualAlarmKey, val hostToken: String, val exact: Boolean, val generation: Long)

class VirtualAlarmRegistry {
    private val values = ConcurrentHashMap<VirtualAlarmKey, VirtualAlarmRecord>()
    fun put(record: VirtualAlarmRecord) { values[record.key] = record }
    fun remove(key: VirtualAlarmKey) = values.remove(key)
    fun removeInstance(packageName: String, instanceId: String) { values.keys.removeIf { it.packageName == packageName && it.instanceId == instanceId } }
    fun snapshot() = values.values.toList()
    fun removeAllForInstance(packageName: String, instanceId: String): List<VirtualAlarmRecord> = values.entries
        .filter { it.key.packageName == packageName && it.key.instanceId == instanceId }
        .map { it.value }.also { removeInstance(packageName, instanceId) }
}

data class VirtualJobKey(val packageRevision: String, val packageName: String, val instanceId: String, val guestJobId: Int)
data class VirtualJobRecord(val key: VirtualJobKey, val hostJobId: Int, val guestService: String, val generation: Long)

class VirtualJobRegistry {
    private val values = ConcurrentHashMap<VirtualJobKey, VirtualJobRecord>()
    fun hostJobId(key: VirtualJobKey): Int = 100000 + (key.hashCode() and 0x3fffffff)
    fun put(record: VirtualJobRecord) { values[record.key] = record }
    fun get(key: VirtualJobKey) = values[key]
    fun remove(key: VirtualJobKey) = values.remove(key)
    fun removeInstance(packageName: String, instanceId: String) { values.keys.removeIf { it.packageName == packageName && it.instanceId == instanceId } }
    fun cancelAllForInstance(packageName: String, instanceId: String): List<VirtualJobRecord> = values.entries
        .filter { it.key.packageName == packageName && it.key.instanceId == instanceId }
        .map { it.value }.also { removeInstance(packageName, instanceId) }
    fun snapshot() = values.values.toList()
}

object M12RuntimeRegistries {
    val permissions = VirtualPermissionManager()
    val pendingIntents = VirtualPendingIntentRegistry()
    val notifications = VirtualNotificationRegistry()
    val alarms = VirtualAlarmRegistry()
    val jobs = VirtualJobRegistry()

    fun removeInstance(packageName: String, instanceId: String) {
        permissions.removeInstance(packageName, instanceId)
        pendingIntents.removeInstance(packageName, instanceId)
        notifications.removeInstance(packageName, instanceId)
        alarms.removeInstance(packageName, instanceId)
        jobs.removeInstance(packageName, instanceId)
    }
}
