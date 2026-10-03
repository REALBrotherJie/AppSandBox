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

    private const val DYNAMIC_RECEIVER_PERMISSION_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    /**
     * AndroidX emulates RECEIVER_NOT_EXPORTED before API 33 by protecting the receiver with the
     * package's own signature permission. The Guest's permission is not held by the Host UID that
     * sends the Guest's broadcasts, so the system drops every delivery; the Host's equivalent
     * permission keeps the same "own UID and system only" semantics.
     */
    fun receiverPermission(permission: String?, guestPackage: String, hostPackage: String): String? =
        if (permission == guestPackage + DYNAMIC_RECEIVER_PERMISSION_SUFFIX) hostPackage + DYNAMIC_RECEIVER_PERMISSION_SUFFIX
        else permission
}
