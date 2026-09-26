package com.example.appsandbox.workspace

data class GuestWorkspaceLaunchSpec(
    val instanceId: String,
    val componentClassName: String,
    val documentUri: String,
    val flags: Int
)

object GuestWorkspaceLaunchPolicy {
    const val COMPONENT_CLASS = "com.example.appsandbox.GuestWorkspaceActivity"
    const val FLAG_NEW_DOCUMENT = 0x00080000
    const val FLAG_MULTIPLE_TASK = 0x08000000
    private const val URI_PREFIX = "appsandbox://workspace/"
    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")

    fun create(instanceId: String): GuestWorkspaceLaunchSpec {
        require(uuidPattern.matches(instanceId)) { "invalid workspace instanceId" }
        return GuestWorkspaceLaunchSpec(instanceId, COMPONENT_CLASS, URI_PREFIX + instanceId.lowercase(), FLAG_NEW_DOCUMENT)
    }

    fun validate(instanceId: String?, documentUri: String?): GuestWorkspaceLaunchSpec {
        val id = requireNotNull(instanceId) { "missing workspace instanceId" }
        val spec = create(id)
        require(documentUri == spec.documentUri) { "workspace intent identity mismatch" }
        return spec
    }
}
