package com.example.appsandbox.experiments.act007.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GuestApplicationSessionTest {
    private fun fixture(): Fixture {
        val base = Files.createTempDirectory("act007").toFile()
        val instances = File(base, "instances").apply { mkdirs() }
        val root = File(instances, "instance-a").apply { mkdirs() }
        val request = GuestApplicationSessionRequest("run-1", "op-1", "instance-a", "revision-a",
            "a".repeat(64), "guest.package", "guest.Application", root.path)
        val expected = GuestApplicationSessionExpected("instance-a", "revision-a", "a".repeat(64),
            "guest.package", "guest.Application", root.path, instances.path)
        return Fixture(base, GuestApplicationSessionRegistry(File(base, "sessions.json")), request, expected)
    }

    private data class Fixture(val base: File, val registry: GuestApplicationSessionRegistry,
        val request: GuestApplicationSessionRequest, val expected: GuestApplicationSessionExpected)

    private class Executor(private val constructFailure: Boolean = false, private val createFailure: Boolean = false) : GuestApplicationSessionExecutor {
        val constructs = AtomicInteger(); val creates = AtomicInteger(); val closes = AtomicInteger()
        override fun construct(request: GuestApplicationSessionRequest): Any { constructs.incrementAndGet(); if (constructFailure) error("construct"); return Any() }
        override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) { creates.incrementAndGet(); if (createFailure) error("onCreate") }
        override fun close(application: Any?) { closes.incrementAndGet() }
    }

    @Test fun startStopAndRestartUseMonotonicStatesAndNewRunId() {
        val f = fixture(); val controller = GuestApplicationSessionController(f.registry) { 7L }; val executor = Executor()
        val running = controller.start(f.request, f.expected, executor)
        assertEquals(GuestApplicationSessionState.RUNNING, running.state)
        assertEquals(listOf(1, 1, 1, 1), listOf(running.constructorAttempted, running.constructorCompleted, running.onCreateAttempted, running.onCreateCompleted))
        assertEquals(running, controller.start(f.request, f.expected, executor))
        val stopped = controller.stop(f.request.runId, "stop-1")
        assertEquals(GuestApplicationSessionState.STOPPED, stopped.state); assertEquals(1, executor.closes.get())
        val restarted = controller.start(f.request.copy(runId = "run-2", operationId = "op-2"), f.expected, Executor())
        assertEquals(GuestApplicationSessionState.RUNNING, restarted.state); assertEquals(2, controller.snapshots().size)
    }

    @Test fun validationFailuresNeverCallExecutor() {
        val f = fixture()
        val cases = listOf(
            f.request.copy(instanceId = "stale"), f.request.copy(revisionId = "stale"),
            f.request.copy(artifactSha256 = "b".repeat(64)), f.request.copy(packageName = "wrong"),
            f.request.copy(applicationClass = "wrong.Application"), f.request.copy(dataRoot = File(f.base, "escaped").path)
        )
        cases.forEach { request ->
            val executor = Executor(); val result = GuestApplicationSessionController(f.registry).start(request.copy(runId = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString()), f.expected, executor)
            assertEquals(GuestApplicationSessionState.FAILED, result.state); assertEquals(0, executor.constructs.get()); assertEquals(0, executor.creates.get())
        }
    }

    @Test fun constructorAndOnCreateFailuresAreIsolatedAndCounted() {
        val f = fixture(); val controller = GuestApplicationSessionController(f.registry)
        val constructor = controller.start(f.request, f.expected, Executor(constructFailure = true))
        assertEquals(GuestApplicationFailure.CONSTRUCTION_FAILED, constructor.failure)
        assertEquals(listOf(1, 0, 0, 0), listOf(constructor.constructorAttempted, constructor.constructorCompleted, constructor.onCreateAttempted, constructor.onCreateCompleted))
        val secondRoot = File(f.expected.allowedDataRoot, "instance-b").apply { mkdirs() }
        val requestB = f.request.copy(runId = "run-b", operationId = "op-b", instanceId = "instance-b", dataRoot = secondRoot.path)
        val expectedB = f.expected.copy(instanceId = "instance-b", dataRoot = secondRoot.path)
        val onCreate = controller.start(requestB, expectedB, Executor(createFailure = true))
        assertEquals(GuestApplicationFailure.ON_CREATE_FAILED, onCreate.failure)
        assertEquals(listOf(1, 1, 1, 0), listOf(onCreate.constructorAttempted, onCreate.constructorCompleted, onCreate.onCreateAttempted, onCreate.onCreateCompleted))
    }

    @Test fun concurrentControllersAllowOneStartPerInstance() {
        val f = fixture(); val gate = CountDownLatch(1); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val slow = object : GuestApplicationSessionExecutor {
            override fun construct(request: GuestApplicationSessionRequest): Any { entered.countDown(); release.await(); return Any() }
            override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) = Unit
        }
        val pool = Executors.newFixedThreadPool(2)
        val first = pool.submit<GuestApplicationSessionSnapshot> { gate.await(); GuestApplicationSessionController(f.registry).start(f.request, f.expected, slow) }
        gate.countDown(); entered.await()
        val second = pool.submit<GuestApplicationSessionSnapshot> { GuestApplicationSessionController(f.registry).start(f.request.copy(runId = "run-2", operationId = "op-2"), f.expected, Executor()) }
        val rejected = second.get(); release.countDown(); val running = first.get(); pool.shutdown()
        assertEquals(GuestApplicationFailure.CONCURRENT_OPERATION, rejected.failure)
        assertEquals(GuestApplicationSessionState.RUNNING, running.state)
    }

    @Test fun onCreateReentryFailsClosedWithoutSecondCallback() {
        val f = fixture(); val controller = GuestApplicationSessionController(f.registry); var nested: GuestApplicationSessionSnapshot? = null
        val executor = object : GuestApplicationSessionExecutor {
            override fun construct(request: GuestApplicationSessionRequest) = Any()
            override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) {
                nested = controller.start(request.copy(runId = "nested", operationId = "nested"), f.expected, Executor())
            }
        }
        assertEquals(GuestApplicationSessionState.RUNNING, controller.start(f.request, f.expected, executor).state)
        assertEquals(GuestApplicationFailure.CONCURRENT_OPERATION, nested?.failure)
    }

    @Test fun concurrentStopsAllowOnlyOneStoppingReservation() {
        val f = fixture(); val closeEntered = CountDownLatch(1); val closeRelease = CountDownLatch(1)
        val executor = object : GuestApplicationSessionExecutor {
            override fun construct(request: GuestApplicationSessionRequest) = Any()
            override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) = Unit
            override fun close(application: Any?) { closeEntered.countDown(); closeRelease.await() }
        }
        val controllerA = GuestApplicationSessionController(f.registry)
        assertEquals(GuestApplicationSessionState.RUNNING, controllerA.start(f.request, f.expected, executor).state)
        val pool = Executors.newFixedThreadPool(2)
        val first = pool.submit<GuestApplicationSessionSnapshot> { controllerA.stop(f.request.runId, "stop-1") }
        closeEntered.await()
        val second = pool.submit<GuestApplicationSessionSnapshot> { GuestApplicationSessionController(f.registry).stop(f.request.runId, "stop-2") }.get()
        closeRelease.countDown(); val stopped = first.get(); pool.shutdown()
        assertEquals(GuestApplicationFailure.INVALID_TRANSITION, second.failure)
        assertEquals(GuestApplicationSessionState.STOPPED, stopped.state)
    }

    @Test fun processRestartMarksInterruptedStateFailedAndOldRunCannotPass() {
        val f = fixture(); val interrupted = GuestApplicationSessionSnapshot(f.request, GuestApplicationSessionState.STARTING,
            constructorAttempted = 1, updatedAt = 1L)
        f.registry.update { listOf(interrupted) }
        val afterRestart = GuestApplicationSessionController(f.registry) { 2L }
        assertEquals(GuestApplicationFailure.CRASH_RECOVERY, afterRestart.recoverInterrupted().single().failure)
        val replay = afterRestart.start(f.request, f.expected, Executor())
        assertEquals(GuestApplicationFailure.CRASH_RECOVERY, replay.failure)
    }

    @Test fun registryRecoversBackupAndRejectsCorruption() {
        val f = fixture(); val controller = GuestApplicationSessionController(f.registry)
        controller.start(f.request, f.expected, Executor())
        val main = File(f.base, "sessions.json"); val backup = File(f.base, "sessions.json.bak")
        assertTrue(main.renameTo(backup)); assertEquals(1, f.registry.readAll().size)
        main.writeText("not-json")
        assertThrows(IllegalStateException::class.java) { f.registry.readAll() }
    }

    @Test fun instanceDataRemainsIsolatedAcrossFailuresAndStop() {
        val f = fixture(); val rootB = File(f.expected.allowedDataRoot, "instance-b").apply { mkdirs() }
        val requestB = f.request.copy(runId = "run-b", operationId = "op-b", instanceId = "instance-b", dataRoot = rootB.path)
        val expectedB = f.expected.copy(instanceId = "instance-b", dataRoot = rootB.path)
        val writingA = object : GuestApplicationSessionExecutor {
            override fun construct(request: GuestApplicationSessionRequest) = Any()
            override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) { File(request.dataRoot, "marker").writeText("A") }
        }
        val failingB = object : GuestApplicationSessionExecutor {
            override fun construct(request: GuestApplicationSessionRequest) = Any()
            override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) { File(request.dataRoot, "marker").writeText("B"); error("B failed") }
        }
        val controller = GuestApplicationSessionController(f.registry)
        assertEquals(GuestApplicationSessionState.RUNNING, controller.start(f.request, f.expected, writingA).state)
        assertEquals(GuestApplicationSessionState.FAILED, controller.start(requestB, expectedB, failingB).state)
        controller.stop(f.request.runId, "stop-a")
        assertEquals("A", File(f.request.dataRoot, "marker").readText()); assertEquals("B", File(requestB.dataRoot, "marker").readText())
    }

    @Test fun stopFromAnotherControllerClosesSharedLiveHandleWithoutChangingStartIdentity() {
        val f = fixture(); val executor = Executor()
        assertEquals(GuestApplicationSessionState.RUNNING, GuestApplicationSessionController(f.registry).start(f.request, f.expected, executor).state)
        val stopped = GuestApplicationSessionController(f.registry).stop(f.request.runId, "stop-cross-controller")
        assertEquals(GuestApplicationSessionState.STOPPED, stopped.state)
        assertEquals(f.request, stopped.request)
        assertEquals("stop-cross-controller", stopped.lastOperationId)
        assertEquals(1, executor.closes.get())
    }

    @Test fun runningWithoutSharedLiveIsRecoveredAsCrash() {
        val f = fixture(); val first = GuestApplicationSessionController(f.registry)
        assertEquals(GuestApplicationSessionState.RUNNING, first.start(f.request, f.expected, Executor()).state)
        GuestApplicationSessionController.clearLiveForTest()
        assertEquals(GuestApplicationFailure.CRASH_RECOVERY, first.recoverInterrupted().single().failure)
    }

    @Test fun symlinkedDataRootIsRejectedBeforeExecutor() {
        val f = fixture(); val outside = File(f.base, "outside").apply { mkdirs() }
        val link = File(f.expected.allowedDataRoot, "link")
        try { Files.createSymbolicLink(link.toPath(), outside.toPath()) } catch (_: UnsupportedOperationException) { assumeTrue("symlink unsupported", false) } catch (_: java.nio.file.FileSystemException) { assumeTrue("symlink unsupported", false) }
        val executor = Executor()
        val result = GuestApplicationSessionController(f.registry).start(f.request.copy(dataRoot = link.path), f.expected.copy(dataRoot = link.path), executor)
        assertEquals(GuestApplicationFailure.PATH_ESCAPE, result.failure)
        assertEquals(0, executor.constructs.get())
    }

    @Test fun sameRunIdInDifferentRegistriesDoesNotShareOrCloseLiveHandle() {
        val a = fixture(); val baseB = Files.createTempDirectory("act007-b").toFile(); val rootB = File(baseB, "instances/instance-a").apply { mkdirs() }
        val requestB = a.request.copy(dataRoot = rootB.path)
        val expectedB = a.expected.copy(dataRoot = rootB.path, allowedDataRoot = rootB.parentFile.path)
        val registryB = GuestApplicationSessionRegistry(File(baseB, "sessions.json")); val executorB = Executor()
        val executorA = Executor(); assertEquals(GuestApplicationSessionState.RUNNING, GuestApplicationSessionController(a.registry).start(a.request, a.expected, executorA).state)
        assertEquals(GuestApplicationSessionState.RUNNING, GuestApplicationSessionController(registryB).start(requestB, expectedB, executorB).state)
        GuestApplicationSessionController(a.registry).stop(a.request.runId, "stop-a")
        assertEquals(1, executorA.closes.get()); assertEquals(0, executorB.closes.get())
    }
}
