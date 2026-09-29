package com.example.appsandbox.workspace

import com.example.appsandbox.activity.LogicalActivityRecord
import com.example.appsandbox.activity.LogicalActivityState

object GuestActivityUiState {
    fun boundTo(record: LogicalActivityRecord, instanceId: String, revisionId: String, packageName: String, sha256: String): Boolean =
        record.instanceId == instanceId && record.revisionId == revisionId &&
            record.packageName == packageName && record.sha256.equals(sha256, ignoreCase = true)

    fun resumedLaunch(record: LogicalActivityRecord?, componentName: String): String? {
        if (record == null || record.state == LogicalActivityState.CLOSED) return null
        require(record.componentName == componentName) { "Another logical Activity is open" }
        return record.launchId
    }

    fun acceptsResult(record: LogicalActivityRecord?, expectedLaunchId: String?, actualLaunchId: String?, resultCode: Int): Boolean =
        record != null && record.state == LogicalActivityState.CLOSED &&
            !expectedLaunchId.isNullOrBlank() && actualLaunchId == expectedLaunchId &&
            record.launchId == expectedLaunchId && record.resultCode == resultCode
}
