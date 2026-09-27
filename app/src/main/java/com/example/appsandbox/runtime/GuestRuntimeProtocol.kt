package com.example.appsandbox.runtime

object GuestRuntimeProtocol {
    const val MSG_OPEN = 1
    const val MSG_READ = 2
    const val MSG_EXECUTE = 3
    const val MSG_CLOSE = 4
    const val MSG_REPLY = 100

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_OK = "ok"
    const val KEY_ERROR = "error"
    const val KEY_MESSAGE = "message"
    const val KEY_INSTANCE_ID = "instanceId"
    const val KEY_REVISION_ID = "revisionId"
    const val KEY_SESSION_TOKEN = "sessionToken"
    const val KEY_ACTION = "action"
    const val KEY_COUNTER = "counter"
}
