package com.example.appsandbox.storage

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.file.Files

/** Single owner for Guest-facing Java/framework storage roots. */
class InstanceStorageManager(context: Context, val instanceId: String) {
    val root: File
    val files: File get() = child("files")
    val cache: File get() = child("cache")
    val codeCache: File get() = child("code_cache")
    val databases: File get() = child("databases")
    val sharedPreferences: File get() = child("shared_prefs")
    val noBackup: File get() = child("no_backup")
    val deviceProtected: File get() = child("device")

    init {
        require(ID_PATTERN.matches(instanceId)) { "invalid instanceId" }
        val owner = File(context.filesDir, "virtual/instances").canonicalFile
        root = File(owner, instanceId).canonicalFile
        require(root.parentFile == owner) { "instance root escapes host storage" }
        check(root.isDirectory || root.mkdirs() || root.isDirectory) { "instance root unavailable" }
        listOf(files, cache, codeCache, databases, sharedPreferences, noBackup, deviceProtected).forEach {
            check(it.isDirectory || it.mkdirs() || it.isDirectory) { "instance path unavailable: $it" }
        }
        runCatching { Log.i(TAG, "VDATA operation=prepare instance=$instanceId root=$root") }
    }

    fun child(name: String): File {
        require(name.matches(NAME_PATTERN)) { "invalid storage child" }
        val file = File(root, name).canonicalFile
        require(file.parentFile == root) { "storage child escapes instance root" }
        return file
    }

    companion object {
        private const val TAG = "AppSandbox.M5"
        private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,80}")
        private val NAME_PATTERN = Regex("[A-Za-z0-9._-]{1,80}")

        fun delete(context: Context, instanceId: String, expectedDataRoot: String): Boolean {
            require(ID_PATTERN.matches(instanceId)) { "invalid instanceId" }
            val filesRoot = context.filesDir.canonicalFile
            val virtual = File(filesRoot, "virtual")
            val owner = File(virtual, "instances")
            require(!virtual.isSymbolicLink() && !owner.isSymbolicLink()) { "virtual storage owner is symbolic link" }
            val rawRoot = File(owner, instanceId)
            require(!rawRoot.isSymbolicLink()) { "instance root is symbolic link" }
            val canonicalOwner = owner.canonicalFile
            val root = rawRoot.canonicalFile
            require(root.parentFile == canonicalOwner && root.name == instanceId) { "instance root escapes host storage" }
            require(File(expectedDataRoot).absoluteFile.normalize() == root) { "registered dataRoot mismatch" }
            if (!rawRoot.exists()) return false
            deleteNoFollow(rawRoot)
            check(!rawRoot.exists() && !rawRoot.isSymbolicLink()) { "instance delete failed" }
            return true
        }

        private fun deleteNoFollow(file: File) {
            if (file.isSymbolicLink()) {
                check(file.delete()) { "cannot delete instance symlink: $file" }
                return
            }
            if (file.isDirectory) file.listFiles()?.forEach(::deleteNoFollow)
            check(file.delete()) { "cannot delete instance path: $file" }
        }

        private fun File.isSymbolicLink() = runCatching { Files.isSymbolicLink(toPath()) }.getOrDefault(false)
    }
}
