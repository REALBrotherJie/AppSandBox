package com.example.appsandbox.storage

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.resolver.GuestIntentFilter
import org.json.JSONArray
import org.json.JSONObject

class GuestRegistryCodecException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object GuestRegistryCodec {
    const val SCHEMA_VERSION = 4

    fun encode(records: List<GuestPackageRecord>): String =
        JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("records", JSONArray(records.map { it.toJson() }))
            .toString(2)

    fun decode(text: String): List<GuestPackageRecord> {
        val root = try {
            JSONObject(text)
        } catch (error: Throwable) {
            throw GuestRegistryCodecException("Malformed Guest registry", error)
        }
        val schema = root.optInt("schemaVersion", -1)
        if (schema != SCHEMA_VERSION) {
            throw GuestRegistryCodecException("Unsupported Guest registry schema: $schema")
        }
        val records = root.optJSONArray("records")
            ?: throw GuestRegistryCodecException("Guest registry has no records")
        return (0 until records.length()).map {
            try {
                parseRecord(records.getJSONObject(it))
            } catch (error: GuestRegistryCodecException) {
                throw error
            } catch (error: Throwable) {
                throw GuestRegistryCodecException("Malformed Guest revision", error)
            }
        }
    }

    private fun parseRecord(value: JSONObject): GuestPackageRecord {
        val recordSchema = value.optInt("schemaVersion", -1)
        if (recordSchema != GuestPackageRecord.CURRENT_SCHEMA_VERSION) {
            throw GuestRegistryCodecException("Unsupported Guest revision schema: $recordSchema")
        }
        val revisionId = value.optString("revisionId").takeIf { it.isNotBlank() }
            ?: throw GuestRegistryCodecException("Guest revision has no revisionId")
        val packageName = value.optString("packageName").takeIf { it.isNotBlank() }
            ?: throw GuestRegistryCodecException("Guest revision has no packageName")
        val summaryValue = value.optJSONObject("componentSummary")
            ?: throw GuestRegistryCodecException("Guest revision has no component summary")
        val componentsValue = value.optJSONArray("components")
            ?: throw GuestRegistryCodecException("Guest revision has no component manifest")
        val components = (0 until componentsValue.length()).map {
            parseComponent(componentsValue.getJSONObject(it))
        }
        if (components.any { it.revisionId != revisionId || it.packageName != packageName }) {
            throw GuestRegistryCodecException("Guest component is not bound to its revision")
        }
        if (components.map { it.className to it.type }.toSet().size != components.size) {
            throw GuestRegistryCodecException("Guest revision has duplicate components")
        }
        val summary = ComponentSummary(
            summaryValue.optInt("activityCount", -1),
            summaryValue.optInt("serviceCount", -1),
            summaryValue.optInt("receiverCount", -1),
            summaryValue.optInt("providerCount", -1)
        )
        if (summary != ComponentSummary.fromComponents(components)) {
            throw GuestRegistryCodecException("Guest component summary does not match manifest")
        }
        return GuestPackageRecord(
            internalGuestId = value.getString("internalGuestId"),
            packageName = packageName,
            versionName = value.optString("versionName").ifEmpty { null },
            versionCode = value.getLong("versionCode"),
            apkPath = value.getString("apkPath"),
            appLabel = value.getString("appLabel"),
            importedAt = value.getLong("importedAt"),
            componentSummary = summary,
            revisionId = revisionId,
            sha256 = value.optString("sha256").ifEmpty { null },
            fileSize = value.optLong("fileSize", -1),
            schemaVersion = recordSchema,
            contractVersion = value.optInt("contractVersion", 1),
            components = components
        )
    }

    private fun parseComponent(value: JSONObject): GuestComponent {
        return try {
            val permissionsValue = value.optJSONArray("declaredPermissions") ?: JSONArray()
            val permissions = (0 until permissionsValue.length()).map { permissionsValue.getString(it) }
            val filtersValue = value.optJSONArray("intentFilters") ?: JSONArray()
            val filters = (0 until filtersValue.length()).map {
                GuestIntentFilter.fromJson(filtersValue.getJSONObject(it))
            }
            val packageName = value.getString("packageName")
            val className = value.getString("className")
            val normalizedClassName = GuestComponentNames.normalize(packageName, className)
            require(normalizedClassName == className) { "component class is not normalized" }
            val normalizedPermissions = permissions.map(GuestComponentNames::normalizePermission).distinct().sorted()
            require(normalizedPermissions == permissions) { "component permissions are not normalized" }
            GuestComponent(
                revisionId = value.getString("revisionId"),
                packageName = packageName,
                className = className,
                type = GuestComponentType.fromCode(value.getString("type")),
                enabled = value.getBoolean("enabled"),
                exported = value.getBoolean("exported"),
                declaredPermissions = normalizedPermissions,
                intentFilters = filters
            )
        } catch (error: Throwable) {
            throw GuestRegistryCodecException(
                "Malformed Guest component: ${error.message ?: "invalid component"}",
                error
            )
        }
    }
}
