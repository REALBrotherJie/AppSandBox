package com.example.appsandbox.storage

import com.example.appsandbox.model.GuestInstanceRecord
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface InstanceFileOps { fun writeAtomic(target: File, text: String); fun deleteTree(file: File) }

class DefaultInstanceFileOps : InstanceFileOps {
    override fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs(); val tmp = File(target.path + ".tmp"); val backup = File(target.path + ".bak")
        try {
            FileOutputStream(tmp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            if (target.exists()) target.copyTo(backup, true)
            try { java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        }
        catch (e: Throwable) { tmp.delete(); throw e }
    }
    override fun deleteTree(file: File) { if (file.exists() && !file.deleteRecursively()) error("instance delete failed") }
}

class GuestInstanceRegistry(root: File, private val ops: InstanceFileOps = DefaultInstanceFileOps()) {
    private val root = root.canonicalFile
    private val registry get() = File(root, "registry.properties")
    private val backup get() = File(root, "registry.properties.bak")
    private val lock = locks.computeIfAbsent(
        if (File.separatorChar == '\\') this.root.path.lowercase(Locale.ROOT) else this.root.path
    ) { ReentrantLock() }

    // Store callers acquire their instance lock first; registry operations never acquire one.
    internal fun <T> transaction(action: () -> T): T = lock.withLock {
        if (lock.holdCount > 1) return@withLock action()
        var path: File? = root
        while (path != null) {
            check(!Files.isSymbolicLink(path.toPath())) { "registry root symbolic link rejected" }
            path = path.parentFile
        }
        check(root.isDirectory || root.mkdirs() || root.isDirectory) { "registry root unavailable" }
        val file = File(root, ".registry.lock")
        check(!Files.isSymbolicLink(file.toPath()) && (!file.exists() || file.isFile)) {
            "invalid registry lock path"
        }
        FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, NOFOLLOW_LINKS).use {
            it.lock().use { action() }
        }
    }

    fun read(): List<GuestInstanceRecord> = transaction {
        if (!registry.exists() && !backup.exists()) return@transaction emptyList()
        val primary = runCatching { parse(registry) }
        if (primary.isSuccess) return@transaction primary.getOrThrow()
        val recovered = runCatching { parse(backup) }.getOrElse { throw GuestInstanceStoreException(InstanceStoreState.CORRUPT, "Instance registry and backup are corrupt") }
        runCatching { ops.writeAtomic(registry, serialize(recovered)) }; recovered
    }

    fun write(records: List<GuestInstanceRecord>) = transaction { validate(records); ops.writeAtomic(registry, serialize(records)) }
    fun update(transform: (List<GuestInstanceRecord>) -> List<GuestInstanceRecord>): List<GuestInstanceRecord> = transaction {
        val next = transform(readUnlocked())
        validate(next); ops.writeAtomic(registry, serialize(next)); next
    }
    fun deleteRoot(record: GuestInstanceRecord) = transaction { validate(listOf(record)); ops.deleteTree(File(record.dataRoot)) }

    private fun parse(file: File): List<GuestInstanceRecord> {
        if (!file.exists()) error("missing registry")
        val p = Properties(); file.inputStream().use { p.load(it) }; require(p.getProperty("schemaVersion") == "1") { "unknown schema" }
        val count = p.getProperty("count")?.toInt() ?: error("missing count"); require(count >= 0)
        return (0 until count).map { i -> GuestInstanceRecord(p.getProperty("$i.id") ?: error("missing id"), p.getProperty("$i.revision") ?: error("missing revision"), p.getProperty("$i.package") ?: error("missing package"), p.getProperty("$i.apk") ?: error("missing apk"), p.getProperty("$i.sha") ?: error("missing sha"), p.getProperty("$i.root") ?: error("missing root"), p.getProperty("$i.created")?.toLong() ?: error("missing created"), p.getProperty("$i.updated")?.toLong() ?: error("missing updated")) }.also { validate(it) }
    }

    private fun readUnlocked(): List<GuestInstanceRecord> {
        if (!registry.exists() && !backup.exists()) return emptyList()
        val primary = runCatching { parse(registry) }
        if (primary.isSuccess) return primary.getOrThrow()
        return runCatching { parse(backup) }.getOrElse { throw GuestInstanceStoreException(InstanceStoreState.CORRUPT, "Instance registry and backup are corrupt") }
    }

    private fun serialize(records: List<GuestInstanceRecord>): String = StringWriter().also { w -> Properties().apply {
        setProperty("schemaVersion", "1"); setProperty("count", records.size.toString())
        records.forEachIndexed { i, r -> setProperty("$i.id", r.instanceId); setProperty("$i.revision", r.guestRevisionId); setProperty("$i.package", r.guestPackageName); setProperty("$i.apk", r.guestApkPath); setProperty("$i.sha", r.guestSha256); setProperty("$i.root", r.dataRoot); setProperty("$i.created", r.createdAt.toString()); setProperty("$i.updated", r.updatedAt.toString()) }
    }.store(w, "AppSandbox instance registry") }.toString()

    private fun validate(records: List<GuestInstanceRecord>) {
        val ids = HashSet<String>(); val base = root.canonicalFile
        records.forEach { r -> require(UUID_PATTERN.matches(r.instanceId)) { "invalid instanceId" }; require(ids.add(r.instanceId)) { "duplicate instanceId" }; require(r.guestRevisionId.isNotBlank() && r.guestPackageName.isNotBlank() && SHA_PATTERN.matches(r.guestSha256)) { "invalid revision" }; require(r.createdAt >= 0 && r.updatedAt >= r.createdAt) { "invalid timestamps" }; val c = File(r.dataRoot).canonicalFile; require(c.parentFile == base && c.name == r.instanceId) { "dataRoot escaped" }; require(!File(root, r.instanceId).isSymbolicLink()) { "symlink dataRoot" } }
    }
    private fun File.isSymbolicLink() = runCatching { java.nio.file.Files.isSymbolicLink(toPath()) }.getOrDefault(false)
    companion object { private val locks = ConcurrentHashMap<String, ReentrantLock>(); private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"); private val SHA_PATTERN = Regex("[0-9a-fA-F]{64}") }
}
