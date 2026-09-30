package com.example.appsandbox.identity

import android.content.Context
import android.os.Process

data class RuntimeIdentity(
    val hostPackageName: String,
    val hostUid: Int,
    val guestPackageName: String,
    val virtualUid: String,
    val instanceId: String,
    val processSlot: Int
) {
    init {
        require(hostPackageName.isNotBlank())
        require(guestPackageName.isNotBlank())
        require(instanceId.isNotBlank())
        require(processSlot >= 0)
    }

    companion object {
        fun create(context: Context, guestPackageName: String, instanceId: String, processSlot: Int) = RuntimeIdentity(
            hostPackageName = context.packageName,
            hostUid = Process.myUid(),
            guestPackageName = guestPackageName,
            virtualUid = "$guestPackageName:$instanceId",
            instanceId = instanceId,
            processSlot = processSlot
        )
    }
}
