package com.example.appsandbox.storage

import java.io.File
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

    private class FakeContext(private val root: File) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = root
    }
}
