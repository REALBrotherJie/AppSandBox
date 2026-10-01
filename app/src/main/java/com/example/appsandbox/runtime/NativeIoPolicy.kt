package com.example.appsandbox.runtime

import java.io.File

/** Central policy model for the native path interception boundary. */
class NativeIoPolicy(
    private val guestPackage: String,
    private val instanceRoot: File,
    private val deviceProtectedRoot: File? = null
) {
    enum class Decision { PASSTHROUGH, VIRTUAL_INSTANCE_PATH, DENY }
    data class Result(val decision: Decision, val logical: String, val physical: String? = null)

    fun map(path: String): Result {
        val prefixes = listOf(
            "/data/data/$guestPackage" to instanceRoot,
            "/data/user/0/$guestPackage" to instanceRoot,
            "/data/user_de/0/$guestPackage" to (deviceProtectedRoot ?: instanceRoot)
        )
        val match = prefixes.firstOrNull { (prefix, _) ->
            path == prefix || path.startsWith("$prefix/")
        } ?: run {
            if (isUnder(instanceRoot) || deviceProtectedRoot?.let(::isUnder) == true) {
                return Result(Decision.PASSTHROUGH, path, path)
            }
            return Result(Decision.PASSTHROUGH, path, path)
        }
        val (logicalPrefix, targetRoot) = match
        val suffix = path.removePrefix(logicalPrefix).trimStart('/')
        val physical = File(targetRoot, suffix)
        val root = targetRoot.canonicalFile
        val canonical = runCatching { physical.canonicalFile }.getOrNull()
            ?: return Result(Decision.DENY, path)
        if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) {
            return Result(Decision.DENY, path)
        }
        return Result(Decision.VIRTUAL_INSTANCE_PATH, path, canonical.path)
    }

    private fun isUnder(root: File): Boolean {
        val canonical = runCatching { root.canonicalFile }.getOrNull() ?: return false
        return canonical.path == instanceRoot.canonicalPath || canonical.path.startsWith(instanceRoot.canonicalPath + File.separator)
    }
}
