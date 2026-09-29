package com.example.appsandbox.activity

import org.json.JSONObject

enum class LogicalActivityState { OPEN, CLOSED }

data class LogicalActivityRecord(
    val launchId: String,
    val instanceId: String,
    val revisionId: String,
    val packageName: String,
    val sha256: String,
    val componentName: String,
    val state: LogicalActivityState = LogicalActivityState.OPEN,
    val resultCode: Int = 0,
    val resultMessage: String? = null
) {
    fun toJson() = JSONObject().apply {
        put("launchId", launchId)
        put("instanceId", instanceId)
        put("revisionId", revisionId)
        put("packageName", packageName)
        put("sha256", sha256)
        put("componentName", componentName)
        put("state", state.name)
        put("resultCode", resultCode)
        resultMessage?.let { put("resultMessage", it) }
    }

    companion object {
        fun fromJson(value: JSONObject) = LogicalActivityRecord(
            value.getString("launchId"),
            value.getString("instanceId"),
            value.getString("revisionId"),
            value.getString("packageName"),
            value.getString("sha256"),
            value.getString("componentName"),
            LogicalActivityState.valueOf(value.getString("state")),
            value.optInt("resultCode", 0),
            value.optString("resultMessage").takeIf { it.isNotBlank() }
        )
    }
}
