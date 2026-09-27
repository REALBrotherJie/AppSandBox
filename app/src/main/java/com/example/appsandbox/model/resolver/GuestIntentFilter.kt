package com.example.appsandbox.model.resolver

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val GUEST_INTENT_NAME = Regex("[A-Za-z0-9_:.+-]+")

enum class GuestIntentFilterReason(val code: String) {
    MISSING_ACTION("missing-action"),
    INVALID_ACTION("invalid-action"),
    DUPLICATE_ACTION("duplicate-action"),
    INVALID_CATEGORY("invalid-category"),
    DUPLICATE_CATEGORY("duplicate-category"),
    INVALID_MIME("invalid-mime"),
    INVALID_SCHEME("invalid-scheme"),
    INVALID_HOST("invalid-host"),
    INVALID_PATH("invalid-path"),
    INVALID_DATA("invalid-data"),
    MULTIPLE_DATA_DECLARATIONS("multiple-data-declarations"),
    UNSUPPORTED_DATA_PATTERN("unsupported-data-pattern"),
    INVALID_PRIORITY("invalid-priority"),
    INVALID_AUTOVERIFY("invalid-autoverify"),
    INVALID_FILTER("invalid-filter")
}

class GuestIntentFilterException(
    val reason: GuestIntentFilterReason,
    message: String = reason.code
) : IllegalArgumentException(message)

data class GuestRawIntentData(
    val mimeType: String? = null,
    val scheme: String? = null,
    val host: String? = null,
    val path: String? = null
)

data class GuestMimeType(
    val type: String,
    val subtype: String
) {
    init {
        require(type == type.lowercase(Locale.US)) { "MIME type is not normalized" }
        require(subtype == subtype.lowercase(Locale.US)) { "MIME subtype is not normalized" }
        require(type.isNotBlank() && subtype.isNotBlank()) { "MIME type is blank" }
        require(type == "*" || MIME_TOKEN.matches(type)) { "MIME type is invalid" }
        require(subtype == "*" || MIME_TOKEN.matches(subtype)) { "MIME subtype is invalid" }
        require(type != "*" || subtype == "*") { "MIME wildcard is invalid" }
    }

    val value: String get() = "$type/$subtype"
    val specificity: Int get() = if (subtype == "*") 1 else 2

    companion object {
        private val MIME_TOKEN = Regex("[a-z0-9!#$&^_.+-]+")

        fun normalize(raw: String): GuestMimeType {
            if (raw.length > 255 || raw.any { it.isISOControl() || it.isWhitespace() }) {
                throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_MIME)
            }
            val separator = raw.indexOf('/')
            if (separator <= 0 || separator != raw.lastIndexOf('/') || separator == raw.lastIndex) {
                throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_MIME)
            }
            val type = raw.substring(0, separator).lowercase(Locale.US)
            val subtype = raw.substring(separator + 1).lowercase(Locale.US)
            if ((type != "*" && !MIME_TOKEN.matches(type)) ||
                (subtype != "*" && !MIME_TOKEN.matches(subtype)) ||
                (type == "*" && subtype != "*")
            ) {
                throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_MIME)
            }
            return GuestMimeType(type, subtype)
        }
    }
}

data class GuestIntentData(
    val scheme: String?,
    val host: String?,
    val path: String?
) {
    init {
        if (scheme != null) require(SCHEME.matches(scheme)) { "URI scheme is not normalized" }
        if (host != null) require(HOST.matches(host) || IPV6.matches(host)) { "URI host is not normalized" }
        if (path != null) {
            require(path.startsWith('/')) { "URI path is not normalized" }
            require(path.none { it.isISOControl() || it.isWhitespace() }) { "URI path contains whitespace" }
            require(path.indexOf('\\') < 0 && path.indexOf('*') < 0) { "URI path pattern is unsupported" }
            require(validPercentEscapes(path)) { "URI path escape is invalid" }
        }
        if (host != null) require(scheme != null) { "URI host has no scheme" }
        if (path != null) require(host != null) { "URI path has no host" }
    }

    val specificity: Int
        get() = (if (scheme != null) 1 else 0) +
            (if (host != null) 2 else 0) +
            (if (path != null) 3 else 0)

    companion object {
        private val SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")
        private val HOST = Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,253}[A-Za-z0-9])?")
        private val IPV6 = Regex("\\[[0-9A-Fa-f:]+]")

        fun normalize(scheme: String?, host: String?, path: String?): GuestIntentData? {
            if (scheme == null && host == null && path == null) return null
            val normalizedScheme = scheme
            val normalizedHost = host
            if (normalizedScheme != null &&
                (normalizedScheme.length > 255 || !SCHEME.matches(normalizedScheme))
            ) {
                throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_SCHEME)
            }
            if (normalizedHost != null &&
                (normalizedHost.length > 255 ||
                    normalizedHost.contains('*') ||
                    normalizedHost.any { it.isISOControl() || it.isWhitespace() } ||
                    (!HOST.matches(normalizedHost) && !IPV6.matches(normalizedHost)))
            ) {
                throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_HOST)
            }
            if (path != null &&
                (path.isBlank() || path.length > 1024 || !path.startsWith('/') ||
                    path.any { it.isISOControl() || it.isWhitespace() } ||
                    path.contains('\\') || path.contains('*') ||
                    !validPercentEscapes(path))
            ) {
                throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_PATH)
            }
            if (normalizedHost != null && normalizedScheme == null ||
                path != null && normalizedHost == null
            ) {
                throw GuestIntentFilterException(GuestIntentFilterReason.UNSUPPORTED_DATA_PATTERN)
            }
            return GuestIntentData(normalizedScheme, normalizedHost, path)
        }

        private fun validPercentEscapes(value: String): Boolean {
            var index = 0
            while (index < value.length) {
                if (value[index] == '%') {
                    if (index + 2 >= value.length ||
                        !value[index + 1].isHexDigit() ||
                        !value[index + 2].isHexDigit()
                    ) {
                        return false
                    }
                    index += 3
                } else {
                    index++
                }
            }
            return true
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}

data class GuestIntentFilter(
    val revisionId: String,
    val packageName: String,
    val componentClassName: String,
    val componentType: GuestComponentType,
    val actions: List<String>,
    val categories: List<String>,
    val mimeTypes: List<GuestMimeType>,
    val data: GuestIntentData?,
    val priority: Int,
    val autoVerify: Boolean
) {
    init {
        require(revisionId.isNotBlank()) { "filter revision is blank" }
        require(packageName.isNotBlank()) { "filter package is blank" }
        require(componentClassName.isNotBlank()) { "filter component is blank" }
        require(componentType != GuestComponentType.PROVIDER) { "Provider filters are unsupported" }
        require(actions.isNotEmpty()) { "filter has no actions" }
        require(actions == actions.distinct().sorted()) { "filter actions are not normalized" }
        require(categories == categories.distinct().sorted()) { "filter categories are not normalized" }
        require(actions.all { it.length <= 255 && GUEST_INTENT_NAME.matches(it) }) {
            "filter action is invalid"
        }
        require(categories.all { it.length <= 255 && GUEST_INTENT_NAME.matches(it) }) {
            "filter category is invalid"
        }
        require(mimeTypes == mimeTypes.distinct().sortedBy { it.value }) { "filter MIME types are not normalized" }
        require(priority in -1000..1000) { "filter priority is invalid" }
    }

    val specificity: Int
        get() = categories.size +
            (mimeTypes.maxOfOrNull { it.specificity } ?: 0) +
            (data?.specificity ?: 0)

    val canonicalKey: String
        get() = buildString {
            append(componentType.code).append('|')
            append(componentClassName).append('|')
            append(actions.joinToString(",")).append('|')
            append(categories.joinToString(",")).append('|')
            append(mimeTypes.joinToString(",") { it.value }).append('|')
            append(data?.scheme.orEmpty()).append('|')
            append(data?.host.orEmpty()).append('|')
            append(data?.path.orEmpty()).append('|')
            append(priority).append('|').append(autoVerify)
        }

    fun toJson() = JSONObject()
        .put("revisionId", revisionId)
        .put("packageName", packageName)
        .put("componentClassName", componentClassName)
        .put("componentType", componentType.code)
        .put("actions", JSONArray(actions))
        .put("categories", JSONArray(categories))
        .put("mimeTypes", JSONArray(mimeTypes.map { it.value }))
        .put("data", data?.let {
            JSONObject()
                .put("scheme", it.scheme)
                .put("host", it.host)
                .put("path", it.path)
        })
        .put("priority", priority)
        .put("autoVerify", autoVerify)

    companion object {
        fun fromJson(value: JSONObject): GuestIntentFilter {
            val actions = value.getJSONArray("actions").stringList()
            val categories = value.optJSONArray("categories")?.stringList().orEmpty()
            val mimeTypes = value.optJSONArray("mimeTypes")
                ?.stringList()
                ?.map(GuestMimeType::normalize)
                ?.sortedBy { it.value }
                .orEmpty()
            val dataValue = value.optJSONObject("data")
            val data = dataValue?.let {
                GuestIntentData.normalize(
                    it.optString("scheme").ifEmpty { null },
                    it.optString("host").ifEmpty { null },
                    it.optString("path").ifEmpty { null }
                )
            }
            return GuestIntentFilter(
                revisionId = value.getString("revisionId"),
                packageName = value.getString("packageName"),
                componentClassName = value.getString("componentClassName"),
                componentType = GuestComponentType.fromCode(value.getString("componentType")),
                actions = actions.sorted(),
                categories = categories.sorted(),
                mimeTypes = mimeTypes,
                data = data,
                priority = value.optInt("priority", 0),
                autoVerify = value.optBoolean("autoVerify", false)
            )
        }

        private fun JSONArray.stringList(): List<String> =
            (0 until length()).map { getString(it) }
    }
}

object GuestIntentFilterNormalizer {
    fun normalize(
        revisionId: String,
        packageName: String,
        componentClassName: String,
        componentType: GuestComponentType,
        actions: List<String>,
        categories: List<String>,
        dataDeclarations: List<GuestRawIntentData>,
        priority: Int,
        autoVerify: Boolean
    ): GuestIntentFilter {
        val normalizedActions = normalizeNames(actions, GuestIntentFilterReason.INVALID_ACTION)
        if (normalizedActions.isEmpty()) {
            throw GuestIntentFilterException(GuestIntentFilterReason.MISSING_ACTION)
        }
        if (normalizedActions.distinct().size != actions.size) {
            throw GuestIntentFilterException(GuestIntentFilterReason.DUPLICATE_ACTION)
        }
        val normalizedCategories = normalizeNames(categories, GuestIntentFilterReason.INVALID_CATEGORY)
        if (normalizedCategories.distinct().size != categories.size) {
            throw GuestIntentFilterException(GuestIntentFilterReason.DUPLICATE_CATEGORY)
        }
        if (priority !in -1000..1000) {
            throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_PRIORITY)
        }
        val mimeTypes: List<GuestMimeType>
        val data: GuestIntentData?
        when {
            dataDeclarations.isEmpty() -> {
                mimeTypes = emptyList()
                data = null
            }

            dataDeclarations.size > 1 &&
                dataDeclarations.all { it.scheme == null && it.host == null && it.path == null && it.mimeType != null } -> {
                mimeTypes = dataDeclarations.map { GuestMimeType.normalize(requireNotNull(it.mimeType)) }
                    .distinct()
                    .sortedBy { it.value }
                if (mimeTypes.size != dataDeclarations.size) {
                    throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_FILTER)
                }
                data = null
            }

            dataDeclarations.size > 1 -> {
                throw GuestIntentFilterException(GuestIntentFilterReason.MULTIPLE_DATA_DECLARATIONS)
            }

            else -> {
                val declaration = dataDeclarations.single()
                mimeTypes = declaration.mimeType?.let { listOf(GuestMimeType.normalize(it)) }.orEmpty()
                data = GuestIntentData.normalize(declaration.scheme, declaration.host, declaration.path)
                if (mimeTypes.isEmpty() && data == null) {
                    throw GuestIntentFilterException(GuestIntentFilterReason.INVALID_DATA)
                }
            }
        }
        return GuestIntentFilter(
            revisionId = revisionId,
            packageName = packageName,
            componentClassName = componentClassName,
            componentType = componentType,
            actions = normalizedActions,
            categories = normalizedCategories,
            mimeTypes = mimeTypes,
            data = data,
            priority = priority,
            autoVerify = autoVerify
        )
    }

    fun normalizeAction(raw: String): String = normalizeName(raw, GuestIntentFilterReason.INVALID_ACTION)

    fun normalizeCategory(raw: String): String = normalizeName(raw, GuestIntentFilterReason.INVALID_CATEGORY)

    fun normalizeMime(raw: String): GuestMimeType = GuestMimeType.normalize(raw)

    private fun normalizeNames(values: List<String>, reason: GuestIntentFilterReason): List<String> {
        val normalized = values.map { normalizeName(it, reason) }.sorted()
        return normalized
    }

    private fun normalizeName(raw: String, reason: GuestIntentFilterReason): String {
        if (raw.isBlank() || raw.length > 255 ||
            raw.any { it.isISOControl() || it.isWhitespace() } ||
            !GUEST_INTENT_NAME.matches(raw)
        ) {
            throw GuestIntentFilterException(reason)
        }
        return raw
    }
}
