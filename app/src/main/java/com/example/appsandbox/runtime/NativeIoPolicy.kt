package com.example.appsandbox.runtime

import java.io.File

/** Central policy model for the native path interception boundary. */
class NativeIoPolicy(
    private val guestPackage: String,
    private val instanceRoot: File
) {
    enum class Decision { PASSTHROUGH, VIRTUAL_INSTANCE_PATH, DENY }
    data class Result(val decision: Decision, val logical: String, val physical: String? = null)

    fun map(path: String): Result {
        val prefixData = "/data/data/$guestPackage"
        val prefixUser = "/data/user/0/$guestPackage"
        val logicalPrefix = when {
            path == prefixData || path.startsWith("$prefixData/") -> prefixData
            path == prefixUser || path.startsWith("$prefixUser/") -> prefixUser
            else -> return Result(Decision.PASSTHROUGH, path, path)
        }
        val suffix = path.removePrefix(logicalPrefix).trimStart('/')
        val physical = File(instanceRoot, suffix)
        val root = instanceRoot.canonicalFile
        val canonical = runCatching { physical.canonicalFile }.getOrNull()
            ?: return Result(Decision.DENY, path)
        if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) {
            return Result(Decision.DENY, path)
        }
        return Result(Decision.VIRTUAL_INSTANCE_PATH, path, canonical.path)
    }
}
