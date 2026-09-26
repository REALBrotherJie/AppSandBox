package com.example.appsandbox.contract.state

import com.example.appsandbox.contract.GuestAction
import com.example.appsandbox.contract.GuestViewSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

class GuestStateStoreTest {
    @Test fun concurrentIncrementAcrossSessionsDoesNotLoseUpdates() = withRoot { root ->
        val workers = 8
        val increments = 40
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val futures = (1..workers).map {
                pool.submit {
                    val session = GuestViewSession(root)
                    gate.await()
                    repeat(increments) { session.execute(GuestAction.INCREMENT) }
                }
            }
            gate.countDown()
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(workers * increments, GuestViewSession(root).counter())
        assertTrue(File(root, "files/counter.txt.bak").isFile)
        assertFalse(File(root, "files/counter.txt.tmp").exists())
    }

    @Test fun resetAndToggleCompeteWithoutPartialOrLostFile() = withRoot { root ->
        GuestViewSession(root).execute(GuestAction.INCREMENT)
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val reset = pool.submit { gate.await(); repeat(80) { GuestViewSession(root).execute(GuestAction.RESET) } }
            val toggle = pool.submit { gate.await(); repeat(80) { GuestViewSession(root).execute(GuestAction.TOGGLE) } }
            gate.countDown()
            reset.get(20, TimeUnit.SECONDS)
            toggle.get(20, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        assertTrue(GuestViewSession(root).counter() in 0..1)
        assertFalse(File(root, "files/counter.txt.tmp").exists())
    }

    @Test fun backupRecoversCorruptPrimaryAndRepairsIt() = withRoot { root ->
        val session = GuestViewSession(root)
        session.execute(GuestAction.INCREMENT)
        session.execute(GuestAction.INCREMENT)
        val state = File(root, "files/counter.txt")
        val backup = File(root, "files/counter.txt.bak")
        assertTrue(backup.isFile)

        state.writeText("corrupt")

        assertEquals(1, session.counter())
        assertEquals(1, GuestViewSession(root).counter())
        assertTrue(state.readText().contains("checksum="))
    }

    @Test fun dualCorruptionFailsClosedInsteadOfResettingToZero() = withRoot { root ->
        val session = GuestViewSession(root)
        session.execute(GuestAction.INCREMENT)
        session.execute(GuestAction.INCREMENT)
        File(root, "files/counter.txt").writeText("broken-primary")
        File(root, "files/counter.txt.bak").writeText("broken-backup")

        expectStoreFailure { session.counter() }
        expectStoreFailure { session.execute(GuestAction.INCREMENT) }
    }

    @Test fun staleTempIsIgnoredAndReplacedByNextAtomicCommit() = withRoot { root ->
        val session = GuestViewSession(root)
        session.execute(GuestAction.INCREMENT)
        File(root, "files/counter.txt.tmp").writeText("stale")

        assertEquals(1, session.counter())
        assertEquals(2, session.execute(GuestAction.INCREMENT))
        assertFalse(File(root, "files/counter.txt.tmp").exists())
    }

    @Test fun symlinkedInstanceRootAndStateFilesFailClosed() = withRoot { root ->
        val outside = createTempDirectory("guest-state-outside").toFile()
        val linkRoot = File(root.parentFile, "root-link")
        try {
            createLink(linkRoot, outside)
            expectStoreFailure { GuestViewSession(linkRoot).execute(GuestAction.INCREMENT) }
            assertFalse(File(outside, "files/counter.txt").exists())

            val stateDir = File(root, "files")
            stateDir.mkdirs()
            val stateLink = File(stateDir, "counter.txt")
            val outsideState = File(outside, "counter.txt").apply { writeText("outside") }
            createLink(stateLink, outsideState)
            expectStoreFailure { GuestViewSession(root).counter() }
            assertEquals("outside", outsideState.readText())
        } finally {
            Files.deleteIfExists(linkRoot.toPath())
            Files.deleteIfExists(File(root, "files/counter.txt").toPath())
            outside.deleteRecursively()
        }
    }

    @Test fun separateRootsRemainIndependentUnderConcurrentUpdates() = withRoots { a, b ->
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit { gate.await(); repeat(20) { GuestViewSession(a).execute(GuestAction.INCREMENT) } }
            val second = pool.submit { gate.await(); repeat(7) { GuestViewSession(b).execute(GuestAction.INCREMENT) } }
            gate.countDown()
            first.get(20, TimeUnit.SECONDS)
            second.get(20, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
        assertEquals(20, GuestViewSession(a).counter())
        assertEquals(7, GuestViewSession(b).counter())
    }

    @Test fun separateRootsRemainIndependentUnderConcurrentMixedActions() = withRoots { a, b ->
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit {
                gate.await()
                repeat(30) {
                    GuestViewSession(a).execute(GuestAction.INCREMENT)
                    GuestViewSession(a).execute(GuestAction.RESET)
                    GuestViewSession(a).execute(GuestAction.TOGGLE)
                }
                repeat(2) { GuestViewSession(a).execute(GuestAction.INCREMENT) }
            }
            val second = pool.submit {
                gate.await()
                repeat(30) {
                    GuestViewSession(b).execute(GuestAction.TOGGLE)
                    GuestViewSession(b).execute(GuestAction.RESET)
                    GuestViewSession(b).execute(GuestAction.INCREMENT)
                }
                GuestViewSession(b).execute(GuestAction.TOGGLE)
            }
            gate.countDown()
            first.get(20, TimeUnit.SECONDS)
            second.get(20, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        assertEquals(3, GuestViewSession(a).counter())
        assertEquals(0, GuestViewSession(b).counter())
    }

    @Test fun failedTemporaryWritePreservesCommittedStateAndOtherRoot() = withRoots { a, b ->
        val first = GuestViewSession(a)
        val second = GuestViewSession(b)
        assertEquals(1, first.execute(GuestAction.INCREMENT))
        assertEquals(1, second.execute(GuestAction.INCREMENT))

        val blocker = File(a, "files/counter.txt.tmp")
        assertTrue(blocker.mkdir())
        File(blocker, "blocker").writeText("occupied")
        try {
            expectStoreFailure { first.execute(GuestAction.INCREMENT) }
            assertEquals(1, GuestViewSession(a).counter())
            assertEquals(2, second.execute(GuestAction.INCREMENT))
        } finally {
            blocker.deleteRecursively()
        }

        assertEquals(2, GuestViewSession(a).execute(GuestAction.INCREMENT))
        assertEquals(2, GuestViewSession(b).counter())
    }

    private fun createLink(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (error: UnsupportedOperationException) {
            assumeNoException(error)
        } catch (error: java.nio.file.FileSystemException) {
            assumeNoException(error)
        }
    }

    private fun expectStoreFailure(action: () -> Unit) {
        try {
            action()
            fail("expected GuestStateStoreException")
        } catch (_: GuestStateStoreException) {
        }
    }

    private fun withRoot(block: (File) -> Unit) {
        val base = createTempDirectory("guest-state-store").toFile()
        try {
            block(File(base, "instance"))
        } finally {
            base.deleteRecursively()
        }
    }

    private fun withRoots(block: (File, File) -> Unit) {
        val base = createTempDirectory("guest-state-roots").toFile()
        try {
            block(File(base, "instance-a"), File(base, "instance-b"))
        } finally {
            base.deleteRecursively()
        }
    }
}
