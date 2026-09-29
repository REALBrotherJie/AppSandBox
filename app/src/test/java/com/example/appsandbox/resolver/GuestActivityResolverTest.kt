package com.example.appsandbox.resolver

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentType
import org.junit.Assert.assertEquals
import org.junit.Test

class GuestActivityResolverTest {
    private val revisionId = "22222222-2222-4222-8222-222222222222"
    private val packageName = "com.example.guest"
    private val activityName = "com.example.guest.GuestMainActivity"

    @Test
    fun hostExternalResolutionRequiresExportedEnabledActivityWithoutPermission() {
        val resolver = GuestComponentResolver { revision(false, true, emptyList()) }

        assertEquals(
            GuestResolutionReason.NOT_EXPORTED,
            rejected(resolver, GuestCallerScope.HOST_EXTERNAL).reason
        )
        assertEquals(
            GuestResolutionReason.DISABLED,
            rejected(GuestComponentResolver { revision(true, false, emptyList()) }, GuestCallerScope.HOST_EXTERNAL).reason
        )
        assertEquals(
            GuestResolutionReason.PERMISSION_REQUIRED,
            rejected(GuestComponentResolver { revision(true, true, listOf("com.example.PERMISSION")) }, GuestCallerScope.HOST_EXTERNAL).reason
        )
    }

    @Test
    fun packageTypeRevisionAndUnknownActivityAreRejected() {
        val resolver = GuestComponentResolver { revision(true, true, emptyList()) }
        assertEquals(
            GuestResolutionReason.PACKAGE_MISMATCH,
            rejected(resolver, GuestCallerScope.HOST_EXTERNAL, packageName = "com.other.guest").reason
        )
        assertEquals(
            GuestResolutionReason.TYPE_MISMATCH,
            rejected(resolver, GuestCallerScope.HOST_EXTERNAL, type = GuestComponentType.SERVICE).reason
        )
        assertEquals(
            GuestResolutionReason.NOT_FOUND,
            rejected(resolver, GuestCallerScope.HOST_EXTERNAL, className = "com.example.guest.OtherActivity").reason
        )
    }

    private fun rejected(
        resolver: GuestComponentResolver,
        scope: GuestCallerScope,
        packageName: String = this.packageName,
        className: String = activityName,
        type: GuestComponentType = GuestComponentType.ACTIVITY
    ): GuestResolutionResult.Rejected {
        val result = resolver.resolve(
            GuestComponentRequest(revisionId, packageName, className, type, scope)
        )
        check(result is GuestResolutionResult.Rejected) { "expected rejection, got $result" }
        return result
    }

    private fun revision(
        exported: Boolean,
        enabled: Boolean,
        permissions: List<String>
    ) = GuestPackageRecord(
        internalGuestId = "guest",
        packageName = packageName,
        versionName = "1",
        versionCode = 1,
        apkPath = "/tmp/guest.apk",
        appLabel = "Guest",
        importedAt = 1,
        componentSummary = ComponentSummary(1, 0, 0, 0),
        revisionId = revisionId,
        sha256 = "a".repeat(64),
        fileSize = 1,
        contractVersion = 2,
        components = listOf(
            GuestComponent(
                revisionId,
                packageName,
                activityName,
                GuestComponentType.ACTIVITY,
                enabled,
                exported,
                permissions
            )
        )
    )
}
