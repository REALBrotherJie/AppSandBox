package com.example.appsandbox.runtime

enum class GuestRuntimeError(val wireName: String) {
    INVALID_IDENTITY("invalid_identity"),
    DELETED_INSTANCE("deleted_instance"),
    REVISION_MISMATCH("revision_mismatch"),
    ARTIFACT_MISMATCH("artifact_mismatch"),
    UNSUPPORTED_ACTION("unsupported_action"),
    STATE_CORRUPT("state_corrupt"),
    RUNTIME_UNAVAILABLE("runtime_unavailable");

    companion object {
        fun fromWireName(value: String?): GuestRuntimeError =
            entries.firstOrNull { it.wireName == value } ?: RUNTIME_UNAVAILABLE
    }
}

class GuestRuntimeException(val error: GuestRuntimeError, message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
