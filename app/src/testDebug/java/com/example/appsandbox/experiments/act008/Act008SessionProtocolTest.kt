package com.example.appsandbox.experiments.act008

import org.junit.Assert.*
import org.junit.Test

class Act008SessionProtocolTest {
    private fun request() = mapOf(
        Act008SessionProtocol.KEY_REQUEST_ID to "request", Act008SessionProtocol.KEY_OPERATION_ID to "operation",
        Act008SessionProtocol.KEY_RUN_ID to "run", Act008SessionProtocol.KEY_INSTANCE_ID to "instance"
    )

    @Test fun validatesVersionIdentityAndBounds() {
        assertNull(Act008SessionProtocol.validateFields(Act008SessionProtocol.MSG_START, Act008SessionProtocol.VERSION, request()))
        assertEquals(Act008Failure.INVALID_PROTOCOL, Act008SessionProtocol.validateFields(Act008SessionProtocol.MSG_START, 99, request()))
        assertEquals(Act008Failure.INVALID_REQUEST, Act008SessionProtocol.validateFields(Act008SessionProtocol.MSG_START, Act008SessionProtocol.VERSION, request() + (Act008SessionProtocol.KEY_RUN_ID to "")))
        assertEquals(Act008Failure.OVERSIZED_PAYLOAD, Act008SessionProtocol.validateFields(Act008SessionProtocol.MSG_START, Act008SessionProtocol.VERSION, request() + (Act008SessionProtocol.KEY_REQUEST_ID to "x".repeat(129))))
    }

    @Test fun snapshotCodecIsBoundedAndRejectsUnknownState() {
        assertEquals(512, Act008SessionProtocol.boundedDetail("x".repeat(600)).length)
        assertEquals(Act008SessionState.RUNNING, Act008SessionState.valueOf("RUNNING"))
        assertThrows(IllegalArgumentException::class.java) { Act008SessionState.valueOf("FUTURE") }
    }
}
