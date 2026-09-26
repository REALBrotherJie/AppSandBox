package com.example.appsandbox.contract

import com.example.appsandbox.contract.state.GuestStateStore
import java.io.File

class GuestViewSession(private val instanceRoot: File) {
    private val state = GuestStateStore(instanceRoot)

    fun counter(): Int = state.readCounter()

    fun execute(action: GuestAction): Int = state.updateCounter { current ->
        when (action) {
            GuestAction.INCREMENT -> (current + 1).coerceAtMost(999_999)
            GuestAction.RESET -> 0
            GuestAction.TOGGLE -> if (current == 0) 1 else 0
        }
    }
}
