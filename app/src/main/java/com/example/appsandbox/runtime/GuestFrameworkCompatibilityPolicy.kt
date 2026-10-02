package com.example.appsandbox.runtime

/** Guest-scoped translations required only at Host framework boundaries. */
object GuestFrameworkCompatibilityPolicy {
    const val RECEIVER_EXPORTED = 0x2
    const val RECEIVER_NOT_EXPORTED = 0x4
    private const val RECEIVER_EXPLICIT_MASK = RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED

    fun receiverFlags(
        guestCall: Boolean,
        deviceApi: Int,
        guestTargetSdk: Int,
        originalFlags: Int,
        hasReceiver: Boolean,
        protectedBroadcastOnly: Boolean
    ): Int {
        if (!guestCall) return originalFlags
        if (deviceApi < 33 || guestTargetSdk >= 33) return originalFlags
        if (originalFlags and RECEIVER_EXPLICIT_MASK != 0) return originalFlags
        if (!hasReceiver || protectedBroadcastOnly) return originalFlags
        // Pre-33 dynamic receivers were externally addressable unless the call was
        // a sticky query or exclusively covered protected system broadcasts.
        return originalFlags or RECEIVER_EXPORTED
    }
}
