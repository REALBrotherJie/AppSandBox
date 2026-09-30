package com.example.appsandbox.platform

import android.os.Build

data class PlatformProbe(
    val api: Int = Build.VERSION.SDK_INT,
    val supported: Boolean,
    val resolvedMembers: List<String> = emptyList(),
    val failureReason: String? = null
)

interface PlatformBridge {
    fun probe(): PlatformProbe
}

internal fun Throwable.rootCause(): Throwable {
    var current = this
    while (current.cause != null && current.cause !== current) current = current.cause!!
    return current
}
