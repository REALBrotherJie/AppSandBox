package com.example.appsandbox.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InstanceStorageManagerTest {
    @Test fun rejectsTraversalAndSeparatesRoots() {
        val host = File.createTempFile("m50", "root").apply { delete(); mkdirs() }
        val context = FakeContext(host)
        val a = InstanceStorageManager(context, "a")
        val b = InstanceStorageManager(context, "b")
        assertEquals(a.root, a.files.parentFile)
        assertEquals("a", a.root.name)
        assertEquals("b", b.root.name)
        try { InstanceStorageManager(context, "../escape"); throw AssertionError("expected traversal rejection") } catch (_: IllegalArgumentException) { }
        try { a.child("../host"); throw AssertionError("expected child rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun directoryCreationFailureFailsClosedWithoutHostFallback() {
        val host = File.createTempFile("m51", "root").apply { delete(); mkdirs() }
        File(host, "virtual").writeText("blocks directory creation")

        try {
            InstanceStorageManager(FakeContext(host), "blocked")
            throw AssertionError("expected storage preparation failure")
        } catch (_: IllegalStateException) {
            assertFalse(File(host, "blocked").exists())
        } finally {
            host.deleteRecursively()
        }
    }

    @Test fun deleteRemovesOnlyTargetAndIsIdempotent() {
        val host = File.createTempFile("m52", "root").apply { delete(); mkdirs() }
        val context = FakeContext(host)
        val target = InstanceStorageManager(context, "target")
        val sibling = InstanceStorageManager(context, "sibling")
        File(target.files, "value.txt").writeText("Alice")
        File(sibling.files, "value.txt").writeText("Bob")

        assertEquals(true, InstanceStorageManager.delete(context, "target", target.root.path))
        assertFalse(target.root.exists())
        assertEquals("Bob", File(sibling.files, "value.txt").readText())
        assertEquals(false, InstanceStorageManager.delete(context, "target", target.root.path))
        host.deleteRecursively()
    }

    @Test fun deleteDoesNotFollowChildSymlink() {
        val host = File.createTempFile("m52", "root").apply { delete(); mkdirs() }
        val outside = File.createTempFile("m52", "outside").apply { writeText("keep") }
        val context = FakeContext(host)
        val target = InstanceStorageManager(context, "target")
        val link = File(target.files, "outside-link")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            assertEquals(true, InstanceStorageManager.delete(context, "target", target.root.path))
            assertEquals("keep", outside.readText())
        } catch (_: UnsupportedOperationException) {
            // The Windows test host may not grant symlink creation; production deletion still uses NOFOLLOW checks.
        } finally {
            link.delete()
            host.deleteRecursively()
            outside.delete()
        }
    }

    private class FakeContext(private val root: File) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = root
    }
}
