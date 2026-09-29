package com.example.appsandbox.experiments.act008

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test

class Act008SessionProtocolTest {
    private fun request() = Bundle().apply {
        putInt(Act008SessionProtocol.KEY_VERSION, Act008SessionProtocol.VERSION)
        putString(Act008SessionProtocol.KEY_REQUEST_ID, "request")
        putString(Act008SessionProtocol.KEY_OPERATION_ID, "operation")
        putString(Act008SessionProtocol.KEY_RUN_ID, "run")
        putString(Act008SessionProtocol.KEY_INSTANCE_ID, "instance")
    }

    @Test fun validatesVersionIdentityAndBounds() {
        assertNull(Act008SessionProtocol.validateRequest(Act008SessionProtocol.MSG_START, request()))
        assertEquals(Act008Failure.INVALID_PROTOCOL, Act008SessionProtocol.validateRequest(Act008SessionProtocol.MSG_START, request().apply { putInt(Act008SessionProtocol.KEY_VERSION, 99) }))
        assertEquals(Act008Failure.INVALID_REQUEST, Act008SessionProtocol.validateRequest(Act008SessionProtocol.MSG_START, request().apply { putString(Act008SessionProtocol.KEY_RUN_ID, "") }))
        assertEquals(Act008Failure.OVERSIZED_PAYLOAD, Act008SessionProtocol.validateRequest(Act008SessionProtocol.MSG_START, request().apply { putString(Act008SessionProtocol.KEY_REQUEST_ID, "x".repeat(129)) }))
    }

    @Test fun snapshotCodecIsBoundedAndRejectsUnknownState() {
        val snapshot = Act008SessionSnapshot("r", "o", "run", "i", Act008SessionState.RUNNING, detail = "x".repeat(600), ownerPid = 42, updatedAt = 7)
        val decoded = requireNotNull(Act008SessionProtocol.decode(Act008SessionProtocol.encode(snapshot)))
        assertEquals(512, decoded.detail.length)
        assertEquals(42, decoded.ownerPid)
        assertNull(Act008SessionProtocol.decode(Act008SessionProtocol.encode(snapshot).apply { putString(Act008SessionProtocol.KEY_STATE, "FUTURE") }))
    }
}
