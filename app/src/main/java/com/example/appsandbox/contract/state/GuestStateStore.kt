package com.example.appsandbox.contract.state

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class GuestStateStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Small, per-instance state store used by the declarative Guest contract.
 *
 * The lock file serializes separate Host instances/processes. The JVM mutex
 * avoids OverlappingFileLockException when two sessions share one process.
 */
class GuestStateStore(private val instanceRoot: File) {
    private val rootPath = instanceRoot.toPath().toAbsolutePath().normalize()
    private val filesPath = rootPath.resolve(FILES_DIRECTORY)
    private val statePath = filesPath.resolve(STATE_FILE)
    private val backupPath = filesPath.resolve(BACKUP_FILE)
    private val tempPath = filesPath.resolve(TEMP_FILE)
    private val lockPath = filesPath.resolve(LOCK_FILE)

    fun readCounter(): Int = withExclusiveLock { loadCounter(repairPrimary = true) }

    fun updateCounter(transform: (Int) -> Int): Int = withExclusiveLock {
        val current = loadCounter(repairPrimary = true)
        val next = transform(current)
        require(next in 0..MAX_COUNTER) { "Guest state counter out of range" }
        persist(next)
        next
    }

    private fun <T> withExclusiveLock(block: () -> T): T {
        val mutex = mutexes.computeIfAbsent(lockKey()) { ReentrantLock() }
        return mutex.withLock {
            try {
                ensureLayout()
                FileChannel.open(lockPath, *CREATE_WRITE_NOFOLLOW).use { channel ->
                    channel.lock().use { block() }
                }
            } catch (error: GuestStateStoreException) {
                throw error
            } catch (error: Exception) {
                throw GuestStateStoreException("Guest state unavailable", error)
            }
        }
    }

    private fun lockKey(): String {
        ensureNoSymlinkAncestors(rootPath)
        return try {
            rootPath.toFile().canonicalPath.lowercase(Locale.ROOT)
        } catch (error: IOException) {
            throw GuestStateStoreException("Guest state path is unavailable", error)
        }
    }

    private fun ensureLayout() {
        ensureDirectory(rootPath, "instance root")
        ensureDirectory(filesPath, "state directory")
        listOf(statePath, backupPath, tempPath, lockPath).forEach { rejectSymlink(it, "state path") }
    }

    private fun ensureDirectory(path: Path, description: String) {
        ensureNoSymlinkAncestors(path)
        rejectSymlink(path, description)
        try {
            if (!Files.exists(path, *NOFOLLOW)) {
                Files.createDirectories(path)
            }
        } catch (error: IOException) {
            throw GuestStateStoreException("Guest $description is unavailable", error)
        }
        rejectSymlink(path, description)
        if (!Files.isDirectory(path, *NOFOLLOW)) {
            throw GuestStateStoreException("Guest $description is not a directory")
        }
    }

    private fun loadCounter(repairPrimary: Boolean): Int {
        val primary = readCandidate(statePath)
        if (primary is Candidate.Valid) {
            return primary.value
        }

        val backup = readCandidate(backupPath)
        if (backup is Candidate.Valid) {
            if (repairPrimary) {
                repairPrimary(backup.value)
            }
            return backup.value
        }

        if (primary is Candidate.Missing && backup is Candidate.Missing) {
            return 0
        }
        throw GuestStateStoreException("Guest state is corrupted")
    }

    private fun readCandidate(path: Path): Candidate {
        rejectSymlink(path, "state file")
        if (!Files.exists(path, *NOFOLLOW)) {
            return Candidate.Missing
        }
        return try {
            val bytes = Files.newByteChannel(path, *READ_NOFOLLOW).use { channel ->
                val buffer = java.nio.ByteBuffer.allocate(MAX_STATE_BYTES)
                val output = java.io.ByteArrayOutputStream()
                while (channel.read(buffer) > 0) {
                    buffer.flip()
                    val chunk = ByteArray(buffer.remaining())
                    buffer.get(chunk)
                    output.write(chunk)
                    buffer.clear()
                    if (output.size() > MAX_STATE_BYTES) {
                        return@use null
                    }
                }
                output.toByteArray()
            } ?: return Candidate.Corrupt
            parse(bytes)
        } catch (_: Exception) {
            Candidate.Corrupt
        }
    }

    private fun parse(bytes: ByteArray): Candidate {
        if (bytes.isEmpty() || bytes.size > MAX_STATE_BYTES) {
            return Candidate.Corrupt
        }
        val text = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull() ?: return Candidate.Corrupt
        val lines = text.split('\n')
        if (lines.size != 4 || lines[0] != "schema=1" || !lines[1].startsWith("counter=") ||
            !lines[2].startsWith("checksum=") || lines[3].isNotEmpty()
        ) {
            return Candidate.Corrupt
        }
        val counterText = lines[1].removePrefix("counter=")
        val checksum = lines[2].removePrefix("checksum=")
        if (!counterText.matches(Regex("0|[1-9][0-9]{0,5}")) || !checksum.matches(Regex("[0-9a-f]{64}"))) {
            return Candidate.Corrupt
        }
        val counter = counterText.toInt()
        val payload = "schema=1\ncounter=$counterText\n".toByteArray(StandardCharsets.UTF_8)
        val expected = sha256(payload)
        return if (checksum == expected) Candidate.Valid(counter) else Candidate.Corrupt
    }

    private fun persist(counter: Int) {
        val state = encode(counter)
        writeTemp(state)
        val primary = readCandidate(statePath)
        try {
            if (primary is Candidate.Valid) {
                atomicMove(statePath, backupPath)
            }
            atomicMove(tempPath, statePath)
            forceDirectory()
        } catch (error: Exception) {
            deleteTempAfterFailure()
            throw when (error) {
                is GuestStateStoreException -> error
                else -> GuestStateStoreException("Guest state commit failed", error)
            }
        }
    }

    private fun repairPrimary(counter: Int) {
        writeTemp(encode(counter))
        try {
            atomicMove(tempPath, statePath)
            forceDirectory()
        } catch (error: Exception) {
            deleteTempAfterFailure()
            throw when (error) {
                is GuestStateStoreException -> error
                else -> GuestStateStoreException("Guest state recovery failed", error)
            }
        }
    }

    private fun writeTemp(bytes: ByteArray) {
        rejectSymlink(tempPath, "temporary state file")
        try {
            Files.deleteIfExists(tempPath)
            FileChannel.open(tempPath, *CREATE_NEW_WRITE_NOFOLLOW).use { channel ->
                var offset = 0
                while (offset < bytes.size) {
                    offset += channel.write(java.nio.ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                }
                channel.force(true)
            }
        } catch (error: Exception) {
            throw GuestStateStoreException("Guest state temporary write failed", error)
        }
    }

    private fun atomicMove(source: Path, target: Path) {
        rejectSymlink(target, "state path")
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            throw GuestStateStoreException("Guest state filesystem lacks atomic move", error)
        } catch (error: IOException) {
            throw GuestStateStoreException("Guest state atomic move failed", error)
        }
    }

    private fun forceDirectory() {
        try {
            FileChannel.open(filesPath, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: UnsupportedOperationException) {
            // Directory fsync is unavailable on some Android filesystems.
        } catch (_: IOException) {
            // The file itself was already forced; directory fsync is best effort.
        }
    }

    private fun deleteTempAfterFailure() {
        runCatching {
            rejectSymlink(tempPath, "temporary state file")
            Files.deleteIfExists(tempPath)
        }
    }

    private fun encode(counter: Int): ByteArray {
        val counterText = counter.toString()
        val payload = "schema=1\ncounter=$counterText\n"
        return (payload + "checksum=${sha256(payload.toByteArray(StandardCharsets.UTF_8))}\n")
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun rejectSymlink(path: Path, description: String) {
        if (Files.isSymbolicLink(path)) {
            throw GuestStateStoreException("Guest $description must not be a symbolic link")
        }
    }

    private fun ensureNoSymlinkAncestors(path: Path) {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                throw GuestStateStoreException("Guest state path must not contain symbolic links")
            }
            current = current.parent
        }
    }

    private sealed interface Candidate {
        data object Missing : Candidate
        data class Valid(val value: Int) : Candidate
        data object Corrupt : Candidate
    }

    private companion object {
        const val FILES_DIRECTORY = "files"
        const val STATE_FILE = "counter.txt"
        const val BACKUP_FILE = "counter.txt.bak"
        const val TEMP_FILE = "counter.txt.tmp"
        const val LOCK_FILE = "counter.txt.lock"
        const val MAX_COUNTER = 999_999
        const val MAX_STATE_BYTES = 256

        val NOFOLLOW = arrayOf<LinkOption>(LinkOption.NOFOLLOW_LINKS)
        val CREATE_WRITE_NOFOLLOW: Array<OpenOption> = arrayOf(
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        )
        val CREATE_NEW_WRITE_NOFOLLOW: Array<OpenOption> = arrayOf(
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        )
        val READ_NOFOLLOW: Array<OpenOption> = arrayOf(
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS
        )
        val mutexes = ConcurrentHashMap<String, ReentrantLock>()
    }
}
