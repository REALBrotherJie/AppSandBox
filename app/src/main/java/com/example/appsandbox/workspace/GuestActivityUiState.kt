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

    fun completedResult(
        record: LogicalActivityRecord?,
        instanceId: String,
        revisionId: String,
        packageName: String,
        sha256: String
    ): LogicalActivityRecord? {
        if (record == null) return null
        require(boundTo(record, instanceId, revisionId, packageName, sha256)) {
            "Persisted logical Activity identity mismatch"
        }
        return record.takeIf { it.state == LogicalActivityState.CLOSED }
    }

    fun acceptsResult(
        record: LogicalActivityRecord?,
        expectedLaunchId: String?,
        actualLaunchId: String?,
        expectedInstanceId: String?,
        actualInstanceId: String?,
        expectedRevisionId: String?,
        actualRevisionId: String?,
        resultCode: Int
    ): Boolean =
        record != null && record.state == LogicalActivityState.CLOSED &&
            !expectedLaunchId.isNullOrBlank() && actualLaunchId == expectedLaunchId &&
            record.launchId == expectedLaunchId && record.resultCode == resultCode &&
            actualInstanceId == expectedInstanceId && record.instanceId == expectedInstanceId &&
            actualRevisionId == expectedRevisionId && record.revisionId == expectedRevisionId
}
