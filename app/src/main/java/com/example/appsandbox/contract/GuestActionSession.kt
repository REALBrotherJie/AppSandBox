package com.example.appsandbox.contract

interface GuestActionSession {
    fun readCounter(callback: (Result<Int>) -> Unit)
    fun execute(action: GuestAction, callback: (Result<Int>) -> Unit)
    fun setStatusListener(listener: ((GuestActionSessionStatus) -> Unit)?) = Unit
    fun close()
}

enum class GuestActionSessionStatus { CONNECTING, READY, UNAVAILABLE }
