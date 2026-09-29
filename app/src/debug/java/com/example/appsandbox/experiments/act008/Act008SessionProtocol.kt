package com.example.appsandbox.experiments.act008

import android.os.Bundle

enum class Act008Failure {
    NONE, INVALID_PROTOCOL, INVALID_REQUEST, OVERSIZED_PAYLOAD, DUPLICATE_REQUEST,
    STALE_RUN, CONCURRENT_OPERATION, RUNTIME_UNAVAILABLE, REGISTRY_CORRUPT,
    INSTANCE_UNAVAILABLE, BINDING_MISMATCH, CONSTRUCTION_FAILED, ON_CREATE_FAILED,
    CRASH_RECOVERY, DELETE_ACTIVE, INTERNAL
}

enum class Act008SessionState { NEW, STARTING, RUNNING, STOPPING, STOPPED, FAILED }

data class Act008SessionSnapshot(
    val requestId: String,
    val operationId: String,
    val runId: String,
    val instanceId: String,
    val state: Act008SessionState,
    val failure: Act008Failure = Act008Failure.NONE,
    val detail: String = "",
    val ownerPid: Int = -1,
    val updatedAt: Long = 0L
)

object Act008SessionProtocol {
    const val VERSION = 1
    const val MSG_START = 0x8001
    const val MSG_READ = 0x8002
    const val MSG_STOP = 0x8003
    const val MSG_RESTART = 0x8004
    const val MSG_DELETE = 0x8005
    const val MSG_TERMINATE_RUNTIME = 0x8006
    const val MSG_REPLY = 0x8100

    const val KEY_VERSION = "act008.version"
    const val KEY_REQUEST_ID = "act008.requestId"
    const val KEY_OPERATION_ID = "act008.operationId"
    const val KEY_RUN_ID = "act008.runId"
    const val KEY_INSTANCE_ID = "act008.instanceId"
    const val KEY_STATE = "act008.state"
    const val KEY_FAILURE = "act008.failure"
    const val KEY_DETAIL = "act008.detail"
    const val KEY_OWNER_PID = "act008.ownerPid"
    const val KEY_UPDATED_AT = "act008.updatedAt"
    const val KEY_OK = "act008.ok"

    const val MAX_ID_LENGTH = 128
    const val MAX_DETAIL_LENGTH = 512
    const val REQUEST_TIMEOUT_MS = 10_000L

    fun validateRequest(message: Int, data: Bundle): Act008Failure? {
        if (data.getInt(KEY_VERSION, -1) != VERSION) return Act008Failure.INVALID_PROTOCOL
        if (message !in setOf(MSG_START, MSG_READ, MSG_STOP, MSG_RESTART, MSG_DELETE, MSG_TERMINATE_RUNTIME)) return Act008Failure.INVALID_REQUEST
        val required = mutableListOf(KEY_REQUEST_ID)
        if (message != MSG_TERMINATE_RUNTIME) required += KEY_INSTANCE_ID
        if (message in setOf(MSG_START, MSG_STOP, MSG_RESTART)) required += KEY_OPERATION_ID
        if (message in setOf(MSG_START, MSG_STOP, MSG_RESTART)) required += KEY_RUN_ID
        for (key in required) {
            val value = data.getString(key).orEmpty()
            if (value.isBlank()) return Act008Failure.INVALID_REQUEST
            if (value.length > MAX_ID_LENGTH) return Act008Failure.OVERSIZED_PAYLOAD
        }
        return null
    }

    fun encode(snapshot: Act008SessionSnapshot) = Bundle().apply {
        putInt(KEY_VERSION, VERSION)
        putBoolean(KEY_OK, snapshot.failure == Act008Failure.NONE)
        putString(KEY_REQUEST_ID, snapshot.requestId.take(MAX_ID_LENGTH))
        putString(KEY_OPERATION_ID, snapshot.operationId.take(MAX_ID_LENGTH))
        putString(KEY_RUN_ID, snapshot.runId.take(MAX_ID_LENGTH))
        putString(KEY_INSTANCE_ID, snapshot.instanceId.take(MAX_ID_LENGTH))
        putString(KEY_STATE, snapshot.state.name)
        putString(KEY_FAILURE, snapshot.failure.name)
        putString(KEY_DETAIL, snapshot.detail.take(MAX_DETAIL_LENGTH))
        putInt(KEY_OWNER_PID, snapshot.ownerPid)
        putLong(KEY_UPDATED_AT, snapshot.updatedAt)
    }

    fun decode(data: Bundle): Act008SessionSnapshot? = runCatching {
        if (data.getInt(KEY_VERSION, -1) != VERSION) return null
        Act008SessionSnapshot(
            data.getString(KEY_REQUEST_ID).orEmpty(), data.getString(KEY_OPERATION_ID).orEmpty(),
            data.getString(KEY_RUN_ID).orEmpty(), data.getString(KEY_INSTANCE_ID).orEmpty(),
            Act008SessionState.valueOf(data.getString(KEY_STATE).orEmpty()),
            Act008Failure.valueOf(data.getString(KEY_FAILURE).orEmpty()),
            data.getString(KEY_DETAIL).orEmpty(), data.getInt(KEY_OWNER_PID, -1), data.getLong(KEY_UPDATED_AT)
        )
    }.getOrNull()
}
