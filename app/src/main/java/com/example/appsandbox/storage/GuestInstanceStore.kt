package com.example.appsandbox.storage

import android.content.Context
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.util.UUID

enum class InstanceStoreState { CORRUPT, MISSING_REVISION, INVALID_PATH }
class GuestInstanceStoreException(val state: InstanceStoreState, message: String) : IllegalStateException(message)

class GuestInstanceStore private constructor(private val root: File, private val registry: GuestInstanceRegistry) {
    constructor(context: Context) : this(trustedRoot(context), GuestInstanceRegistry(trustedRoot(context)))
    internal constructor(root: File, ops: InstanceFileOps = DefaultInstanceFileOps()) : this(root.canonicalFile, GuestInstanceRegistry(root.canonicalFile, ops))

    fun create(record: GuestPackageRecord, instanceId: String = UUID.randomUUID().toString(), now: Long = System.currentTimeMillis()): GuestInstanceRecord {
        require(UUID_PATTERN.matches(instanceId)) { "invalid instanceId" }
        if (record.revisionId.isBlank() || record.sha256.isNullOrBlank()) throw GuestInstanceStoreException(InstanceStoreState.MISSING_REVISION, "unverified Guest revision")
        check(GuestArtifactVerifier.verify(record).state == ArtifactState.VALID) { "Guest revision is not valid" }
        val rawDataRoot = File(root, instanceId)
        if (rawDataRoot.exists() || rawDataRoot.isSymbolicLink()) throw GuestInstanceStoreException(InstanceStoreState.INVALID_PATH, "instance root already exists")
        check(root.isDirectory || root.mkdirs() || root.isDirectory) { "cannot create registry root" }
        return registry.transaction {
            val all = registry.read()
            check(all.none { it.instanceId == instanceId }) { "duplicate instanceId" }
            check(rawDataRoot.mkdir()) { "cannot create instance root" }
            try {
                val canonical = rawDataRoot.canonicalFile
                check(canonical.parentFile == root && !rawDataRoot.isSymbolicLink()) { "instance root escaped" }
                val result = GuestInstanceRecord(instanceId, record.revisionId, record.packageName, record.apkPath, record.sha256!!, canonical.path, now, now)
                registry.write(all + result)
                result
            } catch (error: Throwable) {
                rawDataRoot.deleteRecursively()
                throw error
            }
        }
    }

    fun list(): List<GuestInstanceRecord> = registry.read()
    fun get(instanceId: String): GuestInstanceRecord? = list().firstOrNull { it.instanceId == instanceId }
    fun delete(instanceId: String): Boolean {
        require(UUID_PATTERN.matches(instanceId)) { "invalid instanceId" }
        val rawDataRoot = File(root, instanceId)
        if (rawDataRoot.isSymbolicLink()) throw GuestInstanceStoreException(InstanceStoreState.INVALID_PATH, "instance root is symbolic link")
        val dataRoot = rawDataRoot.canonicalFile
        return registry.transaction {
            val all = registry.read()
            val found = all.firstOrNull { it.instanceId == instanceId } ?: return@transaction false
            if (File(found.dataRoot).absoluteFile.normalize() != dataRoot) throw GuestInstanceStoreException(InstanceStoreState.INVALID_PATH, "dataRoot escaped")
            registry.write(all.filterNot { it.instanceId == instanceId })
            try { registry.deleteRoot(found) } catch (error: Throwable) {
                runCatching { registry.write(all) }.onFailure { error.addSuppressed(it) }
                throw error
            }
            true
        }
    }

    private fun File.isSymbolicLink() = runCatching { java.nio.file.Files.isSymbolicLink(toPath()) }.getOrDefault(false)

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        private fun trustedRoot(context: Context) = File(context.filesDir, "guest-instances").canonicalFile
    }
}
