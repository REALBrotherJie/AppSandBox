package com.example.appsandbox.model

import org.json.JSONObject

data class ComponentSummary(
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val providerCount: Int
) {
    fun toJson() = JSONObject()
        .put("activityCount", activityCount)
        .put("serviceCount", serviceCount)
        .put("receiverCount", receiverCount)
        .put("providerCount", providerCount)
}

data class GuestPackageRecord(
    val internalGuestId: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val apkPath: String,
    val appLabel: String,
    val importedAt: Long,
    val componentSummary: ComponentSummary,
    val revisionId: String = internalGuestId,
    val sha256: String? = null,
    val fileSize: Long = -1L,
    val schemaVersion: Int = 1
) {
    fun toJson() = JSONObject()
        .put("internalGuestId", internalGuestId)
        .put("packageName", packageName)
        .put("versionName", versionName)
        .put("versionCode", versionCode)
        .put("apkPath", apkPath)
        .put("appLabel", appLabel)
        .put("importedAt", importedAt)
        .put("componentSummary", componentSummary.toJson())
        .put("revisionId", revisionId)
        .put("sha256", sha256)
        .put("fileSize", fileSize)
        .put("schemaVersion", schemaVersion)
}
