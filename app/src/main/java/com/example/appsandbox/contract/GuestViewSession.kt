package com.example.appsandbox.contract

import java.io.File

class GuestViewSession(private val instanceRoot: File) {
    private val stateFile get() = File(instanceRoot, "files/counter.txt")

    fun counter(): Int = runCatching { stateFile.readText().trim().toInt() }.getOrDefault(0)

    fun execute(action: GuestAction): Int {
        val current = counter()
        val next = when (action) {
            GuestAction.INCREMENT -> (current + 1).coerceAtMost(999_999)
            GuestAction.RESET -> 0
            GuestAction.TOGGLE -> if (current == 0) 1 else 0
        }
        stateFile.parentFile!!.apply { check(isDirectory || mkdirs()) }
        stateFile.writeText(next.toString())
        return next
    }
}
