package com.example.appsandbox.resolver

import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType

fun interface GuestRevisionSource {
    fun findRevision(revisionId: String): GuestPackageRecord?
}

enum class GuestCallerScope {
    GUEST_INTERNAL,
    HOST_EXTERNAL
}

data class GuestComponentRequest(
    val revisionId: String,
    val packageName: String,
    val className: String,
    val type: GuestComponentType,
    val callerScope: GuestCallerScope
)

enum class GuestResolutionReason(val code: String) {
    INVALID_REQUEST("invalid-request"),
    REVISION_NOT_FOUND("revision-not-found"),
    PACKAGE_MISMATCH("package-mismatch"),
    REVISION_MISMATCH("revision-mismatch"),
    NOT_FOUND("not-found"),
    TYPE_MISMATCH("type-mismatch"),
    DISABLED("disabled"),
    NOT_EXPORTED("not-exported"),
    PERMISSION_REQUIRED("permission-required")
}

sealed interface GuestResolutionResult {
    data class Resolved(val component: GuestComponent) : GuestResolutionResult

    data class Rejected(
        val reason: GuestResolutionReason,
        val revisionId: String,
        val packageName: String,
        val className: String,
        val type: GuestComponentType
    ) : GuestResolutionResult
}

class GuestComponentResolver(private val revisions: GuestRevisionSource) {
    fun resolve(request: GuestComponentRequest): GuestResolutionResult {
        if (request.revisionId.isBlank() || request.packageName.isBlank() || request.className.isBlank()) {
            return request.rejected(GuestResolutionReason.INVALID_REQUEST)
        }
        runCatching { GuestComponentNames.normalize(request.packageName, "ValidationProbe") }
            .onFailure { return request.rejected(GuestResolutionReason.INVALID_REQUEST) }
        val revision = revisions.findRevision(request.revisionId)
            ?: return request.rejected(GuestResolutionReason.REVISION_NOT_FOUND)
        if (revision.packageName != request.packageName) {
            return request.rejected(GuestResolutionReason.PACKAGE_MISMATCH)
        }
        val normalizedClass = runCatching {
            GuestComponentNames.normalize(request.packageName, request.className)
        }.getOrElse {
            return request.rejected(
                if (it is com.example.appsandbox.model.resolver.GuestComponentNameException &&
                    it.reason.code == "package-escape"
                ) GuestResolutionReason.PACKAGE_MISMATCH
                else GuestResolutionReason.INVALID_REQUEST
            )
        }
        val classMatches = revision.components.filter { it.className == normalizedClass }
        if (classMatches.isEmpty()) return request.rejected(GuestResolutionReason.NOT_FOUND)
        val component = classMatches.firstOrNull { it.type == request.type }
            ?: return request.rejected(GuestResolutionReason.TYPE_MISMATCH)
        if (component.revisionId != revision.revisionId || component.packageName != revision.packageName) {
            return request.rejected(GuestResolutionReason.REVISION_MISMATCH)
        }
        if (!component.enabled) return request.rejected(GuestResolutionReason.DISABLED)
        if (request.callerScope == GuestCallerScope.HOST_EXTERNAL && !component.exported) {
            return request.rejected(GuestResolutionReason.NOT_EXPORTED)
        }
        if (component.declaredPermissions.isNotEmpty()) {
            return request.rejected(GuestResolutionReason.PERMISSION_REQUIRED)
        }
        return GuestResolutionResult.Resolved(component)
    }

    private fun GuestComponentRequest.rejected(reason: GuestResolutionReason) =
        GuestResolutionResult.Rejected(reason, revisionId, packageName, className, type)
}
