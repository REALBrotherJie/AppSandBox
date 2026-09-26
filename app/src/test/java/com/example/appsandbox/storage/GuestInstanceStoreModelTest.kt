package com.example.appsandbox.storage

import com.example.appsandbox.model.GuestInstanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class GuestInstanceStoreModelTest {
    private val idA = "11111111-1111-4111-8111-111111111111"
    private val idB = "22222222-2222-4222-8222-222222222222"
    private fun record(root: File, id: String) = GuestInstanceRecord(id, "revision-$id", "com.example.guest", "/immutable/base.apk", "a".repeat(64), File(root, id).canonicalPath, 1, 1)
    private fun withRoot(block: (File, GuestInstanceRegistry) -> Unit) { val root = createTempDir("instance-registry"); try { block(root, GuestInstanceRegistry(root)) } finally { root.deleteRecursively() } }

    @Test fun createListGetDeleteEquivalent() = withRoot { root, registry ->
        val a = record(root, idA); registry.write(listOf(a)); assertEquals(listOf(a), registry.read()); assertEquals(a, registry.read().single()); registry.deleteRoot(a); registry.write(emptyList()); assertTrue(!File(a.dataRoot).exists()); assertTrue(registry.read().isEmpty())
    }

    @Test fun atomicWriteFailureLeavesOldRegistry() = withRoot { root, registry ->
        val a = record(root, idA); registry.write(listOf(a)); val failing = GuestInstanceRegistry(root, object : InstanceFileOps { override fun writeAtomic(target: File, text: String) = error("injected write failure"); override fun deleteTree(file: File) {} })
        try { failing.write(emptyList()); fail("expected failure") } catch (_: IllegalStateException) {}
        assertEquals(listOf(a), registry.read())
    }

    @Test fun malformedPrimaryRecoversFromBackup() = withRoot { root, registry ->
        val a = record(root, idA); registry.write(listOf(a)); registry.write(listOf(a, record(root, idB))); val primary = File(root, "registry.properties"); primary.writeText("schemaVersion=bad\n"); assertEquals(1, GuestInstanceRegistry(root).read().size)
    }

    @Test fun bothPrimaryAndBackupCorruptFailClosed() = withRoot { root, registry ->
        registry.write(listOf(record(root, idA))); File(root, "registry.properties").writeText("bad"); File(root, "registry.properties.bak").writeText("bad"); try { GuestInstanceRegistry(root).read(); fail("expected corrupt") } catch (e: GuestInstanceStoreException) { assertEquals(InstanceStoreState.CORRUPT, e.state) }
    }

    @Test fun unknownSchemaDuplicateAndEscapeRejected() = withRoot { root, registry ->
        File(root, "registry.properties").writeText("schemaVersion=9\ncount=0\n"); try { registry.read(); fail("schema") } catch (_: GuestInstanceStoreException) {}
        try { registry.write(listOf(record(root, idA), record(root, idA))); fail("duplicate") } catch (_: IllegalArgumentException) {}
        try { registry.write(listOf(record(root, idB).copy(dataRoot = File(root, "../escape").path))); fail("escape") } catch (_: IllegalArgumentException) {}
    }

    @Test fun concurrentDifferentIdsPreserveAllRecords() = withRoot { root, registry ->
        val gate = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2); val futures = listOf(idA, idB).map { id -> pool.submit { gate.await(); GuestInstanceRegistry(root).update { current -> current + record(root, id) } } }; gate.countDown(); futures.forEach { it.get() }; pool.shutdown(); assertEquals(2, GuestInstanceRegistry(root).read().map { it.instanceId }.toSet().size)
    }

    @Test fun concurrentSameIdLeavesSingleRecord() = withRoot { root, registry ->
        val gate = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2); val futures = (1..2).map { pool.submit { gate.await(); runCatching { GuestInstanceRegistry(root).update { current -> check(current.none { it.instanceId == idA }); current + record(root, idA) } } } }; gate.countDown(); futures.forEach { it.get() }; pool.shutdown(); assertEquals(1, registry.read().size)
    }

    @Test fun deleteFailureKeepsRegistryAndIsRetryable() = withRoot { root, registry ->
        val a = record(root, idA); registry.write(listOf(a)); val failing = GuestInstanceRegistry(root, object : InstanceFileOps { override fun writeAtomic(target: File, text: String) { DefaultInstanceFileOps().writeAtomic(target, text) }; override fun deleteTree(file: File) = error("injected delete failure") }); try { failing.deleteRoot(a); fail("delete") } catch (_: IllegalStateException) {}; assertEquals(listOf(a), registry.read())
    }

    @Test fun deletingADoesNotRemoveBOrGuestArtifactPath() = withRoot { root, registry ->
        val a = record(root, idA); val b = record(root, idB); registry.write(listOf(a, b)); File(a.dataRoot).mkdirs(); File(b.dataRoot).mkdirs(); registry.write(listOf(b)); DefaultInstanceFileOps().deleteTree(File(a.dataRoot)); assertTrue(File(b.dataRoot).exists()); assertEquals("/immutable/base.apk", b.guestApkPath); assertEquals(1, registry.read().size)
    }

    @Test fun symlinkEscapeRejectedWhenSupported() = withRoot { root, registry ->
        val outside = createTempDir("outside"); val link = File(root, idA); try { java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath()); val escaped = record(root, idA); try { registry.write(listOf(escaped)); fail("symlink") } catch (_: IllegalArgumentException) {} } catch (_: UnsupportedOperationException) { } finally { link.delete(); outside.deleteRecursively() }
    }
}
