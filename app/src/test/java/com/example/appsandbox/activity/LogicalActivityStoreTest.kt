package com.example.appsandbox.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LogicalActivityStoreTest {
    private val instanceId = "11111111-1111-4111-8111-111111111111"
    private val revisionId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun stateSurvivesStoreRecreationAndCompletesOnlyMatchingLaunch() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val open = record("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            LogicalActivityStore(root).begin(open)

            assertEquals(open, LogicalActivityStore(root).current())
            expectStoreFailure("stale logical Activity result") {
                LogicalActivityStore(root).complete("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", 0, "stale")
            }

            val completed = LogicalActivityStore(root).complete(open.launchId, 1, "done")
            assertEquals(LogicalActivityState.CLOSED, completed.state)
            assertEquals(completed, LogicalActivityStore(root).current())
            assertEquals(completed, LogicalActivityStore(root).complete(open.launchId, 1, "ignored"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun onlyOneOpenLaunchMayOwnAnInstance() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val first = record("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            LogicalActivityStore(root).begin(first)

            expectStoreFailure("another logical Activity launch is active") {
                LogicalActivityStore(root).begin(record("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
            }
            assertEquals(first, LogicalActivityStore(root).current())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun malformedStateFailsClosedInsteadOfBecomingEmpty() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val state = File(File(root, "files"), "logical-activity.json")
            state.parentFile!!.mkdirs()
            state.writeText("{not-json")
            expectStoreFailure("logical Activity state is corrupt") {
                LogicalActivityStore(root).current()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingStateIsAnEmptyStore() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            assertNull(LogicalActivityStore(root).current())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sameLaunchResumesButIdentityMismatchAndClosedReopenFail() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val first = record("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            val store = LogicalActivityStore(root)
            assertEquals(first, store.begin(first))
            assertEquals(first, LogicalActivityStore(root).resumeOrBegin(first))

            expectStoreFailure("identity mismatch") {
                LogicalActivityStore(root).begin(first.copy(componentName = "com.example.guest.OtherActivity"))
            }
            store.complete(first.launchId, 1, "done")
            expectStoreFailure("closed logical Activity launch cannot reopen") {
                LogicalActivityStore(root).begin(first)
            }
            val next = record("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
            assertEquals(next, LogicalActivityStore(root).begin(next))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedCommitPreservesPreviousRecordAndLeavesNoTemp() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val first = record("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            LogicalActivityStore(root).begin(first)
            val failing = LogicalActivityStore(root, LogicalActivityCommit { _, _ -> error("injected commit failure") })
            expectStoreFailure("state commit failed") {
                failing.complete(first.launchId, 1, "should-not-commit")
            }
            assertEquals(first, LogicalActivityStore(root).current())
            assertFalse(File(root, "files/logical-activity.json.tmp").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptSchemaAndOversizedStateFailClosed() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val file = File(File(root, "files"), "logical-activity.json")
            file.parentFile!!.mkdirs()
            file.writeText("""{"schemaVersion":99,"record":{},"closedLaunchIds":[]}""")
            expectStoreFailure("corrupt") { LogicalActivityStore(root).current() }
            file.writeText("x".repeat(131073))
            expectStoreFailure("corrupt") { LogicalActivityStore(root).current() }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentDifferentLaunchesHaveOneWinner() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val ids = listOf(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
            )
            val futures = ids.map { id ->
                pool.submit {
                    gate.await()
                    runCatching { LogicalActivityStore(root).begin(record(id)) }
                }
            }
            gate.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.isFailure })
        } finally {
            pool.shutdownNow()
            root.deleteRecursively()
        }
    }

    private fun record(launchId: String) = LogicalActivityRecord(
        launchId = launchId,
        instanceId = instanceId,
        revisionId = revisionId,
        packageName = "com.example.guest",
        sha256 = "a".repeat(64),
        componentName = "com.example.guest.GuestMainActivity"
    )

    private fun expectStoreFailure(message: String, action: () -> Unit) {
        try {
            action()
            fail("expected logical Activity store failure")
        } catch (error: LogicalActivityStoreException) {
            check(error.message?.contains(message) == true) {
                "unexpected error: ${error.message}"
            }
        }
    }
}
