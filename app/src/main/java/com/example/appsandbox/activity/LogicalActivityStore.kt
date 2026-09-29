package com.example.appsandbox.activity

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LogicalActivityStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal fun interface LogicalActivityCommit {
    fun replace(staged: File, target: File)
}

class LogicalActivityStore internal constructor(
    instanceRoot: File,
    private val commit: LogicalActivityCommit
) {
    constructor(instanceRoot: File) : this(instanceRoot, LogicalActivityCommit { staged, target ->
        Files.move(
            staged.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    })

    private val root = instanceRoot.absoluteFile.normalize()
    private val directory = File(root, "files")
    private val stateFile = File(directory, STATE_FILE)
    private val tempFile = File(directory, TEMP_FILE)
    private val lock = locks.computeIfAbsent(
        if (File.separatorChar == '\\') root.path.lowercase(Locale.ROOT) else root.path
    ) { ReentrantLock() }

    fun begin(record: LogicalActivityRecord): LogicalActivityRecord = lock.withLock {
        beginLocked(record)
    }

    // Called after a process restart when the caller wants to reuse a persisted OPEN launch.
    fun resumeOrBegin(record: LogicalActivityRecord): LogicalActivityRecord = lock.withLock {
        beginLocked(record)
    }

    private fun beginLocked(record: LogicalActivityRecord): LogicalActivityRecord {
        validate(record)
        require(record.state == LogicalActivityState.OPEN) { "logical Activity must begin open" }
        val snapshot = read()
        val current = snapshot?.record
        if (current?.state == LogicalActivityState.OPEN) {
            if (!current.sameIdentity(record)) fail("logical Activity identity mismatch")
            if (current.launchId == record.launchId) return current
            fail("another logical Activity launch is active")
        }
        if (snapshot?.closedLaunchIds?.contains(record.launchId) == true) {
            fail("closed logical Activity launch cannot reopen")
        }
        write(Snapshot(record, snapshot?.closedLaunchIds.orEmpty()))
        return record
    }

    fun complete(launchId: String, resultCode: Int, resultMessage: String?): LogicalActivityRecord = lock.withLock {
        val snapshot = read() ?: fail("logical Activity launch is missing")
        val current = snapshot.record
        if (current.launchId != launchId) fail("stale logical Activity result")
        if (current.state == LogicalActivityState.CLOSED) return@withLock current
        val completed = current.copy(
            state = LogicalActivityState.CLOSED,
            resultCode = resultCode,
            resultMessage = resultMessage
        )
        validate(completed)
        write(Snapshot(completed, snapshot.closedLaunchIds + launchId))
        completed
    }

    fun current(): LogicalActivityRecord? = lock.withLock { read()?.record }

    private fun validate(record: LogicalActivityRecord) {
        try {
            record.validate()
        } catch (error: Exception) {
            throw LogicalActivityStoreException("logical Activity record is invalid", error)
        }
    }

    private fun checkPaths() {
        var path: File? = directory
        while (path != null) {
            if (Files.isSymbolicLink(path.toPath())) fail("logical Activity symbolic link rejected")
            path = path.parentFile
        }
        if (!root.exists() && !root.mkdirs()) fail("logical Activity root unavailable")
        for (file in listOf(stateFile, tempFile)) {
            if (Files.isSymbolicLink(file.toPath())) fail("logical Activity symbolic link rejected")
            if (file.exists() && !file.isFile) fail("logical Activity state path is not a file")
        }
    }

    private fun read(): Snapshot? {
        checkPaths()
        if (!stateFile.exists()) return null
        return try {
            require(stateFile.length() <= MAX_BYTES) { "state too large" }
            val bytes = Files.newInputStream(stateFile.toPath(), NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(MAX_BYTES + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                require(count <= MAX_BYTES) { "state too large" }
                buffer.copyOf(count)
            }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.keys().asSequence().toSet() == setOf("schemaVersion", "record", "closedLaunchIds"))
            require(json.getInt("schemaVersion") == SCHEMA_VERSION)
            val record = LogicalActivityRecord.fromJson(json.getJSONObject("record"))
            validate(record)
            val ids = json.getJSONArray("closedLaunchIds")
            require(ids.length() <= MAX_CLOSED)
            val closed = (0 until ids.length()).map { index ->
                val id = ids.getString(index)
                require(GuestActivityLaunchPolicy.resultBelongsTo(id, id))
                id
            }.toSet()
            require(closed.size == ids.length())
            require(record.state == LogicalActivityState.CLOSED || record.launchId !in closed)
            Snapshot(record, closed)
        } catch (error: LogicalActivityStoreException) {
            throw error
        } catch (error: Exception) {
            throw LogicalActivityStoreException("logical Activity state is corrupt", error)
        }
    }

    private fun write(snapshot: Snapshot) {
        var ownsTemp = false
        try {
            checkPaths()
            check(directory.isDirectory || directory.mkdirs()) { "cannot create state directory" }
            val bytes = JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("record", snapshot.record.toJson())
                .put("closedLaunchIds", JSONArray(snapshot.closedLaunchIds.toList()))
                .toString()
                .toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BYTES) { "state too large" }
            Files.deleteIfExists(tempFile.toPath())
            FileChannel.open(
                tempFile.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                NOFOLLOW_LINKS
            ).use { channel ->
                ownsTemp = true
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            checkPaths()
            commit.replace(tempFile, stateFile)
            ownsTemp = false
        } catch (error: LogicalActivityStoreException) {
            if (ownsTemp) runCatching { Files.deleteIfExists(tempFile.toPath()) }
            throw error
        } catch (error: Exception) {
            if (ownsTemp) runCatching { Files.deleteIfExists(tempFile.toPath()) }
            throw LogicalActivityStoreException("logical Activity state commit failed", error)
        }
    }

    private data class Snapshot(
        val record: LogicalActivityRecord,
        val closedLaunchIds: Set<String>
    )

    private fun fail(message: String): Nothing = throw LogicalActivityStoreException(message)

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_BYTES = 131072
        const val MAX_CLOSED = 256
        val locks = ConcurrentHashMap<String, ReentrantLock>()
    }
}
