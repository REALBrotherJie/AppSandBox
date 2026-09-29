package com.example.appsandbox.workspace

import com.example.appsandbox.activity.LogicalActivityRecord
import com.example.appsandbox.activity.LogicalActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestActivityUiStateTest {
    private val instance = "11111111-1111-1111-1111-111111111111"
    private val revision = "22222222-2222-2222-2222-222222222222"
    private val launch = "33333333-3333-3333-3333-333333333333"
    private val sha = "a".repeat(64)
    private val record = LogicalActivityRecord(
        launch, instance, revision, "com.example.guest", sha,
        "com.example.guest.runtime.GuestScreenActivity"
    )

    @Test
    fun openSameComponentResumesLaunchAndClosedLaunchGetsNewOne() {
        assertEquals(launch, GuestActivityUiState.resumedLaunch(record, record.componentName))
        val closed = record.copy(state = LogicalActivityState.CLOSED, resultCode = 0, resultMessage = "back")
        assertNull(GuestActivityUiState.resumedLaunch(closed, record.componentName))
    }

    @Test
    fun openDifferentComponentFailsClosed() {
        runCatching {
            GuestActivityUiState.resumedLaunch(record, "com.example.guest.OtherActivity")
        }.onSuccess { error("different component was accepted") }
    }

    @Test
    fun resultRequiresExactLaunchAndBoundIdentity() {
        val closed = record.copy(state = LogicalActivityState.CLOSED, resultCode = 0, resultMessage = "back")
        assertTrue(GuestActivityUiState.boundTo(closed, instance, revision, record.packageName, sha))
        assertTrue(GuestActivityUiState.acceptsResult(closed, launch, launch, instance, instance, revision, revision, 0))
        assertFalse(GuestActivityUiState.acceptsResult(closed, launch, "44444444-4444-4444-4444-444444444444", instance, instance, revision, revision, 0))
        assertFalse(GuestActivityUiState.acceptsResult(closed, launch, launch, instance, "44444444-4444-4444-4444-444444444444", revision, revision, 0))
        assertFalse(GuestActivityUiState.acceptsResult(closed, launch, launch, instance, instance, revision, "44444444-4444-4444-4444-444444444444", 0))
        assertFalse(GuestActivityUiState.boundTo(closed, instance, revision, record.packageName, "b".repeat(64)))
        assertFalse(GuestActivityUiState.acceptsResult(record, launch, launch, instance, instance, revision, revision, 0))
        assertFalse(GuestActivityUiState.acceptsResult(closed, launch, launch, instance, instance, revision, revision, -1))
        assertFalse(GuestActivityUiState.acceptsResult(closed, null, launch, instance, instance, revision, revision, 0))
    }

    @Test
    fun persistedClosedResultIsSelectedWithoutAnInMemoryPendingLaunch() {
        val closed = record.copy(state = LogicalActivityState.CLOSED, resultCode = -1, resultMessage = "completed")
        assertEquals(closed, GuestActivityUiState.completedResult(closed, instance, revision, record.packageName, sha))
        assertNull(GuestActivityUiState.completedResult(record, instance, revision, record.packageName, sha))
        assertNull(GuestActivityUiState.completedResult(null, instance, revision, record.packageName, sha))
    }

    @Test
    fun persistedResultForAnotherBindingFailsClosed() {
        val closed = record.copy(state = LogicalActivityState.CLOSED, resultCode = -1, resultMessage = "completed")
        listOf(
            closed.copy(instanceId = "44444444-4444-4444-4444-444444444444"),
            closed.copy(revisionId = "44444444-4444-4444-4444-444444444444"),
            closed.copy(packageName = "com.example.other"),
            closed.copy(sha256 = "b".repeat(64))
        ).forEach { mismatched ->
            assertTrue(runCatching {
                GuestActivityUiState.completedResult(mismatched, instance, revision, record.packageName, sha)
            }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
