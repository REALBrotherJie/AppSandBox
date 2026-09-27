package com.example.appsandbox.dispatch

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class InMemoryGuestDispatchStateStore : GuestDispatchStateStore {
    private val lock = Any()
    private val plans = LinkedHashMap<String, GuestDispatchPlan>()

    override fun find(operationId: String): GuestDispatchPlan? = synchronized(lock) {
        plans[operationId]
    }

    override fun committed(): List<GuestDispatchPlan> = synchronized(lock) {
        plans.values.toList()
    }

    override fun commit(plan: GuestDispatchPlan) = synchronized(lock) {
        val current = plans[plan.operationId]
        if (current != null && current != plan) {
            throw GuestDispatchStoreException("Operation id already belongs to another plan")
        }
        plans[plan.operationId] = plan
    }

    override fun removeForInstance(instanceId: String): Int = synchronized(lock) {
        val ids = plans.values.filter { it.instanceId == instanceId }.map { it.operationId }
        ids.forEach(plans::remove)
        ids.size
    }
}

class FileGuestDispatchStateStore(private val file: File) : GuestDispatchStateStore {
    private val lock = LOCKS.computeIfAbsent(file.canonicalFile.path) { Any() }
    private val backup get() = File(file.path + ".bak")

    override fun find(operationId: String): GuestDispatchPlan? = synchronized(lock) {
        read().firstOrNull { it.operationId == operationId }
    }

    override fun committed(): List<GuestDispatchPlan> = synchronized(lock) {
        read()
    }

    override fun commit(plan: GuestDispatchPlan) = synchronized(lock) {
        val current = read()
        val existing = current.firstOrNull { it.operationId == plan.operationId }
        if (existing != null && existing != plan) {
            throw GuestDispatchStoreException("Operation id already belongs to another plan")
        }
        if (existing == null) write(current + plan)
    }

    override fun removeForInstance(instanceId: String): Int = synchronized(lock) {
        val current = read()
        val next = current.filterNot { it.instanceId == instanceId }
        if (next.size != current.size) write(next)
        current.size - next.size
    }

    private fun read(): List<GuestDispatchPlan> {
        if (!file.exists() && !backup.exists()) return emptyList()
        val primary = runCatching { decode(file) }
        if (primary.isSuccess) return primary.getOrThrow()
        val recovered = runCatching { decode(backup) }.getOrElse {
            throw GuestDispatchStoreException("Logical dispatch state and backup are corrupt", it)
        }
        runCatching {
            Files.copy(
                backup.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        return recovered
    }

    private fun decode(source: File): List<GuestDispatchPlan> {
        if (!source.exists()) error("missing dispatch state")
        val root = JSONObject(source.readText(Charsets.UTF_8))
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            error("unsupported dispatch state schema")
        }
        val records = root.optJSONArray("plans") ?: error("missing dispatch plans")
        val parsed = (0 until records.length()).map { guestDispatchPlanFromJson(records.getJSONObject(it)) }
        require(parsed.map { it.operationId }.toSet().size == parsed.size) {
            "duplicate logical operation id"
        }
        return parsed
    }

    private fun write(plans: List<GuestDispatchPlan>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("plans", JSONArray(plans.map { it.toJson() }))
        try {
            FileOutputStream(temporary).use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (file.exists()) file.copyTo(backup, overwrite = true)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw GuestDispatchStoreException("Unable to persist logical dispatch state", error)
        }
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private val LOCKS = ConcurrentHashMap<String, Any>()
    }
}
