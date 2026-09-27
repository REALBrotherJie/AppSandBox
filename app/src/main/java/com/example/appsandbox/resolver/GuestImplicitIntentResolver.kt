package com.example.appsandbox.resolver

import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.resolver.GuestIntentData
import com.example.appsandbox.model.resolver.GuestIntentFilter
import com.example.appsandbox.model.resolver.GuestIntentFilterNormalizer
import com.example.appsandbox.model.resolver.GuestMimeType
import java.net.URI
import java.util.Locale

data class GuestImplicitIntentRequest(
    val revisionId: String,
    val packageName: String,
    val componentType: GuestComponentType,
    val callerScope: GuestCallerScope,
    val action: String,
    val categories: List<String> = emptyList(),
    val mimeType: String? = null,
    val uri: String? = null,
    val policy: GuestImplicitResolutionPolicy = GuestImplicitResolutionPolicy.DEFAULT_ONLY
)

enum class GuestImplicitResolutionPolicy { DEFAULT_ONLY, GENERAL }

data class GuestImplicitIntent(
    val action: String,
    val categories: List<String>,
    val mimeType: GuestMimeType?,
    val uri: GuestParsedUri?
)

data class GuestParsedUri(
    val scheme: String,
    val host: String?,
    val path: String?
)

enum class GuestImplicitResolutionReason(val code: String) {
    INVALID_REQUEST("invalid-request"),
    REVISION_NOT_FOUND("revision-not-found"),
    PACKAGE_MISMATCH("package-mismatch"),
    NO_MATCH("no-match"),
    AMBIGUOUS("ambiguous"),
    REVISION_MISMATCH("revision-mismatch"),
    DISABLED("disabled"),
    NOT_EXPORTED("not-exported"),
    PERMISSION_REQUIRED("permission-required")
}

data class GuestImplicitCandidate(
    val component: GuestComponent,
    val filter: GuestIntentFilter
) {
    val specificity: Int get() = filter.specificity
}

sealed interface GuestImplicitResolutionResult {
    data class Resolved(val candidates: List<GuestImplicitCandidate>) : GuestImplicitResolutionResult
    data class Ambiguous(val candidates: List<GuestImplicitCandidate>) : GuestImplicitResolutionResult

    data class Rejected(
        val reason: GuestImplicitResolutionReason,
        val revisionId: String,
        val packageName: String,
        val componentType: GuestComponentType,
        val action: String
    ) : GuestImplicitResolutionResult
}

class GuestImplicitIntentResolver(private val revisions: GuestRevisionSource) {
    fun resolve(request: GuestImplicitIntentRequest): GuestImplicitResolutionResult {
        if (request.revisionId.isBlank() || request.packageName.isBlank() || request.action.isBlank()) {
            return request.rejected(GuestImplicitResolutionReason.INVALID_REQUEST)
        }
        if (request.componentType == GuestComponentType.PROVIDER) {
            return request.rejected(GuestImplicitResolutionReason.INVALID_REQUEST)
        }
        runCatching { GuestComponentNames.normalize(request.packageName, "ValidationProbe") }
            .onFailure { return request.rejected(GuestImplicitResolutionReason.INVALID_REQUEST) }
        val intent = runCatching { normalizeIntent(request) }
            .getOrElse { return request.rejected(GuestImplicitResolutionReason.INVALID_REQUEST) }
        val revision = revisions.findRevision(request.revisionId)
            ?: return request.rejected(GuestImplicitResolutionReason.REVISION_NOT_FOUND)
        if (revision.packageName != request.packageName) {
            return request.rejected(GuestImplicitResolutionReason.PACKAGE_MISMATCH)
        }

        val matches = revision.components
            .asSequence()
            .filter { it.type == request.componentType }
            .flatMap { component ->
                component.intentFilters
                    .asSequence()
                    .filter { filterMatches(it, intent) }
                    .filter { request.policy == GuestImplicitResolutionPolicy.GENERAL ||
                        request.componentType != GuestComponentType.ACTIVITY ||
                        "android.intent.category.DEFAULT" in it.categories }
                    .map { GuestImplicitCandidate(component, it) }
            }
            .sortedWith(candidateComparator)
            .toList()
        if (matches.isEmpty()) {
            return request.rejected(GuestImplicitResolutionReason.NO_MATCH)
        }

        val resolved = matches.filter { candidate ->
            componentAllowed(candidate.component, revision, request.callerScope)
        }
        if (resolved.size == 1) return GuestImplicitResolutionResult.Resolved(resolved)
        if (resolved.size > 1) return GuestImplicitResolutionResult.Ambiguous(resolved)
        return request.rejected(
            denialReason(matches.first().component, revision, request.callerScope)
        )
    }

    private fun normalizeIntent(request: GuestImplicitIntentRequest): GuestImplicitIntent =
        GuestImplicitIntent(
            action = GuestIntentFilterNormalizer.normalizeAction(request.action),
            categories = request.categories.map(GuestIntentFilterNormalizer::normalizeCategory)
                .also { normalized ->
                    if (normalized.size != normalized.distinct().size) {
                        throw IllegalArgumentException("duplicate category")
                    }
                }
                .sorted(),
            mimeType = request.mimeType?.let(GuestMimeType::normalize)?.also {
                if (it.type == "*" || it.subtype == "*") {
                    throw IllegalArgumentException("request MIME wildcard is unsupported")
                }
            },
            uri = request.uri?.let(::parseUri)
        )

    private fun filterMatches(filter: GuestIntentFilter, intent: GuestImplicitIntent): Boolean {
        if (intent.action !in filter.actions) return false
        if (!intent.categories.all { it in filter.categories }) return false
        if (!mimeMatches(filter, intent.mimeType)) return false
        if (filter.data == null) {
            return filter.mimeTypes.isNotEmpty() || intent.uri == null
        }
        val uri = intent.uri ?: return false
        return filter.data.scheme?.equals(uri.scheme) != false &&
            (filter.data.host == null || filter.data.host == uri.host) &&
            (filter.data.path == null || filter.data.path == uri.path)
    }

    private fun mimeMatches(filter: GuestIntentFilter, request: GuestMimeType?): Boolean {
        if (filter.mimeTypes.isEmpty()) return request == null
        if (request == null) return false
        return filter.mimeTypes.any {
            (it.type == "*" || it.type == request.type) &&
                (it.subtype == "*" || it.subtype == request.subtype)
        }
    }

    private fun componentAllowed(
        component: GuestComponent,
        revision: GuestPackageRecord,
        callerScope: GuestCallerScope
    ): Boolean =
        component.revisionId == revision.revisionId &&
            component.packageName == revision.packageName &&
            component.enabled &&
            (callerScope == GuestCallerScope.GUEST_INTERNAL || component.exported) &&
            component.declaredPermissions.isEmpty()

    private fun denialReason(
        component: GuestComponent,
        revision: GuestPackageRecord,
        callerScope: GuestCallerScope
    ): GuestImplicitResolutionReason =
        when {
            component.revisionId != revision.revisionId ||
                component.packageName != revision.packageName -> GuestImplicitResolutionReason.REVISION_MISMATCH
            !component.enabled -> GuestImplicitResolutionReason.DISABLED
            callerScope == GuestCallerScope.HOST_EXTERNAL && !component.exported ->
                GuestImplicitResolutionReason.NOT_EXPORTED
            component.declaredPermissions.isNotEmpty() ->
                GuestImplicitResolutionReason.PERMISSION_REQUIRED
            else -> GuestImplicitResolutionReason.NO_MATCH
        }

    private fun parseUri(raw: String): GuestParsedUri {
        if (raw.isBlank() || raw.length > 2048 || raw.contains('\\')) {
            throw IllegalArgumentException("URI is invalid")
        }
        val uri = URI(raw)
        val scheme = uri.scheme
            ?: throw IllegalArgumentException("URI has no scheme")
        if (uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null) {
            throw IllegalArgumentException("URI pattern is unsupported")
        }
        val host = uri.host
        if (uri.rawAuthority != null && host == null) {
            throw IllegalArgumentException("URI host is invalid")
        }
        val path = uri.rawPath.takeIf { it.isNotEmpty() }
        if (path != null) {
            GuestIntentData.normalize("uri", "host", path)
        }
        return GuestParsedUri(scheme, host, path)
    }

    private val candidateComparator = compareByDescending<GuestImplicitCandidate> { it.filter.priority }
        .thenByDescending { it.filter.specificity }
        .thenBy { it.component.className }
        .thenBy { it.component.type.code }
        .thenBy { it.filter.canonicalKey }

    private fun GuestImplicitIntentRequest.rejected(reason: GuestImplicitResolutionReason) =
        GuestImplicitResolutionResult.Rejected(
            reason = reason,
            revisionId = revisionId,
            packageName = packageName,
            componentType = componentType,
            action = action
        )
}
