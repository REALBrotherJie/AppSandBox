package com.example.appsandbox.storage

import java.io.File

/** Maps framework file requests without rewriting an already-physical instance path. */
class InstancePathPolicy(
    private val guestPackageName: String,
    instanceRoot: File
) {
    private val root = instanceRoot.canonicalFile
    private val logicalRoots = listOf(
        "/data/data/$guestPackageName",
        "/data/user/0/$guestPackageName"
    )

    fun databasePath(name: String): File {
        val logicalRoot = logicalRoots.firstOrNull { path ->
            name == path || name.startsWith(path + "/")
        }
        if (logicalRoot != null) {
            val suffix = name.removePrefix(logicalRoot).trimStart('/')
            val mapped = File(root, suffix).canonicalFile
            check(isWithin(mapped, root)) { "Guest database path escapes instance root" }
            return mapped
        }
        val input = File(name)
        if (!input.isAbsolute) {
            return File(ensureDirectory(File(root, "databases")), name)
        }
        val normalized = input.canonicalFile
        if (isWithin(normalized, root)) return normalized
        return normalized
    }

    private fun ensureDirectory(directory: File): File = directory.apply {
        check(isDirectory || mkdirs() || isDirectory) { "Guest database directory unavailable: $this" }
    }

    private fun isWithin(file: File, owner: File): Boolean =
        file == owner || file.path.startsWith(owner.path + File.separator)
}
