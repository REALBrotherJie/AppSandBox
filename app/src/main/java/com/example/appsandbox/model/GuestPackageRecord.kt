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
    val componentSummary: ComponentSummary
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
}
