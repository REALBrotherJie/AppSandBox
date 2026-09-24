package com.example.appsandbox.storage

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.UUID

class GuestStore(private val context: Context) {
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
            val record = parsed.copy(internalGuestId = guestId, revisionId = revisionId, apkPath = committed.absolutePath, sha256 = sha, fileSize = size, schemaVersion = 2)
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
        val records = readRegistry()
        if (records.length() == 0) return null
        val record = parse(records.getJSONObject(records.length() - 1))
        val verification = GuestArtifactVerifier.verify(record)
        if (verification.state != ArtifactState.VALID) throw GuestStoreException(verification.state, verification.message ?: verification.state.name)
        record
    }

    fun records(): List<GuestPackageRecord> = synchronized(LOCK) {
        val records = readRegistry()
        (0 until records.length()).map { parse(records.getJSONObject(it)) }
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

    private fun readRegistry(): JSONArray {
        val text = try { registry.readText() } catch (e: Exception) { throw GuestStoreException(ArtifactState.CORRUPT, "Cannot read registry: ${e.message}") }
        return try {
            val rootValue = JSONObject(text)
            require(rootValue.getInt("schemaVersion") == 2)
            rootValue.getJSONArray("records")
        } catch (_: Exception) {
            runCatching { JSONArray(text) }.getOrElse { throw GuestStoreException(ArtifactState.CORRUPT, "Malformed registry.json") }
        }
    }

    private fun appendRecordAtomically(record: GuestPackageRecord) {
        root.mkdirs()
        val records = if (registry.exists()) readRegistry() else JSONArray()
        records.put(record.toJson())
        val atomic = AtomicFile(registry)
        val stream = atomic.startWrite()
        try {
            stream.writer(Charsets.UTF_8).use { it.write(JSONObject().put("schemaVersion", 2).put("records", records).toString(2)) }
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }

    private fun parse(value: JSONObject): GuestPackageRecord {
        val summary = value.getJSONObject("componentSummary")
        return GuestPackageRecord(
            internalGuestId = value.getString("internalGuestId"), packageName = value.getString("packageName"), versionName = value.optString("versionName").ifEmpty { null }, versionCode = value.getLong("versionCode"), apkPath = value.getString("apkPath"), appLabel = value.getString("appLabel"), importedAt = value.getLong("importedAt"),
            componentSummary = ComponentSummary(summary.getInt("activityCount"), summary.getInt("serviceCount"), summary.getInt("receiverCount"), summary.getInt("providerCount")), revisionId = value.optString("revisionId", value.getString("internalGuestId")), sha256 = value.optString("sha256").ifEmpty { null }, fileSize = value.optLong("fileSize", -1), schemaVersion = value.optInt("schemaVersion", 1)
        )
    }

    companion object { private val LOCK = Any() }
}
