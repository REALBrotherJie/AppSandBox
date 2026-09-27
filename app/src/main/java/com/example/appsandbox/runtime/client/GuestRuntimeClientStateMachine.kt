package com.example.appsandbox.runtime.client

import com.example.appsandbox.runtime.GuestRuntimeError

enum class GuestRuntimeClientState { IDLE, BINDING, OPENING, READY, FAILED, CLOSED }

class GuestRuntimeClientStateMachine(private val maxReconnects: Int = 2) {
    var state: GuestRuntimeClientState = GuestRuntimeClientState.IDLE
        private set
    var reconnects: Int = 0
        private set
    var sessionToken: String? = null
        private set
    var failure: GuestRuntimeError? = null
        private set

    fun start() {
        if (state == GuestRuntimeClientState.CLOSED) return
        state = GuestRuntimeClientState.BINDING
        failure = null
    }

    fun bound() {
        if (state == GuestRuntimeClientState.BINDING) state = GuestRuntimeClientState.OPENING
    }

    fun opened(token: String) {
        if (state == GuestRuntimeClientState.OPENING && token.isNotBlank()) {
            sessionToken = token
            state = GuestRuntimeClientState.READY
            failure = null
        } else {
            fail(GuestRuntimeError.RUNTIME_UNAVAILABLE)
        }
    }

    fun binderDied() {
        sessionToken = null
        if (state == GuestRuntimeClientState.CLOSED) return
        if (reconnects < maxReconnects) {
            reconnects += 1
            state = GuestRuntimeClientState.BINDING
        } else {
            fail(GuestRuntimeError.RUNTIME_UNAVAILABLE)
        }
    }

    fun bindTimedOut() {
        if (state == GuestRuntimeClientState.BINDING || state == GuestRuntimeClientState.OPENING) {
            fail(GuestRuntimeError.RUNTIME_UNAVAILABLE)
        }
    }

    fun fail(error: GuestRuntimeError) {
        sessionToken = null
        failure = error
        state = GuestRuntimeClientState.FAILED
    }

    fun close() {
        sessionToken = null
        state = GuestRuntimeClientState.CLOSED
    }
}
