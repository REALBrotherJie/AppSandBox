package com.example.appsandbox.storage

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GuestInstanceStoreTest {
    private val idA = "11111111-1111-4111-8111-111111111111"
    private val idB = "22222222-2222-4222-8222-222222222222"

    @Test fun createListGetDeleteUsesStoreApi() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val created = store.create(guest, idA, 10)

        assertEquals(created, store.get(idA))
        assertEquals(listOf(created), GuestInstanceStore(root).list())
        assertTrue(store.delete(idA))
        assertFalse(File(created.dataRoot).exists())
        assertTrue(store.list().isEmpty())
    }

    @Test fun createWriteFailureRollsBackDirectoryAndKeepsOldRegistry() = withFixture { root, guest ->
        val stable = GuestInstanceStore(root)
        val old = stable.create(guest, idA, 10)
        val failing = GuestInstanceStore(root, failingWrites())

        expectFailure { failing.create(guest, idB, 11) }

        assertEquals(listOf(old), stable.list())
        assertFalse(File(root, idB).exists())
        assertFalse(root.walkTopDown().any { it.name.endsWith(".tmp") })
    }

    @Test fun corruptRegistryFailsClosedForCreateListAndGet() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        store.create(guest, idA)
        File(root, "registry.properties").writeText("schemaVersion=broken\n")
        File(root, "registry.properties.bak").writeText("broken")

        listOf<() -> Unit>(
            { store.list() },
            { store.get(idA) },
            { store.create(guest, idB) }
        ).forEach { action ->
            try { action(); fail("expected CORRUPT") }
            catch (e: GuestInstanceStoreException) { assertEquals(InstanceStoreState.CORRUPT, e.state) }
        }
        assertFalse(File(root, idB).exists())
    }

    @Test fun invalidIdsAndExternalDataRootAreRejectedWithoutMutation() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        listOf("../escape", File(root.parentFile, "absolute").absolutePath).forEach { id ->
            expectFailure { store.create(guest, id) }
        }

        val outside = createTempDirectory("instance-outside").toFile()
        try {
            val external = instanceRecord(guest, idA, outside.canonicalPath)
            writeRegistry(root, listOf(external))
            try { store.delete(idA); fail("expected external root rejection") }
            catch (_: GuestInstanceStoreException) {}
            assertTrue(outside.exists())
            assertTrue(File(root, "registry.properties").exists())
        } finally { outside.deleteRecursively() }
    }

    @Test fun symlinkEscapeIsRejectedWhenSupported() = withFixture { root, guest ->
        val outside = createTempDirectory("instance-symlink-outside").toFile()
        val link = File(root, idA)
        try {
            try { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
            catch (e: UnsupportedOperationException) { assumeNoException(e) }
            catch (e: java.nio.file.FileSystemException) { assumeNoException(e) }

            try { GuestInstanceStore(root).create(guest, idA); fail("expected symlink rejection") }
            catch (e: GuestInstanceStoreException) { assertEquals(InstanceStoreState.INVALID_PATH, e.state) }
            assertTrue(outside.exists())
            assertTrue(GuestInstanceStore(root).list().isEmpty())
        } finally { Files.deleteIfExists(link.toPath()); outside.deleteRecursively() }
    }

    @Test fun concurrentDifferentIdsAcrossStoresPreservesAllRecords() = withFixture { root, guest ->
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(idA, idB).map { id -> pool.submit { gate.await(); GuestInstanceStore(root).create(guest, id) } }
            gate.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }

        val records = GuestInstanceStore(root).list()
        assertEquals(setOf(idA, idB), records.map { it.instanceId }.toSet())
        assertTrue(records.all { File(it.dataRoot).isDirectory })
    }

    @Test fun duplicateIdIsRejectedWithoutDeletingExistingDirectory() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val first = store.create(guest, idA)

        expectFailure { GuestInstanceStore(root).create(guest, idA) }

        assertEquals(listOf(first), store.list())
        assertTrue(File(first.dataRoot).isDirectory)
    }

    @Test fun concurrentSameIdHasOneWinnerAndKeepsWinnerDirectory() = withFixture { root, guest ->
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val successes = AtomicInteger()
        try {
            val futures = (1..2).map { pool.submit {
                gate.await()
                runCatching { GuestInstanceStore(root).create(guest, idA) }.onSuccess { successes.incrementAndGet() }
            } }
            gate.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }

        assertEquals(1, successes.get())
        assertEquals(listOf(idA), GuestInstanceStore(root).list().map { it.instanceId })
        assertTrue(File(root, idA).isDirectory)
    }

    @Test fun deleteWriteFailureKeepsRecordsDirectoriesAndArtifact() = withFixture { root, guest ->
        val stable = GuestInstanceStore(root)
        val a = stable.create(guest, idA)
        val b = stable.create(guest, idB)

        expectFailure { GuestInstanceStore(root, failingWrites()).delete(idA) }

        assertEquals(setOf(idA, idB), stable.list().map { it.instanceId }.toSet())
        assertTrue(File(a.dataRoot).isDirectory)
        assertTrue(File(b.dataRoot).isDirectory)
        assertTrue(File(guest.apkPath).isFile)
    }

    @Test fun deleteDirectoryFailureRestoresRegistryAndRetrySucceeds() = withFixture { root, guest ->
        val stable = GuestInstanceStore(root)
        val a = stable.create(guest, idA)
        val b = stable.create(guest, idB)
        val failingDelete = object : InstanceFileOps {
            private val delegate = DefaultInstanceFileOps()
            override fun writeAtomic(target: File, text: String) = delegate.writeAtomic(target, text)
            override fun deleteTree(file: File) = error("injected delete failure")
        }

        expectFailure { GuestInstanceStore(root, failingDelete).delete(idA) }
        assertEquals(setOf(idA, idB), stable.list().map { it.instanceId }.toSet())
        assertTrue(File(a.dataRoot).isDirectory)
        assertTrue(File(b.dataRoot).isDirectory)

        assertTrue(stable.delete(idA))
        assertEquals(listOf(idB), stable.list().map { it.instanceId })
        assertTrue(File(b.dataRoot).isDirectory)
        assertTrue(File(guest.apkPath).isFile)
    }

    private fun withFixture(block: (File, GuestPackageRecord) -> Unit) {
        val base = createTempDirectory("instance-store").toFile()
        val root = File(base, "instances")
        val artifact = File(base, "guest.apk").apply { writeText("minimal-test-apk"); setReadOnly() }
        val guest = GuestPackageRecord(
            "guest", "com.example.guest", "1", 1, artifact.canonicalPath, "Guest", 1,
            ComponentSummary(0, 0, 0, 0), "revision-1", GuestArtifactVerifier.sha256(artifact), artifact.length(), 2
        )
        try { block(root, guest) }
        finally { artifact.setWritable(true); base.deleteRecursively() }
    }

    private fun failingWrites() = object : InstanceFileOps {
        override fun writeAtomic(target: File, text: String) = error("injected write failure")
        override fun deleteTree(file: File) = DefaultInstanceFileOps().deleteTree(file)
    }

    private fun instanceRecord(guest: GuestPackageRecord, id: String, dataRoot: String) = GuestInstanceRecord(
        id, guest.revisionId, guest.packageName, guest.apkPath, guest.sha256!!, dataRoot, 1, 1
    )

    private fun writeRegistry(root: File, records: List<GuestInstanceRecord>) {
        root.mkdirs()
        val p = Properties().apply {
            setProperty("schemaVersion", "1")
            setProperty("count", records.size.toString())
            records.forEachIndexed { i, r ->
                setProperty("$i.id", r.instanceId); setProperty("$i.revision", r.guestRevisionId)
                setProperty("$i.package", r.guestPackageName); setProperty("$i.apk", r.guestApkPath)
                setProperty("$i.sha", r.guestSha256); setProperty("$i.root", r.dataRoot)
                setProperty("$i.created", r.createdAt.toString()); setProperty("$i.updated", r.updatedAt.toString())
            }
        }
        File(root, "registry.properties").outputStream().use { p.store(it, "test") }
    }

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("expected failure") }
        catch (_: IllegalArgumentException) {}
        catch (_: IllegalStateException) {}
    }
}
