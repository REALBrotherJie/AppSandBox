package com.example.appsandbox.platform

import android.os.Handler
import com.example.appsandbox.hidden.HiddenApiAccess

class ActivityThreadBridge : PlatformBridge {
    data class Handles(val activityThread: Any, val handler: Handler)

    fun resolve(): Result<Handles> = HiddenApiAccess.probe().map { Handles(it.activityThread, it.mainHandler) }

    override fun probe(): PlatformProbe = resolve().fold(
        onSuccess = { PlatformProbe(supported = true, resolvedMembers = listOf("ActivityThread.currentActivityThread", "ActivityThread.mH")) },
        onFailure = { PlatformProbe(supported = false, failureReason = it.rootCause().let { root -> "${root.javaClass.name}: ${root.message}" }) }
    )
}
