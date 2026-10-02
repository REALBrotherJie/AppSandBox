package com.example.appsandbox.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class InstancePathPolicyTest {
    @Test fun database_path_is_idempotent_for_physical_instance_paths() {
        val root = temporaryRoot()
        val policy = InstancePathPolicy("guest.package", root)
        val physical = File(root, "no_backup/androidx.work.workdb").canonicalFile

        assertEquals(physical, policy.databasePath(physical.path))
        assertEquals(physical, policy.databasePath(policy.databasePath(physical.path).path))
        root.deleteRecursively()
    }

    @Test fun logical_database_path_maps_once_and_relative_name_uses_database_directory() {
        val root = temporaryRoot()
        val policy = InstancePathPolicy("guest.package", root)
        val logical = "/data/user/0/guest.package/no_backup/androidx.work.workdb"
        val mapped = File(root, "no_backup/androidx.work.workdb").canonicalFile

        assertEquals(mapped, policy.databasePath(logical))
        assertEquals(mapped, policy.databasePath(policy.databasePath(logical).path))
        assertEquals(File(root, "databases/plain.db"), policy.databasePath("plain.db"))
        root.deleteRecursively()
    }

    private fun temporaryRoot(): File = File.createTempFile("instance-path", "root").apply {
        delete()
        mkdirs()
        File(this, "no_backup").mkdirs()
    }.canonicalFile
}
