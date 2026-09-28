package com.example.appsandbox.experiments.act007.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GuestApplicationSessionReviewTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(): Triple<GuestApplicationSessionController, GuestApplicationSessionRequest, GuestApplicationSessionExpected> {
        val base = temporary.newFolder()
        val roots = File(base, "instances").apply { mkdirs() }
        val root = File(roots, "a").apply { mkdirs() }
        val request = GuestApplicationSessionRequest(
            "run-a", "start-a", "a", "revision", "a".repeat(64),
            "guest.example", "guest.example.Application", root.path
        )
        val expected = GuestApplicationSessionExpected(
            request.instanceId, request.revisionId, request.artifactSha256,
            request.packageName, request.applicationClass, root.path, roots.path
        )
        return Triple(
            GuestApplicationSessionController(GuestApplicationSessionRegistry(File(base, "sessions.json"))),
            request, expected
        )
    }

    private open class RecordingExecutor : GuestApplicationSessionExecutor {
        var constructs = 0
        var callbacks = 0
        override fun construct(request: GuestApplicationSessionRequest): Any {
            constructs++
            return Any()
        }
        override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) {
            callbacks++
        }
    }

    @Test fun identicalRequestDuringConstructionCannotBecomeAnotherExecutionOwner() {
        val (controller, request, expected) = fixture()
        val duplicate = RecordingExecutor()
        val first = object : RecordingExecutor() {
            override fun construct(request: GuestApplicationSessionRequest): Any {
                // Reentry fixes the interleaving at the persisted STARTING reservation.
                controller.start(request, expected, duplicate)
                return super.construct(request)
            }
        }
        try {
            assertEquals(GuestApplicationSessionState.RUNNING, controller.start(request, expected, first).state)
            assertEquals("Replayed start must not construct another Application", 0, duplicate.constructs)
            assertEquals("Replayed start must not execute another callback", 0, duplicate.callbacks)
            assertEquals(1, first.callbacks)
        } finally {
            controller.stop(request.runId, "cleanup")
        }
    }

    @Test fun recoveryDuringCloseCannotInvalidateOwnedStopOrPermitAnotherRun() {
        val (controller, request, expected) = fixture()
        var recovered: List<GuestApplicationSessionSnapshot> = emptyList()
        var competing: GuestApplicationSessionSnapshot? = null
        val executor = object : RecordingExecutor() {
            override fun close(application: Any?) {
                recovered = controller.recoverInterrupted()
                competing = controller.start(
                    request.copy(runId = "run-competing", operationId = "start-competing"),
                    expected, RecordingExecutor()
                )
            }
        }
        controller.start(request, expected, executor)
        try {
            val stopped = controller.stop(request.runId, "stop-a")
            assertTrue("In-process close is not crash recovery", recovered.isEmpty())
            assertEquals(GuestApplicationFailure.CONCURRENT_OPERATION, competing?.failure)
            assertEquals(GuestApplicationSessionState.STOPPED, stopped.state)
        } finally {
            controller.stop("run-competing", "cleanup-competing")
        }
    }

    @Test fun stopCannotReuseAnotherInstancesStartOperationId() {
        val (controller, request, expected) = fixture()
        val rootB = File(expected.allowedDataRoot, "b").apply { mkdirs() }
        val b = request.copy(runId = "run-b", operationId = "start-b", instanceId = "b", dataRoot = rootB.path)
        controller.start(request, expected, RecordingExecutor())
        controller.start(b, expected.copy(instanceId = "b", dataRoot = rootB.path), RecordingExecutor())
        try {
            val conflicting = controller.stop(b.runId, request.operationId)
            assertEquals(GuestApplicationFailure.DUPLICATE_OPERATION, conflicting.failure)
            assertEquals(
                GuestApplicationSessionState.RUNNING,
                controller.snapshots().single { it.request.runId == b.runId }.state
            )
        } finally {
            controller.stop(request.runId, "cleanup-a")
            controller.stop(b.runId, "cleanup-b")
        }
    }

    @Test fun tempWriteFailureDoesNotLeaveExecutionOwnership() {
        val base = temporary.newFolder(); val roots = File(base, "instances").apply { mkdirs() }; val root = File(roots, "a").apply { mkdirs() }
        val request = GuestApplicationSessionRequest("run", "op", "a", "rev", "a".repeat(64), "pkg", "pkg.App", root.path)
        val expected = GuestApplicationSessionExpected("a", "rev", request.artifactSha256, "pkg", "pkg.App", root.path, roots.path)
        var fail = true
        val io = object : GuestApplicationSessionRegistryIo {
            override fun write(path: File, text: String) { if (fail) { fail = false; error("temp write") }; path.writeText(text) }
            override fun copy(from: File, to: File, overwrite: Boolean) = from.copyTo(to, overwrite).let { true }
            override fun delete(path: File) = !path.exists() || path.delete()
            override fun publish(from: File, to: File) = from.renameTo(to)
        }
        val registry = GuestApplicationSessionRegistry(File(base, "sessions.json"), io)
        val result = runCatching { GuestApplicationSessionController(registry).start(request, expected, RecordingExecutor()) }
        assertTrue(result.isFailure)
    }

    @Test fun partialTempWriteKeepsOldRegistryAndCleansTemp() {
        val base = temporary.newFolder(); val file = File(base, "sessions.json")
        val normal = GuestApplicationSessionRegistry(file); normal.update { emptyList() }; val old = file.readBytes()
        val io = object : GuestApplicationSessionRegistryIo {
            override fun write(path: File, text: String) { path.outputStream().use { it.write(text.take(3).toByteArray()) }; error("partial") }
            override fun copy(from: File, to: File, overwrite: Boolean) = from.copyTo(to, overwrite).let { true }
            override fun delete(path: File) = !path.exists() || path.delete()
            override fun publish(from: File, to: File) = from.renameTo(to)
        }
        assertTrue(runCatching { GuestApplicationSessionRegistry(file, io).update { emptyList() } }.isFailure)
        assertEquals(old.toList(), file.readBytes().toList())
        assertTrue(!File(base, "sessions.json.tmp").exists())
    }

    @Test fun backupCopyFailureAndPublishFailurePreserveReadableOldState() {
        val base = temporary.newFolder(); val file = File(base, "sessions.json"); val normal = GuestApplicationSessionRegistry(file)
        normal.update { emptyList() }; val old = file.readBytes()
        fun failing(copyFail: Boolean, publishFail: Boolean) = object : GuestApplicationSessionRegistryIo {
            override fun write(path: File, text: String) = path.writeText(text)
            override fun copy(from: File, to: File, overwrite: Boolean): Boolean { if (copyFail) error("copy"); from.copyTo(to, overwrite); return true }
            override fun delete(path: File) = !path.exists() || path.delete()
            override fun publish(from: File, to: File) = !publishFail && from.renameTo(to)
        }
        assertTrue(runCatching { GuestApplicationSessionRegistry(file, failing(true, false)).update { emptyList() } }.isFailure)
        assertEquals(old.toList(), file.readBytes().toList())
        assertTrue(runCatching { GuestApplicationSessionRegistry(file, failing(false, true)).update { emptyList() } }.isFailure)
        assertEquals(old.toList(), file.readBytes().toList())
        assertEquals(0, normal.readAll().size)
    }
}
