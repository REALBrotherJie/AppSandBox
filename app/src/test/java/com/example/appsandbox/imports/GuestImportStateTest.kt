package com.example.appsandbox.imports

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuestImportStateTest {
    private val record = GuestPackageRecord("guest", "com.example.guest", "1", 1, "/base.apk", "Guest", 1, ComponentSummary(0, 0, 0, 0), "revision", "a".repeat(64), 10, 2)
    @Test fun emptyAndSelectingHaveNoRecord() {
        assertNull(GuestImportState(GuestImportPhase.EMPTY).record)
        assertEquals(GuestImportPhase.SELECTING, GuestImportState(GuestImportPhase.SELECTING).phase)
    }
    @Test fun unsupportedIsDistinctFromReadFailure() {
        assertEquals(GuestImportPhase.UNSUPPORTED, GuestImportState(GuestImportPhase.UNSUPPORTED, message = "Guest contract 不支持").phase)
        assertEquals(GuestImportPhase.FAILURE, GuestImportState(GuestImportPhase.FAILURE, message = "文件不可读").phase)
    }
    @Test fun failureDoesNotCarryRecord() {
        assertNull(GuestImportState(GuestImportPhase.FAILURE, message = "APK 无效").record)
    }
    @Test fun cancellationAfterSuccessRetainsPreviousGuest() {
        val session = GuestImportSession(record); session.selecting(); session.canceled()
        assertEquals(GuestImportPhase.SUCCESS, session.state.phase); assertEquals(record, session.state.record)
    }
    @Test fun unsupportedAfterSuccessRetainsPreviousGuestButReportsUnsupported() {
        val session = GuestImportSession(record); session.importing(); session.complete(GuestImportState(GuestImportPhase.UNSUPPORTED, message = "contract"))
        assertEquals(GuestImportPhase.UNSUPPORTED, session.state.phase); assertEquals(record, session.state.record)
    }
    @Test fun recreateRestoresLastValidRevision() {
        assertEquals(record, GuestImportSession(record).state.record)
        assertEquals(GuestImportPhase.SUCCESS, GuestImportSession(record).state.phase)
    }
}
