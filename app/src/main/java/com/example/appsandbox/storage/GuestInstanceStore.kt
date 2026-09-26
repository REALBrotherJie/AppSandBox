package com.example.appsandbox.storage

import android.content.Context
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class InstanceStoreState { CORRUPT, MISSING_REVISION, INVALID_PATH }
class GuestInstanceStoreException(val state: InstanceStoreState, message: String) : IllegalStateException(message)

class GuestInstanceStore(context: Context) {
    private val root = File(context.filesDir, "guest-instances")
    private val registry = File(root, "registry.json")

    fun create(record: GuestPackageRecord, instanceId: String = UUID.randomUUID().toString(), now: Long = System.currentTimeMillis()): GuestInstanceRecord = synchronized(LOCK) {
        require(UUID_PATTERN.matches(instanceId)) { "invalid instanceId" }
        require(record.revisionId.isNotBlank() && !record.sha256.isNullOrBlank()) { throw GuestInstanceStoreException(InstanceStoreState.MISSING_REVISION, "unverified Guest revision") }
        check(GuestArtifactVerifier.verify(record).state == ArtifactState.VALID) { "Guest revision is not valid" }
        val all = readAll()
        check(all.none { it.instanceId == instanceId }) { "duplicate instanceId" }
        val dataRoot = File(root, instanceId)
        check(dataRoot.mkdirs() || dataRoot.isDirectory) { "cannot create instance root" }
        val canonicalRoot = dataRoot.canonicalFile
        check(canonicalRoot.parentFile == root.canonicalFile) { throw GuestInstanceStoreException(InstanceStoreState.INVALID_PATH, "instance root escaped") }
        val result = GuestInstanceRecord(instanceId, record.revisionId, record.packageName, record.apkPath, record.sha256!!, canonicalRoot.path, now, now)
        writeAll(all + result)
        result
    }

    fun list(): List<GuestInstanceRecord> = synchronized(LOCK) { readAll() }
    fun get(instanceId: String): GuestInstanceRecord? = list().firstOrNull { it.instanceId == instanceId }
    fun delete(instanceId: String): Boolean = synchronized(LOCK) {
        val all = readAll()
        val found = all.firstOrNull { it.instanceId == instanceId } ?: return false
        val next = all.filterNot { it.instanceId == instanceId }
        writeAll(next)
        val dir = File(found.dataRoot)
        check(dir.canonicalFile.parentFile == root.canonicalFile) { throw GuestInstanceStoreException(InstanceStoreState.INVALID_PATH, "instance root escaped") }
        dir.deleteRecursively()
        true
    }

    private fun readAll(): List<GuestInstanceRecord> {
        if (!registry.exists()) return emptyList()
        return try {
            val value = JSONObject(registry.readText())
            require(value.getInt("schemaVersion") == 1)
            val array = value.getJSONArray("instances")
            (0 until array.length()).map { GuestInstanceRecord.fromJson(array.getJSONObject(it)) }
        } catch (error: Throwable) {
            throw GuestInstanceStoreException(InstanceStoreState.CORRUPT, "Malformed instance registry: ${error.message}")
        }
    }

    private fun writeAll(records: List<GuestInstanceRecord>) {
        root.mkdirs()
        val temp = File(root, "registry.json.tmp")
        val backup = File(root, "registry.json.bak")
        val text = JSONObject().put("schemaVersion", 1).put("instances", JSONArray(records.map { it.toJson() })).toString(2)
        try {
            FileOutputStream(temp).use { output -> output.write(text.toByteArray(StandardCharsets.UTF_8)); output.fd.sync() }
            if (registry.exists()) registry.copyTo(backup, overwrite = true)
            check(temp.renameTo(registry)) { "cannot commit instance registry" }
        } catch (error: Throwable) {
            temp.delete()
            if (!registry.exists() && backup.exists()) backup.renameTo(registry)
            throw GuestInstanceStoreException(InstanceStoreState.CORRUPT, "Cannot commit instance registry: ${error.message}")
        }
    }

    companion object {
        private val LOCK = Any()
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    }
}
