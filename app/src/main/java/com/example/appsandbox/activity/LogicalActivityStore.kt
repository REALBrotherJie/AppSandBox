package com.example.appsandbox.activity

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LogicalActivityStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class LogicalActivityStore(private val instanceRoot: File) {
    private val stateFile = File(File(instanceRoot, "files"), STATE_FILE)
    private val tempFile = File(File(instanceRoot, "files"), TEMP_FILE)
    private val lock = locks.computeIfAbsent(lockKey()) { ReentrantLock() }

    fun begin(record: LogicalActivityRecord): LogicalActivityRecord = lock.withLock {
        require(record.state == LogicalActivityState.OPEN) { "logical Activity must begin open" }
        val current = read()
        if (current != null && current.state == LogicalActivityState.OPEN && current.launchId != record.launchId) {
            throw LogicalActivityStoreException("another logical Activity launch is active")
        }
        write(record)
        record
    }

    fun complete(launchId: String, resultCode: Int, resultMessage: String?): LogicalActivityRecord = lock.withLock {
        val current = read() ?: throw LogicalActivityStoreException("logical Activity launch is missing")
        if (current.launchId != launchId) throw LogicalActivityStoreException("stale logical Activity result")
        if (current.state == LogicalActivityState.CLOSED) return@withLock current
        val completed = current.copy(
            state = LogicalActivityState.CLOSED,
            resultCode = resultCode,
            resultMessage = resultMessage
        )
        write(completed)
        completed
    }

    fun current(): LogicalActivityRecord? = lock.withLock { read() }

    private fun lockKey(): String = instanceRoot.absoluteFile.normalize().path

    private fun read(): LogicalActivityRecord? {
        if (!stateFile.exists()) return null
        return try {
            LogicalActivityRecord.fromJson(org.json.JSONObject(stateFile.readText(StandardCharsets.UTF_8)))
        } catch (error: Exception) {
            throw LogicalActivityStoreException("logical Activity state is corrupt", error)
        }
    }

    private fun write(record: LogicalActivityRecord) {
        try {
            stateFile.parentFile?.mkdirs()
            tempFile.delete()
            tempFile.writeText(record.toJson().toString(), StandardCharsets.UTF_8)
            check(
                Files.move(
                    tempFile.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                ).let { true }
            )
        } catch (error: Exception) {
            tempFile.delete()
            throw LogicalActivityStoreException("logical Activity state commit failed", error)
        }
    }

    private companion object {
        const val STATE_FILE = "logical-activity.json"
        const val TEMP_FILE = "logical-activity.json.tmp"
        val locks = ConcurrentHashMap<String, ReentrantLock>()
    }
}
