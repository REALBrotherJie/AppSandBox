package com.example.appsandbox.model.resolver

import org.json.JSONObject

enum class GuestComponentType(val code: String) {
    ACTIVITY("activity"),
    SERVICE("service"),
    RECEIVER("receiver"),
    PROVIDER("provider");

    companion object {
        fun fromCode(code: String): GuestComponentType =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("unknown component type: $code")
    }
}

data class GuestComponent(
    val revisionId: String,
    val packageName: String,
    val className: String,
    val type: GuestComponentType,
    val enabled: Boolean,
    val exported: Boolean,
    val declaredPermissions: List<String> = emptyList(),
    val intentFilters: List<GuestIntentFilter> = emptyList()
) {
    init {
        require(revisionId.isNotBlank()) { "component revision is blank" }
        require(packageName.isNotBlank()) { "component package is blank" }
        require(className.isNotBlank()) { "component class is blank" }
        require(declaredPermissions.none { it.isBlank() }) { "component permission is blank" }
        require(intentFilters.all {
            it.revisionId == revisionId &&
                it.packageName == packageName &&
                it.componentClassName == className &&
                it.componentType == type
        }) { "component filter is not bound to component" }
        require(intentFilters.map { it.canonicalKey }.size == intentFilters.map { it.canonicalKey }.toSet().size) {
            "component has duplicate intent filters"
        }
    }

    fun toJson() = JSONObject()
        .put("revisionId", revisionId)
        .put("packageName", packageName)
        .put("className", className)
        .put("type", type.code)
        .put("enabled", enabled)
        .put("exported", exported)
        .put("declaredPermissions", org.json.JSONArray(declaredPermissions))
        .put("intentFilters", org.json.JSONArray(intentFilters.map { it.toJson() }))
}

enum class GuestComponentNameReason(val code: String) {
    INVALID_PACKAGE("invalid-package"),
    INVALID_CLASS_NAME("invalid-class-name"),
    PACKAGE_ESCAPE("package-escape"),
    NAME_TOO_LONG("name-too-long"),
    INVALID_PERMISSION("invalid-permission")
}

class GuestComponentNameException(
    val reason: GuestComponentNameReason,
    message: String = reason.code
) : IllegalArgumentException(message)

object GuestComponentNames {
    private const val MAX_NAME_LENGTH = 255
    private val identifier = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

    fun normalize(packageName: String, rawName: String): String {
        validatePackage(packageName)
        if (rawName.isBlank() || rawName.length > MAX_NAME_LENGTH ||
            rawName.any { it.isISOControl() || it.isWhitespace() } ||
            rawName.contains('/') || rawName.contains('\\')
        ) {
            throw GuestComponentNameException(
                if (rawName.length > MAX_NAME_LENGTH) GuestComponentNameReason.NAME_TOO_LONG
                else GuestComponentNameReason.INVALID_CLASS_NAME
            )
        }
        val candidate = when {
            rawName.startsWith('.') -> packageName + rawName
            rawName.contains('.') -> rawName
            else -> "$packageName.$rawName"
        }
        if (!candidate.startsWith("$packageName.")) {
            throw GuestComponentNameException(GuestComponentNameReason.PACKAGE_ESCAPE)
        }
        validateQualifiedName(candidate, GuestComponentNameReason.INVALID_CLASS_NAME)
        return candidate
    }

    fun normalizePermission(rawPermission: String): String {
        if (rawPermission.isBlank() || rawPermission.length > MAX_NAME_LENGTH ||
            rawPermission.any { it.isISOControl() || it.isWhitespace() } ||
            rawPermission.split('.').any { !identifier.matches(it) }
        ) {
            throw GuestComponentNameException(GuestComponentNameReason.INVALID_PERMISSION)
        }
        return rawPermission
    }

    private fun validatePackage(packageName: String) {
        if (packageName.isBlank() || packageName.length > MAX_NAME_LENGTH ||
            packageName.split('.').any { !identifier.matches(it) }
        ) {
            throw GuestComponentNameException(GuestComponentNameReason.INVALID_PACKAGE)
        }
    }

    private fun validateQualifiedName(name: String, reason: GuestComponentNameReason) {
        if (name.length > MAX_NAME_LENGTH || name.split('.').any { !identifier.matches(it) }) {
            throw GuestComponentNameException(reason)
        }
    }
}
