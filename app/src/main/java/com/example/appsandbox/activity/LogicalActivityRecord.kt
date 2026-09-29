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
    fun validate() {
        val spec = GuestActivityLaunchPolicy.create(
            launchId, instanceId, revisionId, packageName, componentName
        )
        require(spec.componentName == componentName) { "non-canonical component" }
        require(Regex("[0-9a-fA-F]{64}").matches(sha256)) { "invalid SHA256" }
        require(resultMessage == null || resultMessage.length <= 1024) { "result message too large" }
        require(state != LogicalActivityState.OPEN || (resultCode == 0 && resultMessage == null)) {
            "open launch cannot contain a result"
        }
    }

    fun sameIdentity(other: LogicalActivityRecord): Boolean =
        instanceId == other.instanceId &&
            revisionId == other.revisionId &&
            packageName == other.packageName &&
            sha256 == other.sha256 &&
            componentName == other.componentName

    fun toJson() = JSONObject().apply {
        validate()
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
        fun fromJson(value: JSONObject): LogicalActivityRecord {
            val required = setOf(
                "launchId", "instanceId", "revisionId", "packageName",
                "sha256", "componentName", "state", "resultCode"
            )
            require(
                value.keys().asSequence().toSet().minus(required)
                    .all { it == "resultMessage" }
            )
            require(required.all(value::has))
            require((required - "resultCode").all { value.get(it) is String }) {
                "logical Activity identity fields must be strings"
            }
            require(value.get("resultCode") is Int) { "logical Activity result code must be an integer" }
            require(!value.has("resultMessage") || value.get("resultMessage") is String) {
                "logical Activity result message must be a string"
            }
            val record = LogicalActivityRecord(
                value.getString("launchId"),
                value.getString("instanceId"),
                value.getString("revisionId"),
                value.getString("packageName"),
                value.getString("sha256"),
                value.getString("componentName"),
                LogicalActivityState.valueOf(value.getString("state")),
                value.getInt("resultCode"),
                if (value.has("resultMessage")) value.getString("resultMessage") else null
            )
            record.validate()
            return record
        }
    }
}
