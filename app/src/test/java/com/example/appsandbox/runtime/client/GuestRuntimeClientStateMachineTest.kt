package com.example.appsandbox.runtime.client

import com.example.appsandbox.runtime.GuestRuntimeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuestRuntimeClientStateMachineTest {
    @Test fun binderDeathPerformsBoundedReconnectsThenFailsClosed() {
        val machine = GuestRuntimeClientStateMachine(maxReconnects = 2)
        machine.start()
        machine.bound()
        machine.opened("session-1")

        machine.binderDied()
        assertEquals(GuestRuntimeClientState.BINDING, machine.state)
        assertNull(machine.sessionToken)
        machine.bound()
        machine.opened("session-2")

        machine.binderDied()
        assertEquals(GuestRuntimeClientState.BINDING, machine.state)
        machine.bound()
        machine.opened("session-3")

        machine.binderDied()
        assertEquals(GuestRuntimeClientState.FAILED, machine.state)
        assertEquals(GuestRuntimeError.RUNTIME_UNAVAILABLE, machine.failure)
    }

    @Test fun malformedRuntimeErrorMapsToFailClosedUnavailable() {
        val error = GuestRuntimeError.fromWireName("future-error")
        val machine = GuestRuntimeClientStateMachine()

        machine.start()
        machine.fail(error)

        assertEquals(GuestRuntimeClientState.FAILED, machine.state)
        assertEquals(GuestRuntimeError.RUNTIME_UNAVAILABLE, machine.failure)
    }

    @Test fun bindTimeoutFailsClosed() {
        val machine = GuestRuntimeClientStateMachine()

        machine.start()
        machine.bindTimedOut()

        assertEquals(GuestRuntimeClientState.FAILED, machine.state)
        assertEquals(GuestRuntimeError.RUNTIME_UNAVAILABLE, machine.failure)
    }

    @Test fun closeDropsSessionAndIgnoresFurtherStarts() {
        val machine = GuestRuntimeClientStateMachine()
        machine.start()
        machine.bound()
        machine.opened("session")

        machine.close()
        machine.start()

        assertEquals(GuestRuntimeClientState.CLOSED, machine.state)
        assertNull(machine.sessionToken)
    }
}
