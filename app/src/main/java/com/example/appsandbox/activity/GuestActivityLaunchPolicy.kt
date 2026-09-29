package com.example.appsandbox.activity

import com.example.appsandbox.model.resolver.GuestComponentNames
import java.util.UUID

data class GuestActivityLaunchSpec(
    val launchId: String,
    val instanceId: String,
    val revisionId: String,
    val packageName: String,
    val componentName: String,
    val documentUri: String
)

enum class GuestActivityLaunchReason(val code: String) {
    INVALID_ID("invalid-id"),
    INVALID_PACKAGE("invalid-package"),
    INVALID_COMPONENT("invalid-component"),
    URI_MISMATCH("uri-mismatch"),
    STALE_RESULT("stale-result")
}

class GuestActivityLaunchException(
    val reason: GuestActivityLaunchReason,
    message: String = reason.code
) : IllegalArgumentException(message)

object GuestActivityLaunchPolicy {
    const val CARRIER_COMPONENT = "com.example.appsandbox.GuestActivityCarrierActivity"
    const val EXTRA_LAUNCH_ID = "appsandbox.activity.launchId"
    const val EXTRA_INSTANCE_ID = "appsandbox.activity.instanceId"
    const val EXTRA_REVISION_ID = "appsandbox.activity.revisionId"
    const val EXTRA_PACKAGE_NAME = "appsandbox.activity.packageName"
    const val EXTRA_COMPONENT_NAME = "appsandbox.activity.componentName"
    const val EXTRA_RESULT_LAUNCH_ID = "appsandbox.activity.resultLaunchId"
    const val EXTRA_RESULT_MESSAGE = "appsandbox.activity.resultMessage"
    const val URI_PREFIX = "appsandbox://activity/"

    fun create(
        launchId: String = UUID.randomUUID().toString(),
        instanceId: String,
        revisionId: String,
        packageName: String,
        componentName: String
    ): GuestActivityLaunchSpec {
        val normalizedPackage = normalizeId(packageName, GuestActivityLaunchReason.INVALID_PACKAGE)
        val normalizedComponent = runCatching {
            GuestComponentNames.normalize(normalizedPackage, componentName)
        }.getOrElse { throw GuestActivityLaunchException(GuestActivityLaunchReason.INVALID_COMPONENT) }
        validateUuid(launchId)
        validateUuid(instanceId)
        validateUuid(revisionId)
        val uri = "$URI_PREFIX$instanceId/$launchId"
        return GuestActivityLaunchSpec(launchId, instanceId, revisionId, normalizedPackage, normalizedComponent, uri)
    }

    fun validate(
        launchId: String?,
        instanceId: String?,
        revisionId: String?,
        packageName: String?,
        componentName: String?,
        documentUri: String?
    ): GuestActivityLaunchSpec {
        val spec = create(
            requireNotNull(launchId),
            requireNotNull(instanceId),
            requireNotNull(revisionId),
            requireNotNull(packageName),
            requireNotNull(componentName)
        )
        if (documentUri != spec.documentUri) {
            throw GuestActivityLaunchException(GuestActivityLaunchReason.URI_MISMATCH)
        }
        return spec
    }

    fun resultBelongsTo(expectedLaunchId: String, resultLaunchId: String?): Boolean =
        runCatching { validateUuid(expectedLaunchId) }.isSuccess && expectedLaunchId == resultLaunchId

    private fun normalizeId(value: String, reason: GuestActivityLaunchReason): String {
        if (value.isBlank() || value.length > 255 || value.any { it.isWhitespace() || it.isISOControl() }) {
            throw GuestActivityLaunchException(reason)
        }
        return value
    }

    private fun validateUuid(value: String) {
        if (!UUID_PATTERN.matches(value)) {
            throw GuestActivityLaunchException(GuestActivityLaunchReason.INVALID_ID)
        }
    }

    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}
