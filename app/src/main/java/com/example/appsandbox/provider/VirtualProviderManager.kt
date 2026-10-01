package com.example.appsandbox.provider

import android.content.pm.ProviderInfo
import java.util.concurrent.ConcurrentHashMap

/** Registry for framework-installed Guest providers. It never invokes provider callbacks itself. */
class VirtualProviderManager {
    companion object { val GLOBAL = VirtualProviderManager() }
    data class Key(val instanceId: String, val authority: String)
    data class Record(
        val key: Key,
        val packageName: String,
        val virtualUid: String,
        val providerInfo: ProviderInfo,
        val provider: Any,
        val transport: Any? = null
    )

    private val records = ConcurrentHashMap<Key, Record>()

    fun install(record: Record): Boolean = records.putIfAbsent(record.key, record) == null
    fun find(instanceId: String, authority: String): Record? = records[Key(instanceId, authority)]
    fun remove(instanceId: String, authority: String): Record? = records.remove(Key(instanceId, authority))
    fun removeInstance(instanceId: String): Int {
        val keys = records.keys.filter { it.instanceId == instanceId }
        keys.forEach(records::remove)
        return keys.size
    }
    fun size(instanceId: String? = null): Int = if (instanceId == null) records.size else records.keys.count { it.instanceId == instanceId }
    fun snapshot(instanceId: String): List<Record> = records.values.filter { it.key.instanceId == instanceId }
    fun ownsAuthority(instanceId: String, authority: String?): Boolean = authority != null && find(instanceId, authority) != null
}
