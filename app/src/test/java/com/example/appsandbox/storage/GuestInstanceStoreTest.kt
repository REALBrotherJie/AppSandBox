package com.example.appsandbox.storage

import com.example.appsandbox.activity.LogicalActivityCommit
import com.example.appsandbox.activity.LogicalActivityInstanceLock
import com.example.appsandbox.activity.LogicalActivityRecord
import com.example.appsandbox.activity.LogicalActivityStore
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
import java.util.concurrent.Callable
import java.util.concurrent.TimeoutException
import java.nio.file.StandardCopyOption

class GuestInstanceStoreTest {
    private val idA = "11111111-1111-4111-8111-111111111111"
    private val idB = "22222222-2222-4222-8222-222222222222"
    private val idC = "33333333-3333-4333-8333-333333333333"

    @Test fun createListGetDeleteUsesStoreApi() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val created = store.create(guest, idA, 10)

        assertEquals(created, store.get(idA))
        assertEquals(listOf(created), GuestInstanceStore(root).list())
        assertTrue(store.delete(idA))
        assertFalse(File(created.dataRoot).exists())
        assertTrue(store.list().isEmpty())
    }

    @Test fun canonicalRootAliasUsesSameInstancePathForCreateAndDelete() = withFixture { root, guest ->
        val parent = requireNotNull(root.parentFile)
        val alias = File(parent, "alias/../${root.name}")
        val canonicalStore = GuestInstanceStore(root)
        val aliasedStore = GuestInstanceStore(alias)
        val created = aliasedStore.create(guest, idA, 10)

        assertEquals(root.canonicalFile, File(created.dataRoot).parentFile?.canonicalFile)
        assertEquals(created, canonicalStore.get(idA))
        assertTrue(canonicalStore.delete(idA))
        assertFalse(File(created.dataRoot).exists())
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
        check(root.mkdirs())
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

    @Test fun danglingSymlinkEscapeIsRejectedWhenSupported() = withFixture { root, guest ->
        check(root.mkdirs())
        val outside = File(root.parentFile, "missing-outside")
        val link = File(root, idA)
        try {
            try { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
            catch (e: UnsupportedOperationException) { assumeNoException(e) }
            catch (e: java.nio.file.FileSystemException) { assumeNoException(e) }

            try {
                GuestInstanceStore(root).create(guest, idA)
                fail("expected dangling symlink rejection")
            } catch (e: GuestInstanceStoreException) {
                assertEquals(InstanceStoreState.INVALID_PATH, e.state)
            }
            assertFalse(outside.exists())
            assertTrue(link.exists() || Files.isSymbolicLink(link.toPath()))
            assertTrue(GuestInstanceStore(root).list().isEmpty())
        } finally {
            Files.deleteIfExists(link.toPath())
            outside.deleteRecursively()
        }
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

    @Test fun activeLogicalActivityRejectsDeleteAndClosedAllowsIsolatedDelete() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val a = store.create(guest, idA)
        val b = store.create(guest, idB)
        val activityStore = LogicalActivityStore(File(a.dataRoot))
        val open = logicalRecord(a)
        activityStore.begin(open)
        val before = File(root, "registry.properties").readText()
        try {
            store.delete(idA)
            fail("expected active logical Activity rejection")
        } catch (e: GuestInstanceStoreException) {
            assertEquals(InstanceStoreState.ACTIVE_LOGICAL_ACTIVITY, e.state)
        }
        assertEquals(before, File(root, "registry.properties").readText())
        assertEquals(open, activityStore.current())
        assertEquals(a, store.get(idA))
        activityStore.complete(open.launchId, 0, "closed")
        assertTrue(store.delete(idA))
        assertFalse(File(a.dataRoot).exists())
        assertTrue(File(root, ".logical-activity-$idA.lock").isFile)
        assertEquals(listOf(b), store.list())
        assertTrue(File(b.dataRoot).isDirectory)
        assertTrue(File(guest.apkPath).isFile)
        expectFailure { activityStore.begin(open) }
        assertFalse(File(a.dataRoot).exists())
    }

    @Test fun deleteWaitsForBeginCommitThenRejectsOpen() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val a = store.create(guest, idA)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val activityStore = LogicalActivityStore(File(a.dataRoot), LogicalActivityCommit { staged, target ->
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        })
        try {
            val begin = pool.submit(Callable { activityStore.begin(logicalRecord(a)) })
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val deletion = pool.submit(Callable { runCatching { store.delete(idA) } })
            try { deletion.get(200, TimeUnit.MILLISECONDS); fail("delete bypassed begin lock") }
            catch (_: TimeoutException) {}
            release.countDown()
            begin.get(10, TimeUnit.SECONDS)
            val error = deletion.get(10, TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(error is GuestInstanceStoreException)
            assertEquals(InstanceStoreState.ACTIVE_LOGICAL_ACTIVITY, (error as GuestInstanceStoreException).state)
            assertEquals(a, store.get(idA))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test fun corruptLogicalActivityStateRefusesDeleteWithoutChangingRegistry() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val a = store.create(guest, idA)
        val b = store.create(guest, idB)
        File(a.dataRoot, "files/logical-activity.json").apply {
            parentFile!!.mkdirs()
            writeText("corrupt")
        }
        val before = File(root, "registry.properties").readText()
        expectFailure { store.delete(idA) }
        assertEquals(before, File(root, "registry.properties").readText())
        assertEquals(setOf(a, b), store.list().toSet())
        assertTrue(File(a.dataRoot).isDirectory)
        assertTrue(File(b.dataRoot).isDirectory)
    }

    @Test fun beginWaitsForDeleteThenCannotResurrectDirectory() = withFixture { root, guest ->
        val a = GuestInstanceStore(root).create(guest, idA)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delegate = DefaultInstanceFileOps()
        val store = GuestInstanceStore(root, object : InstanceFileOps {
            override fun writeAtomic(target: File, text: String) = delegate.writeAtomic(target, text)
            override fun deleteTree(file: File) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                delegate.deleteTree(file)
            }
        })
        val pool = Executors.newFixedThreadPool(2)
        try {
            val deletion = pool.submit(Callable { store.delete(idA) })
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val begin = pool.submit(Callable {
                runCatching { LogicalActivityStore(File(a.dataRoot)).begin(logicalRecord(a)) }
            })
            try { begin.get(200, TimeUnit.MILLISECONDS); fail("begin bypassed delete lock") }
            catch (_: TimeoutException) {}
            release.countDown()
            assertTrue(deletion.get(10, TimeUnit.SECONDS))
            assertTrue(begin.get(10, TimeUnit.SECONDS).isFailure)
            assertFalse(File(a.dataRoot).exists())
            assertTrue(store.list().isEmpty())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test fun foreignProcessLockBlocksBeginAndDelete() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val a = store.create(guest, idA)
        val classpath = listOf(
            LogicalActivityFileLockProcess::class.java,
            LogicalActivityStore::class.java,
            kotlin.Unit::class.java
        ).map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(
            java, "-cp", classpath, LogicalActivityFileLockProcess::class.java.name, a.dataRoot
        ).redirectErrorStream(true).start()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val ready = pool.submit(Callable { process.inputStream.bufferedReader().readLine() })
            assertEquals("LOCKED", ready.get(10, TimeUnit.SECONDS))
            val begin = pool.submit(Callable {
                runCatching { LogicalActivityStore(File(a.dataRoot)).begin(logicalRecord(a)) }
            })
            val deletion = pool.submit(Callable { runCatching { store.delete(idA) } })
            for (future in listOf(begin, deletion)) {
                try { future.get(200, TimeUnit.MILLISECONDS); fail("bypassed foreign file lock") }
                catch (_: TimeoutException) {}
            }
            process.outputStream.write('\n'.code)
            process.outputStream.flush()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            val began = begin.get(10, TimeUnit.SECONDS).isSuccess
            val deleted = deletion.get(10, TimeUnit.SECONDS).getOrDefault(false)
            assertTrue(began.xor(deleted))
            assertEquals(began, File(a.dataRoot).exists())
            assertEquals(began, store.get(idA) != null)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test fun concurrentDeletesAndCreateDoNotLoseAnyRegistryUpdates() = withFixture { root, guest ->
        val pool = Executors.newFixedThreadPool(3)
        try {
            repeat(12) {
                val store = GuestInstanceStore(root)
                store.create(guest, idA)
                store.create(guest, idB)
                val gate = CountDownLatch(1)
                val deleteA = pool.submit(Callable { gate.await(); GuestInstanceStore(root).delete(idA) })
                val deleteB = pool.submit(Callable { gate.await(); GuestInstanceStore(root).delete(idB) })
                val createC = pool.submit(Callable { gate.await(); GuestInstanceStore(root).create(guest, idC) })
                gate.countDown()
                assertTrue(deleteA.get(10, TimeUnit.SECONDS))
                assertTrue(deleteB.get(10, TimeUnit.SECONDS))
                val c = createC.get(10, TimeUnit.SECONDS)
                assertEquals(listOf(c), GuestInstanceStore(root).list())
                assertFalse(File(root, idA).exists())
                assertFalse(File(root, idB).exists())
                assertTrue(File(c.dataRoot).isDirectory)
                assertTrue(File(guest.apkPath).isFile)
                assertTrue(store.delete(idC))
            }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test fun deleteRollbackCannotOverwriteConcurrentCreate() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        val a = store.create(guest, idA)
        val b = store.create(guest, idB)
        val deleting = CountDownLatch(1)
        val release = CountDownLatch(1)
        val creatorStarted = CountDownLatch(1)
        val delegate = DefaultInstanceFileOps()
        val failing = GuestInstanceStore(root, object : InstanceFileOps {
            override fun writeAtomic(target: File, text: String) = delegate.writeAtomic(target, text)
            override fun deleteTree(file: File) {
                deleting.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                error("injected deletion failure before directory mutation")
            }
        })
        val pool = Executors.newFixedThreadPool(2)
        try {
            val deletion = pool.submit(Callable { runCatching { failing.delete(idA) } })
            assertTrue(deleting.await(10, TimeUnit.SECONDS))
            val creation = pool.submit(Callable {
                creatorStarted.countDown()
                GuestInstanceStore(root).create(guest, idC)
            })
            assertTrue(creatorStarted.await(10, TimeUnit.SECONDS))
            try { creation.get(200, TimeUnit.MILLISECONDS); fail("create escaped delete transaction") }
            catch (_: TimeoutException) {}
            assertFalse(File(root, idC).exists())
            release.countDown()
            assertTrue(deletion.get(10, TimeUnit.SECONDS).isFailure)
            val c = creation.get(10, TimeUnit.SECONDS)
            assertEquals(setOf(a, b, c), GuestInstanceStore(root).list().toSet())
            assertTrue(listOf(a, b, c).all { File(it.dataRoot).isDirectory })
            assertTrue(store.delete(idA))
            assertEquals(setOf(b, c), store.list().toSet())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test fun foreignRegistryTransactionBlocksStoreMutationsUntilReleased() = withFixture { root, guest ->
        val store = GuestInstanceStore(root)
        store.create(guest, idA)
        store.create(guest, idB)
        val classpath = listOf(
            LogicalActivityFileLockProcess::class.java,
            GuestInstanceRegistry::class.java,
            kotlin.Unit::class.java,
            org.json.JSONObject::class.java
        ).map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", classpath, LogicalActivityFileLockProcess::class.java.name, root.path, "registry"
        ).redirectErrorStream(true).start()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val ready = pool.submit(Callable { process.inputStream.bufferedReader().readLine() })
            assertEquals("LOCKED", ready.get(10, TimeUnit.SECONDS))
            val started = CountDownLatch(3)
            val deleteA = pool.submit(Callable { started.countDown(); GuestInstanceStore(root).delete(idA) })
            val deleteB = pool.submit(Callable { started.countDown(); GuestInstanceStore(root).delete(idB) })
            val createC = pool.submit(Callable { started.countDown(); GuestInstanceStore(root).create(guest, idC) })
            assertTrue(started.await(10, TimeUnit.SECONDS))
            for (future in listOf(deleteA, deleteB, createC)) {
                try { future.get(200, TimeUnit.MILLISECONDS); fail("bypassed foreign registry transaction") }
                catch (_: TimeoutException) {}
            }
            assertTrue(File(root, idA).isDirectory)
            assertTrue(File(root, idB).isDirectory)
            assertFalse(File(root, idC).exists())
            process.outputStream.write('\n'.code)
            process.outputStream.flush()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            assertTrue(deleteA.get(10, TimeUnit.SECONDS))
            assertTrue(deleteB.get(10, TimeUnit.SECONDS))
            val c = createC.get(10, TimeUnit.SECONDS)
            assertEquals(listOf(c), store.list())
            assertFalse(File(root, idA).exists())
            assertFalse(File(root, idB).exists())
            assertTrue(File(c.dataRoot).isDirectory)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun logicalRecord(instance: GuestInstanceRecord) = LogicalActivityRecord(
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", instance.instanceId,
        "33333333-3333-4333-8333-333333333333", instance.guestPackageName,
        instance.guestSha256, "${instance.guestPackageName}.GuestMainActivity"
    )

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

object LogicalActivityFileLockProcess {
    @JvmStatic fun main(args: Array<String>) {
        val hold: () -> Unit = {
            println("LOCKED")
            System.out.flush()
            check(System.`in`.read() >= 0)
        }
        if (args.getOrNull(1) == "registry") {
            GuestInstanceRegistry(File(args[0])).transaction(hold)
        } else {
            LogicalActivityInstanceLock.withLock(File(args.single()), hold)
        }
    }
}
