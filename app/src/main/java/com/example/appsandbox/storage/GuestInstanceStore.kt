package com.example.appsandbox.storage

import android.content.Context
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.util.UUID

enum class InstanceStoreState { CORRUPT, MISSING_REVISION, INVALID_PATH }
class GuestInstanceStoreException(val state: InstanceStoreState, message: String) : IllegalStateException(message)

class GuestInstanceStore private constructor(private val root: File, private val registry: GuestInstanceRegistry) {
    constructor(context: Context) : this(File(context.filesDir, "guest-instances"), GuestInstanceRegistry(File(context.filesDir, "guest-instances")))

    fun create(record: GuestPackageRecord, instanceId: String = UUID.randomUUID().toString(), now: Long = System.currentTimeMillis()): GuestInstanceRecord {
        require(UUID_PATTERN.matches(instanceId)) { "invalid instanceId" }
        if (record.revisionId.isBlank() || record.sha256.isNullOrBlank()) throw GuestInstanceStoreException(InstanceStoreState.MISSING_REVISION, "unverified Guest revision")
        check(GuestArtifactVerifier.verify(record).state == ArtifactState.VALID) { "Guest revision is not valid" }
        val dataRoot = File(root, instanceId); check(dataRoot.mkdirs() || dataRoot.isDirectory) { "cannot create instance root" }
        val canonical = dataRoot.canonicalFile
        if (canonical.parentFile != root.canonicalFile || dataRoot.isSymbolicLink()) { dataRoot.deleteRecursively(); throw GuestInstanceStoreException(InstanceStoreState.INVALID_PATH, "instance root escaped") }
        val result = GuestInstanceRecord(instanceId, record.revisionId, record.packageName, record.apkPath, record.sha256!!, canonical.path, now, now)
        try { registry.update { all -> check(all.none { it.instanceId == instanceId }) { "duplicate instanceId" }; all + result } } catch (e: Throwable) { dataRoot.deleteRecursively(); throw e }
        return result
    }
    fun list(): List<GuestInstanceRecord> = registry.read()
    fun get(instanceId: String): GuestInstanceRecord? = list().firstOrNull { it.instanceId == instanceId }
    fun delete(instanceId: String): Boolean {
        val all = registry.read(); val found = all.firstOrNull { it.instanceId == instanceId } ?: return false
        registry.write(all.filterNot { it.instanceId == instanceId })
        try { registry.deleteRoot(found) } catch (e: Throwable) { runCatching { registry.write(all) }; throw e }
        return true
    }
    private fun File.isSymbolicLink() = runCatching { java.nio.file.Files.isSymbolicLink(toPath()) }.getOrDefault(false)
    companion object { private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}") }
}
