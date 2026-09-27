package com.example.appsandbox.runtime

import com.example.appsandbox.contract.GuestAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

class GuestRuntimeEngineTest {
    @Test fun openCreatesOpaqueTokenAndRejectsInstanceIdAsToken() = withRepository { repo ->
        val engine = engine(repo)
        val opened = engine.opened(ID_A, REV_A)

        assertNotEquals(ID_A, opened.sessionToken)
        assertEquals(0, opened.counter)
        engine.expectFailure(GuestRuntimeError.INVALID_IDENTITY) { readState(ID_A) }
        assertEquals(0, engine.state(engine.readState(opened.sessionToken)).counter)
    }

    @Test fun deletedInstanceInvalidatesExistingSession() = withRepository { repo ->
        val engine = engine(repo)
        val token = engine.opened(ID_A, REV_A).sessionToken

        repo.instances.remove(ID_A)

        engine.expectFailure(GuestRuntimeError.DELETED_INSTANCE) { readState(token) }
        engine.expectFailure(GuestRuntimeError.DELETED_INSTANCE) { execute(token, GuestAction.INCREMENT) }
    }

    @Test fun revisionAndArtifactChangesFailClosedAfterOpen() = withRepository { repo ->
        val engine = engine(repo)
        val revisionToken = engine.opened(ID_A, REV_A).sessionToken
        repo.instances[ID_A] = repo.instances.getValue(ID_A).copy(revisionId = REV_B)

        engine.expectFailure(GuestRuntimeError.REVISION_MISMATCH) { readState(revisionToken) }

        repo.instances[ID_A] = repo.instances.getValue(ID_A).copy(revisionId = REV_A, artifactValid = false)

        engine.expectFailure(GuestRuntimeError.ARTIFACT_MISMATCH) { execute(revisionToken, GuestAction.INCREMENT) }
    }

    @Test fun unsupportedActionIsRejectedByAllowedActionSet() = withRepository { repo ->
        repo.instances[ID_A] = repo.instances.getValue(ID_A).copy(allowed = setOf(GuestAction.INCREMENT))
        val engine = engine(repo)
        val token = engine.opened(ID_A, REV_A).sessionToken

        assertEquals(1, engine.state(engine.execute(token, GuestAction.INCREMENT)).counter)
        engine.expectFailure(GuestRuntimeError.UNSUPPORTED_ACTION) { execute(token, GuestAction.RESET) }
    }

    @Test fun separateInstancesKeepIndependentSessions() = withRepository { repo ->
        val engine = engine(repo)
        val tokenA = engine.opened(ID_A, REV_A).sessionToken
        val tokenB = engine.opened(ID_B, REV_B).sessionToken

        repeat(3) { engine.state(engine.execute(tokenA, GuestAction.INCREMENT)) }
        engine.state(engine.execute(tokenB, GuestAction.TOGGLE))

        assertEquals(3, engine.state(engine.readState(tokenA)).counter)
        assertEquals(1, engine.state(engine.readState(tokenB)).counter)
    }

    @Test fun sameInstanceConcurrentIncrementsRemainSerializedByStateStore() = withRepository { repo ->
        val engine = engine(repo)
        val tokenA = engine.opened(ID_A, REV_A).sessionToken
        val tokenB = engine.opened(ID_A, REV_A).sessionToken
        val workers = 6
        val iterations = 30
        val gate = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val futures = (0 until workers).map { worker ->
                pool.submit {
                    gate.await()
                    val token = if (worker % 2 == 0) tokenA else tokenB
                    repeat(iterations) {
                        if (engine.execute(token, GuestAction.INCREMENT) is GuestRuntimeReply.Failure) errors.incrementAndGet()
                    }
                }
            }
            gate.countDown()
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(0, errors.get())
        assertEquals(workers * iterations, engine.state(engine.readState(tokenA)).counter)
        assertTrue(File(repo.instances.getValue(ID_A).root, "files/counter.txt.bak").isFile)
    }

    @Test fun stateCorruptionMapsToStableRuntimeError() = withRepository { repo ->
        val engine = engine(repo)
        val token = engine.opened(ID_A, REV_A).sessionToken
        engine.state(engine.execute(token, GuestAction.INCREMENT))
        File(repo.instances.getValue(ID_A).root, "files/counter.txt").writeText("broken-primary")
        File(repo.instances.getValue(ID_A).root, "files/counter.txt.bak").writeText("broken-backup")

        engine.expectFailure(GuestRuntimeError.STATE_CORRUPT) { readState(token) }
    }

    @Test fun closeIsIdempotentAndRejectsOldToken() = withRepository { repo ->
        val engine = engine(repo)
        val token = engine.opened(ID_A, REV_A).sessionToken

        assertEquals(GuestRuntimeReply.Closed, engine.closeSession(token))
        assertEquals(GuestRuntimeReply.Closed, engine.closeSession(token))

        engine.expectFailure(GuestRuntimeError.INVALID_IDENTITY) { readState(token) }
        engine.expectFailure(GuestRuntimeError.INVALID_IDENTITY) { execute(token, GuestAction.INCREMENT) }
    }

    private fun engine(repository: FakeRepository) = GuestRuntimeEngine(repository, tokenFactory = object {
        private var next = 0
        operator fun invoke(): String = "token-${++next}"
    }::invoke)

    private fun GuestRuntimeEngine.opened(instanceId: String, revisionId: String): GuestRuntimeReply.Opened =
        openSession(instanceId, revisionId) as GuestRuntimeReply.Opened

    private fun GuestRuntimeEngine.state(reply: GuestRuntimeReply): GuestRuntimeReply.State =
        reply as GuestRuntimeReply.State

    private fun GuestRuntimeEngine.expectFailure(error: GuestRuntimeError, call: GuestRuntimeEngine.() -> GuestRuntimeReply) {
        val failure = call() as GuestRuntimeReply.Failure
        assertEquals(error, failure.error)
    }

    private fun withRepository(block: (FakeRepository) -> Unit) {
        val root = createTempDirectory("guest-runtime-engine").toFile()
        try {
            block(
                FakeRepository(
                    mutableMapOf(
                        ID_A to FakeInstance(ID_A, REV_A, File(root, "a"), setOf(GuestAction.INCREMENT, GuestAction.RESET, GuestAction.TOGGLE)),
                        ID_B to FakeInstance(ID_B, REV_B, File(root, "b"), setOf(GuestAction.INCREMENT, GuestAction.RESET, GuestAction.TOGGLE))
                    )
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    data class FakeInstance(
        val instanceId: String,
        val revisionId: String,
        val root: File,
        val allowed: Set<GuestAction>,
        val artifactValid: Boolean = true
    )

    class FakeRepository(val instances: MutableMap<String, FakeInstance>) : GuestRuntimeRepository {
        override fun resolve(instanceId: String, revisionId: String): GuestRuntimeResolvedSession {
            val instance = instances[instanceId]
                ?: throw GuestRuntimeException(GuestRuntimeError.DELETED_INSTANCE, "deleted instance")
            if (instance.revisionId != revisionId) {
                throw GuestRuntimeException(GuestRuntimeError.REVISION_MISMATCH, "revision mismatch")
            }
            if (!instance.artifactValid) {
                throw GuestRuntimeException(GuestRuntimeError.ARTIFACT_MISMATCH, "artifact mismatch")
            }
            return GuestRuntimeResolvedSession(instance.instanceId, instance.revisionId, instance.root, instance.allowed)
        }
    }

    private companion object {
        const val ID_A = "11111111-1111-4111-8111-111111111111"
        const val ID_B = "22222222-2222-4222-8222-222222222222"
        const val REV_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val REV_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    }
}
