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

    @Test fun validateFailureNeverCallsExecutor() {
        val e = Executor(); val r = Act006StateMachine("run-1", e).execute(input.copy(hostCarrier = "bad"), expected)
        assertEquals(Act006Phase.REJECTED, r.phase); assertEquals(Act006Reason.HOST_CARRIER_MISMATCH, r.reason); assertEquals(0, e.constructed.get()); assertEquals(0, e.attached.get())
    }

    @Test fun constructorFailureIsSafeRejectAndRetryable() {
        val e = Executor(true); val sm = Act006StateMachine("run-1", e); val r = sm.execute(input, expected)
        assertEquals(Act006Reason.CONSTRUCTOR_FAILED, r.reason); assertFalse(r.irreversible); assertFalse(r.phases.contains(Act006Phase.ATTACH_PENDING))
    }

    @Test fun attachFailureRequiresProcessRecoveryAndNoRollback() {
        val e = Executor(failAttach = true); val sm = Act006StateMachine("run-1", e); val r = sm.execute(input, expected)
        assertEquals(Act006Phase.PROCESS_RECOVERY_REQUIRED, r.phase); assertTrue(r.irreversible); assertEquals(1, e.attached.get())
        val next = sm.execute(input.copy(operationId = "op-2", launchId = "launch-2"), expected)
        assertEquals(Act006Reason.PROCESS_RECOVERY, next.reason)
    }

    @Test fun successfulAttachNeverInvokesLifecycle() {
        val e = Executor(); val r = Act006StateMachine("run-1", e).execute(input, expected)
        assertEquals(Act006Phase.ATTACHED, r.phase); assertTrue(r.attachedNoLifecycle); assertEquals(0, r.counters.lifecycle)
    }

    @Test fun duplicateOperationIsIdempotentButDifferentRequestFailsClosed() {
        val e = Executor(); val sm = Act006StateMachine("run-1", e); val first = sm.execute(input, expected); val repeat = sm.execute(input, expected)
        assertEquals(first, repeat); assertEquals(1, e.constructed.get()); assertEquals(1, e.attached.get())
        val changed = sm.execute(input.copy(artifactSha256 = "b".repeat(64)), expected)
        assertEquals(Act006Reason.DUPLICATE_OPERATION, changed.reason)
        val launch = sm.execute(input.copy(operationId = "op-2"), expected)
        assertEquals(Act006Reason.DUPLICATE_LAUNCH, launch.reason)
    }

    @Test fun concurrentStartsAllowOnlyOneIrreversibleRun() {
        val e = Executor(); val sm = Act006StateMachine("run-1", e); val pool = Executors.newFixedThreadPool(8); val gate = CountDownLatch(1)
        val results = (1..8).map { n -> pool.submit<Act006Result> { gate.await(); sm.execute(input.copy(operationId = "op-$n", launchId = "launch-$n"), expected) } }
        gate.countDown(); val all = results.map { it.get() }; pool.shutdown()
        assertEquals(1, all.count { it.phase == Act006Phase.ATTACHED }); assertEquals(1, e.constructed.get()); assertTrue(all.count { it.reason == Act006Reason.INVALID_TRANSITION || it.phase == Act006Phase.REJECTED } >= 7)
    }

    @Test fun recoveryCreatesFreshRunAndOldOperationIsNotSuccess() {
        val failing = Act006StateMachine("run-1", Executor(failAttach = true)); val old = failing.execute(input, expected)
        assertEquals(Act006Phase.PROCESS_RECOVERY_REQUIRED, old.phase)
        val fresh = Act006StateMachine("run-2", Executor()).execute(input.copy(operationId = "op-2", launchId = "launch-2"), expected)
        assertEquals("run-2", fresh.runId); assertEquals(Act006Phase.ATTACHED, fresh.phase)
    }

    @Test fun codecRejectsMissingUnknownAndInvalidTransition() {
        val result = Act006StateMachine("run-1", Executor()).execute(input, expected); val encoded = Act006ResultCodec.encode(result)
        assertEquals(result, Act006ResultCodec.decode(encoded))
        listOf(encoded.replace("\"reason\":\"NONE\",", ""), encoded.replace("ATTACHED", "UNKNOWN"), encoded.replace("RECEIVED,VALIDATED,OBJECT_CONSTRUCTED,ATTACH_PENDING,ATTACHED", "RECEIVED,ATTACHED")).forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { Act006ResultCodec.decode(raw) }
        }
    }
}
