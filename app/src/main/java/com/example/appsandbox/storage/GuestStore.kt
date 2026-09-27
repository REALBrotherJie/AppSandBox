package com.example.appsandbox.storage

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.UUID

class GuestStore(private val context: Context) : com.example.appsandbox.resolver.GuestRevisionSource {
    private val tag = "AppSandbox.Import"
    private val root get() = File(context.filesDir, "guests")
    private val registry get() = File(root, "registry.json")
    private val staging get() = File(root, "staging")

    fun importApk(source: InputStream, parser: (String) -> GuestPackageRecord): GuestPackageRecord = synchronized(LOCK) {
        val transaction = File(staging, UUID.randomUUID().toString()).apply { mkdirs() }
        val staged = File(transaction, "base.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = writeReadOnlyBeforeContent(source, staged, digest)
            require(size > 0) { "APK artifact is empty" }
            val parsed = parser(staged.absolutePath)
            val guestId = UUID.randomUUID().toString()
            val revisionId = UUID.randomUUID().toString()
            val destination = File(root, guestId).apply { check(mkdirs()) }
            val committed = File(destination, "base.apk")
            check(staged.renameTo(committed)) { "Unable to commit staged APK" }
            committed.setReadOnly()
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val record = parsed.copy(
                internalGuestId = guestId,
                revisionId = revisionId,
                apkPath = committed.absolutePath,
                sha256 = sha,
                fileSize = size,
                schemaVersion = GuestPackageRecord.CURRENT_SCHEMA_VERSION,
                components = parsed.components.map { it.copy(revisionId = revisionId) }
            )
            val verification = GuestArtifactVerifier.verify(record)
            if (verification.state != ArtifactState.VALID) throw GuestStoreException(verification.state, verification.message ?: verification.state.name)
            appendRecordAtomically(record)
            transaction.deleteRecursively()
            Log.i(tag, "Imported immutable ${record.packageName} revision=${record.revisionId} sha256=$sha")
            record
        } catch (error: Throwable) {
            transaction.deleteRecursively()
            Log.e(tag, "Import failed in staging=${transaction.absolutePath}", error)
            throw error
        } finally { runCatching { source.close() } }
    }

    fun latestRecord(): GuestPackageRecord? = synchronized(LOCK) {
        if (!registry.exists()) return null
        val records = readRecords()
        if (records.isEmpty()) return null
        val record = records.last()
        val verification = GuestArtifactVerifier.verify(record)
        if (verification.state != ArtifactState.VALID) throw GuestStoreException(verification.state, verification.message ?: verification.state.name)
        record
    }

    fun records(): List<GuestPackageRecord> = synchronized(LOCK) {
        readRecords()
    }

    override fun findRevision(revisionId: String): GuestPackageRecord? =
        synchronized(LOCK) { readRecords().firstOrNull { it.revisionId == revisionId } }

    fun recordsForPackage(packageName: String): List<GuestPackageRecord> = records().filter { it.packageName == packageName }
    fun deleteRevision(revisionId: String, instances: List<com.example.appsandbox.model.GuestInstanceRecord>): Boolean = synchronized(LOCK) {
        val current = records()
        val target = current.firstOrNull { it.revisionId == revisionId } ?: return false
        val next = GuestRevisionPolicy.remove(current, instances, revisionId)
        writeRecordsAtomically(next)
        val directory = File(target.apkPath).parentFile
        if (directory?.deleteRecursively() == false) { writeRecordsAtomically(current); error("Unable to delete Guest revision artifact") }
        true
    }

    private fun writeReadOnlyBeforeContent(source: InputStream, destination: File, digest: MessageDigest): Long {
        destination.parentFile!!.mkdirs()
        val output = FileOutputStream(destination)
        check(destination.setReadOnly() || !destination.canWrite()) { "Unable to mark staged APK read-only before write" }
        var total = 0L
        output.use {
            val buffer = ByteArray(16 * 1024)
            while (true) { val n = source.read(buffer); if (n < 0) break; it.write(buffer, 0, n); digest.update(buffer, 0, n); total += n }
            it.flush()
            runCatching { (it.channel as FileChannel).force(true) }
        }
        return total
    }

    private fun appendRecordAtomically(record: GuestPackageRecord) {
        val records = readRecords().toMutableList().apply { add(record) }
        writeRecordsAtomically(records)
    }

    private fun writeRecordsAtomically(records: List<GuestPackageRecord>) {
        root.mkdirs()
        val atomic = AtomicFile(registry)
        val stream = atomic.startWrite()
        try {
            stream.writer(Charsets.UTF_8).use { it.write(GuestRegistryCodec.encode(records)) }
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }

    private fun readRecords(): List<GuestPackageRecord> {
        if (!registry.exists()) return emptyList()
        val text = try {
            registry.readText()
        } catch (error: Throwable) {
            throw GuestStoreException(ArtifactState.CORRUPT, "Cannot read registry: ${error.message}")
        }
        return try {
            GuestRegistryCodec.decode(text)
        } catch (error: GuestRegistryCodecException) {
            throw GuestStoreException(ArtifactState.CORRUPT, error.message ?: "Malformed Guest registry")
        }
    }

    companion object { private val LOCK = Any() }
}
