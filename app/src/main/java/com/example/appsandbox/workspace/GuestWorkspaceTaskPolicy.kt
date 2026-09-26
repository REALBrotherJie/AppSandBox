package com.example.appsandbox.workspace

object GuestWorkspaceTaskPolicy {
    fun recentsLabel(packageName: String, instanceId: String): String {
        require(packageName.isNotBlank()) { "missing guest package name" }
        val spec = GuestWorkspaceLaunchPolicy.create(instanceId)
        return "$packageName / ${spec.instanceId.take(8)}"
    }

    fun actionLabel(hasExistingTask: Boolean): String = if (hasExistingTask) "Focus" else "Open"
}
