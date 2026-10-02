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
    private val pending = ConcurrentHashMap<Key, Pending>()

    private data class Pending(
        val packageName: String,
        val virtualUid: String,
        val install: () -> Record
    )

    fun install(record: Record): Boolean = records.putIfAbsent(record.key, record) == null
    fun registerSelfProvider(
        key: Key,
        packageName: String,
        virtualUid: String,
        install: () -> Record
    ) {
        pending.putIfAbsent(key, Pending(packageName, virtualUid, install))
    }
    fun find(instanceId: String, authority: String): Record? = records[Key(instanceId, authority)]
    fun findSelfProvider(
        instanceId: String,
        packageName: String,
        virtualUid: String,
        authority: String
    ): Record? {
        val key = Key(instanceId, authority)
        find(instanceId, authority)?.let {
            return it.takeIf { record -> record.packageName == packageName && record.virtualUid == virtualUid }
        }
        val candidate = pending[key]?.takeIf { it.packageName == packageName && it.virtualUid == virtualUid }
            ?: return null
        return synchronized(candidate) {
            records[key] ?: candidate.install().also { record ->
                check(record.key == key && record.packageName == packageName && record.virtualUid == virtualUid) {
                    "Self-provider installer returned mismatched identity"
                }
                records[key] = record
                pending.remove(key, candidate)
            }
        }
    }
    fun remove(instanceId: String, authority: String): Record? = records.remove(Key(instanceId, authority))
    fun removeInstance(instanceId: String): Int {
        val keys = records.keys.filter { it.instanceId == instanceId }
        keys.forEach(records::remove)
        pending.keys.filter { it.instanceId == instanceId }.forEach(pending::remove)
        return keys.size
    }
    fun size(instanceId: String? = null): Int = if (instanceId == null) records.size else records.keys.count { it.instanceId == instanceId }
    fun snapshot(instanceId: String): List<Record> = records.values.filter { it.key.instanceId == instanceId }
    fun ownsAuthority(instanceId: String, authority: String?): Boolean = authority != null && find(instanceId, authority) != null
}
