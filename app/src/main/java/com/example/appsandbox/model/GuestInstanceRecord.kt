package com.example.appsandbox.model

import org.json.JSONObject

data class GuestInstanceRecord(
    val instanceId: String,
    val guestRevisionId: String,
    val guestPackageName: String,
    val guestApkPath: String,
    val guestSha256: String,
    val dataRoot: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toJson() = JSONObject().apply {
        put("instanceId", instanceId)
        put("guestRevisionId", guestRevisionId)
        put("guestPackageName", guestPackageName)
        put("guestApkPath", guestApkPath)
        put("guestSha256", guestSha256)
        put("dataRoot", dataRoot)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }
    companion object {
        fun fromJson(value: JSONObject) = GuestInstanceRecord(
            value.getString("instanceId"), value.getString("guestRevisionId"),
            value.getString("guestPackageName"), value.getString("guestApkPath"),
            value.getString("guestSha256"), value.getString("dataRoot"),
            value.getLong("createdAt"), value.getLong("updatedAt")
        )
    }
}
