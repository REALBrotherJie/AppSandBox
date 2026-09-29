package com.example.appsandbox.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

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
                pool.submit(Callable {
                    gate.await()
                    runCatching { LogicalActivityStore(root).begin(record(id)) }
                })
            }
            gate.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.fold({ true }, { false }) })
            assertEquals(1, results.count { it.fold({ false }, { true }) })
        } finally {
            pool.shutdownNow()
            root.deleteRecursively()
        }
    }

    @Test
    fun threeHundredCompletionsSurviveRecreationAndRetainReplayProtection() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val records = (1L..300L).map { record(UUID(1L, it).toString()) }
            records.forEach { open ->
                assertEquals(open, LogicalActivityStore(root).begin(open))
                val closed = LogicalActivityStore(root).complete(open.launchId, -1, "done")
                assertEquals(closed, LogicalActivityStore(root).current())
                assertEquals(closed, LogicalActivityStore(root).complete(open.launchId, 0, "duplicate"))
            }
            val latest = LogicalActivityStore(root).current()
            for (old in listOf(records.first(), records[255], records.last())) {
                expectStoreFailure("cannot reopen") { LogicalActivityStore(root).begin(old) }
            }
            expectStoreFailure("stale") {
                LogicalActivityStore(root).complete(records.first().launchId, 0, "stale")
            }
            assertEquals(latest, LogicalActivityStore(root).current())
            val next = record(UUID(1L, 301L).toString())
            assertEquals(next, LogicalActivityStore(root).begin(next))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun fullHistoryRejectsBeforeOpeningAndKeepsCompletedResultReadable() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val ids = (1L..2048L).map { UUID(1L, it).toString() }
            val closed = record(ids.last()).copy(state = LogicalActivityState.CLOSED, resultMessage = "kept")
            val file = File(root, "files/logical-activity.json").apply { parentFile!!.mkdirs() }
            file.writeText(snapshot(closed, ids).toString())
            val before = file.readText()
            assertEquals(closed, LogicalActivityStore(root).current())
            assertEquals(closed, LogicalActivityStore(root).complete(closed.launchId, -1, "ignored"))
            expectStoreFailure("history capacity reached") {
                LogicalActivityStore(root).begin(record(UUID(1L, 2049L).toString()))
            }
            assertEquals(before, file.readText())
            assertEquals(closed, LogicalActivityStore(root).current())
            assertFalse(File(root, "files/logical-activity.json.tmp").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun closedTombstoneAndOpenTombstoneMustAgreeWithCurrentRecord() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val open = record(UUID(1L, 1L).toString())
            val closed = open.copy(state = LogicalActivityState.CLOSED)
            val file = File(root, "files/logical-activity.json").apply { parentFile!!.mkdirs() }
            for (invalid in listOf(snapshot(closed), snapshot(open, listOf(open.launchId)))) {
                file.writeText(invalid.toString())
                val before = file.readText()
                expectStoreFailure("corrupt") { LogicalActivityStore(root).current() }
                expectStoreFailure("corrupt") { LogicalActivityStore(root).begin(open) }
                expectStoreFailure("corrupt") { LogicalActivityStore(root).complete(open.launchId, 0, null) }
                assertEquals(before, file.readText())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun schemaAndRecordTypesAreNotCoerced() {
        val root = Files.createTempDirectory("logical-activity").toFile()
        try {
            val open = record(UUID(1L, 1L).toString())
            val file = File(root, "files/logical-activity.json").apply { parentFile!!.mkdirs() }
            val mutations: List<(JSONObject) -> Unit> = listOf(
                { it.put("schemaVersion", "1") },
                { it.put("schemaVersion", 1.5) },
                { it.put("schemaVersion", true) },
                { it.put("record", JSONObject.NULL) },
                { it.put("closedLaunchIds", JSONArray().put(42)) },
                { it.getJSONObject("record").put("resultCode", "0") },
                { it.getJSONObject("record").put("resultCode", 0.5) },
                { it.getJSONObject("record").put("resultCode", 4294967296L) },
                { it.getJSONObject("record").put("resultMessage", JSONObject.NULL) },
                { it.getJSONObject("record").put("packageName", 42) },
                { it.getJSONObject("record").put("launchId", true) },
                { it.getJSONObject("record").put("unknown", "field") },
                { it.getJSONObject("record").remove("resultCode") }
            )
            mutations.forEach { mutate ->
                val json = snapshot(open)
                mutate(json)
                file.writeText(json.toString())
                expectStoreFailure("corrupt") { LogicalActivityStore(root).current() }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingInstanceRootCannotBeCreatedByBegin() {
        val parent = Files.createTempDirectory("logical-parent").toFile()
        val root = File(parent, instanceId)
        try {
            expectStoreFailure("root unavailable") {
                LogicalActivityStore(root).begin(record(UUID(1L, 1L).toString()))
            }
            assertFalse(root.exists())
            root.mkdirs()
            val store = LogicalActivityStore(root)
            val open = record(UUID(1L, 2L).toString())
            store.begin(open)
            store.complete(open.launchId, 0, null)
            root.deleteRecursively()
            expectStoreFailure("root unavailable") {
                store.begin(record(UUID(1L, 3L).toString()))
            }
            assertFalse(root.exists())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun symbolicLinkLockCannotRedirectWritesWhenSupported() {
        val parent = Files.createTempDirectory("logical-lock-parent").toFile()
        val root = File(parent, instanceId).apply { mkdirs() }
        val external = File(parent, "external").apply { writeText("untouched") }
        val link = File(parent, ".logical-activity-$instanceId.lock")
        try {
            try { Files.createSymbolicLink(link.toPath(), external.toPath()) }
            catch (e: UnsupportedOperationException) { assumeNoException(e) }
            catch (e: java.nio.file.FileSystemException) { assumeNoException(e) }
            expectStoreFailure("lock path") {
                LogicalActivityStore(root).begin(record(UUID(1L, 1L).toString()))
            }
            assertEquals("untouched", external.readText())
            assertFalse(File(root, "files").exists())
        } finally {
            Files.deleteIfExists(link.toPath())
            parent.deleteRecursively()
        }
    }

    private fun snapshot(record: LogicalActivityRecord, ids: List<String> = emptyList()) =
        JSONObject().put("schemaVersion", 1).put("record", record.toJson())
            .put("closedLaunchIds", JSONArray(ids))

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
