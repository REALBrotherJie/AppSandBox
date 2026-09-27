package com.example.appsandbox.experiments.act006.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class Act006CoreTest {
    private val input = Act006InputSnapshot("launch-1", "op-1", "i-1", "rev-1", "a".repeat(64), "GuestActivity", "HostStub", "api31|fp")
    private val expected = Act006Expected("i-1", "rev-1", "a".repeat(64), "GuestActivity", "HostStub", "api31|fp")
    private class Executor(private val failConstruct: Boolean = false, private val failAttach: Boolean = false) : Act006AttachExecutor {
        val constructed = AtomicInteger(); val attached = AtomicInteger()
        override fun construct(input: Act006InputSnapshot): Any { constructed.incrementAndGet(); if (failConstruct) error("construct"); return Any() }
        override fun attach(instance: Any, input: Act006InputSnapshot) { attached.incrementAndGet(); if (failAttach) error("attach") }
    }
    private fun machine(run: String = "run-1", executor: Act006AttachExecutor = Executor(), guard: Act006ProcessGuard = Act006ProcessGuard()) = Act006StateMachine(run, executor, guard)

    @Test fun allValidationFailuresAvoidExecutor() {
        val cases = listOf(input.copy(instanceId = "bad"), input.copy(revisionId = "bad"), input.copy(artifactSha256 = "bad"),
            input.copy(guestClass = "bad"), input.copy(hostCarrier = "bad"), input.copy(apiAdapterFingerprint = "bad"))
        cases.forEach { bad -> val e = Executor(); assertEquals(Act006Phase.REJECTED, machine(executor = e).execute(bad, expected).phase); assertEquals(0, e.constructed.get()); assertEquals(0, e.attached.get()) }
        val e = Executor(); assertEquals(Act006Reason.NON_ACTIVITY, machine(executor = e).execute(input, expected.copy(activityClass = false)).reason); assertEquals(0, e.constructed.get())
    }

    @Test fun constructorFailureRecordsAttemptAndRemainsRetryable() {
        val guard = Act006ProcessGuard(); val failed = machine(executor = Executor(failConstruct = true), guard = guard).execute(input, expected)
        assertEquals(Act006Reason.CONSTRUCTOR_FAILED, failed.reason); assertEquals(Act006Counters(constructorAttempted = 1), failed.counters); assertFalse(failed.irreversible)
        assertEquals(Act006Phase.ATTACHED, machine(run = "run-2", guard = guard).execute(input.copy(operationId = "op-2", launchId = "launch-2"), expected).phase)
    }

    @Test fun attachFailureRecordsAttemptAndRequiresActualNewProcessGuard() {
        val guard = Act006ProcessGuard(); val sm = machine(executor = Executor(failAttach = true), guard = guard); val failed = sm.execute(input, expected)
        assertEquals(Act006Phase.PROCESS_RECOVERY_REQUIRED, failed.phase); assertEquals(Act006Counters(1, 1, 1, 0, 0, 0), failed.counters)
        assertEquals(Act006Reason.PROCESS_RECOVERY, sm.recover("run-2").execute(input.copy(operationId = "op-2", launchId = "launch-2"), expected).reason)
        assertEquals(Act006Phase.ATTACHED, machine(run = "run-3", guard = Act006ProcessGuard()).execute(input.copy(operationId = "op-3", launchId = "launch-3"), expected).phase)
    }

    @Test fun successIsAttachedWithoutLifecycleAndBlocksSecondAttach() {
        val guard = Act006ProcessGuard(); val result = machine(guard = guard).execute(input, expected)
        assertTrue(result.attachedNoLifecycle); assertEquals(Act006Counters(1, 1, 1, 1, 0, 0), result.counters)
        assertEquals(Act006Reason.PROCESS_RECOVERY, machine(run = "run-2", guard = guard).execute(input.copy(operationId = "op-2", launchId = "launch-2"), expected).reason)
    }

    @Test fun replayRevalidatesCurrentIdentityBeforeReturningCachedSuccess() {
        val guard = Act006ProcessGuard(); val sm = machine(guard = guard); val first = sm.execute(input, expected)
        assertSame(first, sm.execute(input, expected))
        assertEquals(Act006Reason.STALE_REVISION, sm.execute(input, expected.copy(revisionId = "new-revision")).reason)
        assertEquals(Act006Reason.DUPLICATE_OPERATION, sm.execute(input.copy(artifactSha256 = "b".repeat(64)), expected.copy(artifactSha256 = "b".repeat(64))).reason)
    }

    @Test fun multipleMachinesCompeteForOneProcessIrreversibleBoundary() {
        val guard = Act006ProcessGuard(); val pool = Executors.newFixedThreadPool(8); val gate = CountDownLatch(1); val calls = AtomicInteger()
        val jobs = (1..8).map { n -> pool.submit<Act006Result> { gate.await(); machine(run = "run-$n", executor = object : Act006AttachExecutor {
            override fun construct(input: Act006InputSnapshot): Any { calls.incrementAndGet(); return Any() }
            override fun attach(instance: Any, input: Act006InputSnapshot) = Unit
        }, guard = guard).execute(input.copy(operationId = "op-$n", launchId = "launch-$n"), expected) } }
        gate.countDown(); val results = jobs.map { it.get() }; pool.shutdown()
        assertEquals(1, results.count { it.phase == Act006Phase.ATTACHED }); assertEquals(1, calls.get()); assertEquals(7, results.count { it.reason == Act006Reason.PROCESS_RECOVERY })
    }

    @Test fun constructorAndAttachReentryFailClosed() {
        fun run(reenterOnAttach: Boolean): Act006Reason {
            val guard = Act006ProcessGuard(); lateinit var sm: Act006StateMachine; var nested: Act006Result? = null
            sm = machine(guard = guard, executor = object : Act006AttachExecutor {
                override fun construct(input: Act006InputSnapshot): Any { if (!reenterOnAttach) nested = sm.execute(input.copy(operationId = "nested", launchId = "nested"), expected); return Any() }
                override fun attach(instance: Any, input: Act006InputSnapshot) { if (reenterOnAttach) nested = sm.execute(input.copy(operationId = "nested", launchId = "nested"), expected) }
            })
            sm.execute(input, expected); return nested!!.reason
        }
        assertEquals(Act006Reason.EXECUTION_IN_PROGRESS, run(false)); assertEquals(Act006Reason.PROCESS_RECOVERY, run(true))
    }

    @Test fun duplicateLaunchFailsClosed() {
        val guard = Act006ProcessGuard(); machine(executor = Executor(failConstruct = true), guard = guard).execute(input, expected)
        assertEquals(Act006Reason.DUPLICATE_LAUNCH, machine(run = "run-2", guard = guard).execute(input.copy(operationId = "op-2"), expected).reason)
    }

    @Test fun codecRejectsTypesRangesUnknownsAndSemanticForgeries() {
        val encoded = Act006ResultCodec.encode(machine().execute(input, expected)); assertTrue(Act006ResultCodec.decode(encoded).attachedNoLifecycle)
        val corrupt = listOf(
            encoded.replace("\"runId\":\"run-1\"", "\"runId\":\"\""),
            encoded.replace("\"attachCompleted\":1", "\"attachCompleted\":2"),
            encoded.replace("\"attachCompleted\":1", "\"attachCompleted\":\"1\""),
            encoded.replace("ATTACHED", "UNKNOWN"),
            encoded.replace("RECEIVED,VALIDATED,OBJECT_CONSTRUCTED,ATTACH_PENDING,ATTACHED", "RECEIVED,ATTACHED"),
            encoded.replace("\"lifecycleAttempted\":0", "\"lifecycleAttempted\":1"),
            encoded.replace("\"reason\":\"NONE\"", "\"reason\":\"ATTACH_FAILED\"")
        )
        corrupt.forEach { assertThrows(IllegalArgumentException::class.java) { Act006ResultCodec.decode(it) } }
    }

    @Test fun codecRejectsImpossibleRejectedPaths() {
        val validation = machine().execute(input.copy(instanceId = "stale"), expected)
        val validationJson = Act006ResultCodec.encode(validation)
        val constructor = machine(executor = Executor(failConstruct = true)).execute(input, expected)
        val constructorJson = Act006ResultCodec.encode(constructor)
        assertEquals(validation, Act006ResultCodec.decode(validationJson))
        assertEquals(constructor.copy(error = null), Act006ResultCodec.decode(constructorJson))
        val forgeries = listOf(
            validationJson.replace("RECEIVED,REJECTED", "RECEIVED,VALIDATED,REJECTED"),
            constructorJson.replace("RECEIVED,VALIDATED,REJECTED", "RECEIVED,REJECTED")
        )
        forgeries.forEach { assertThrows(IllegalArgumentException::class.java) { Act006ResultCodec.decode(it) } }
    }
}
