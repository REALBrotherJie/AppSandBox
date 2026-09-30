package com.example.appsandbox.runtime

object GuestRuntimeProtocol {
    const val MSG_ALLOCATE = 1
    const val MSG_QUERY = 2
    const val MSG_RELEASE = 3
    const val MSG_REPLY = 100
    const val KEY_INSTANCE_ID = "instanceId"
    const val KEY_PREFERRED_SLOT = "preferredSlot"
    const val KEY_SLOT = "slot"
    const val KEY_OK = "ok"
    const val KEY_MESSAGE = "message"
}
