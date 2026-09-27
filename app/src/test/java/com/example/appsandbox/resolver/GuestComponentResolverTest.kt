package com.example.appsandbox.resolver

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNameException
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestComponentResolverTest {
    private val packageName = "com.example.fixture"
    private val revisionA = record(
        "revision-a",
        listOf(
            component("revision-a", ".VisibleActivity", GuestComponentType.ACTIVITY, exported = true),
            component("revision-a", ".PrivateActivity", GuestComponentType.ACTIVITY, exported = false),
            component("revision-a", ".DisabledService", GuestComponentType.SERVICE, enabled = false, exported = true),
            component("revision-a", ".ProtectedService", GuestComponentType.SERVICE, exported = true, permissions = listOf("com.example.fixture.permission.TEST")),
            component("revision-a", ".Receiver", GuestComponentType.RECEIVER, exported = true),
            component("revision-a", ".Provider", GuestComponentType.PROVIDER, exported = true)
        )
    )
    private val revisionB = record(
        "revision-b",
        listOf(component("revision-b", ".ChangedActivity", GuestComponentType.ACTIVITY, exported = true))
    )

    @Test
    fun relativeAndFullNamesNormalizeAndPackageEscapeFailsClosed() {
        assertEquals("$packageName.VisibleActivity", GuestComponentNames.normalize(packageName, ".VisibleActivity"))
        assertEquals("$packageName.VisibleActivity", GuestComponentNames.normalize(packageName, "VisibleActivity"))
        assertEquals("$packageName.VisibleActivity", GuestComponentNames.normalize(packageName, "$packageName.VisibleActivity"))

        try {
            GuestComponentNames.normalize(packageName, "com.other.Evil")
            throw AssertionError("expected package escape")
        } catch (error: GuestComponentNameException) {
            assertEquals("package-escape", error.reason.code)
        }
        try {
            GuestComponentNames.normalize(packageName, "bad/name")
            throw AssertionError("expected path-like name rejection")
        } catch (error: GuestComponentNameException) {
            assertEquals("invalid-class-name", error.reason.code)
        }
        try {
            GuestComponentNames.normalize(packageName, "a".repeat(256))
            throw AssertionError("expected long name rejection")
        } catch (error: GuestComponentNameException) {
            assertEquals("name-too-long", error.reason.code)
        }
        try {
            GuestComponentNames.normalize(packageName, "Bad\u0001Name")
            throw AssertionError("expected control character rejection")
        } catch (error: GuestComponentNameException) {
            assertEquals("invalid-class-name", error.reason.code)
        }
    }

    @Test
    fun explicitResolverUsesRequestedRevisionAndDoesNotFallBackToLatest() {
        var visible = listOf(revisionA, revisionB)
        val resolver = GuestComponentResolver { id -> visible.firstOrNull { it.revisionId == id } }

        assertResolved(resolver.resolve(request("revision-a", ".VisibleActivity", GuestComponentType.ACTIVITY)))
        assertRejected(
            resolver.resolve(request("revision-a", ".ChangedActivity", GuestComponentType.ACTIVITY)),
            GuestResolutionReason.NOT_FOUND
        )
        assertResolved(resolver.resolve(request("revision-b", ".ChangedActivity", GuestComponentType.ACTIVITY)))

        visible = listOf(revisionB)
        assertRejected(
            resolver.resolve(request("revision-a", ".VisibleActivity", GuestComponentType.ACTIVITY)),
            GuestResolutionReason.REVISION_NOT_FOUND
        )
    }

    @Test
    fun typeAndAvailabilityAndCallerScopeReasonsAreStable() {
        val resolver = GuestComponentResolver { id -> listOf(revisionA).firstOrNull { it.revisionId == id } }

        assertRejected(
            resolver.resolve(request("revision-a", ".VisibleActivity", GuestComponentType.SERVICE)),
            GuestResolutionReason.TYPE_MISMATCH
        )
        assertRejected(
            resolver.resolve(request("revision-a", ".DisabledService", GuestComponentType.SERVICE)),
            GuestResolutionReason.DISABLED
        )
        assertRejected(
            resolver.resolve(request("revision-a", ".PrivateActivity", GuestComponentType.ACTIVITY, GuestCallerScope.HOST_EXTERNAL)),
            GuestResolutionReason.NOT_EXPORTED
        )
        assertResolved(
            resolver.resolve(request("revision-a", ".PrivateActivity", GuestComponentType.ACTIVITY, GuestCallerScope.GUEST_INTERNAL))
        )
        assertRejected(
            resolver.resolve(request("revision-a", ".ProtectedService", GuestComponentType.SERVICE)),
            GuestResolutionReason.PERMISSION_REQUIRED
        )
        assertRejected(
            resolver.resolve(request("revision-a", ".VisibleActivity", GuestComponentType.ACTIVITY, packageName = "com.other.package")),
            GuestResolutionReason.PACKAGE_MISMATCH
        )
    }

    @Test
    fun unboundComponentCannotResolve() {
        val unbound = record(
            "revision-a",
            listOf(component("revision-b", ".VisibleActivity", GuestComponentType.ACTIVITY, exported = true))
        )
        val resolver = GuestComponentResolver { id -> unbound.takeIf { it.revisionId == id } }

        assertRejected(
            resolver.resolve(request("revision-a", ".VisibleActivity", GuestComponentType.ACTIVITY)),
            GuestResolutionReason.REVISION_MISMATCH
        )
    }

    private fun request(
        revisionId: String,
        className: String,
        type: GuestComponentType,
        callerScope: GuestCallerScope = GuestCallerScope.HOST_EXTERNAL,
        packageName: String = this.packageName
    ) = GuestComponentRequest(revisionId, packageName, className, type, callerScope)

    private fun assertResolved(result: GuestResolutionResult) {
        assertTrue("expected resolved result but was $result", result is GuestResolutionResult.Resolved)
    }

    private fun assertRejected(result: GuestResolutionResult, expected: GuestResolutionReason) {
        assertTrue("expected rejected result but was $result", result is GuestResolutionResult.Rejected)
        assertEquals(expected, (result as GuestResolutionResult.Rejected).reason)
    }

    private fun component(
        revisionId: String,
        rawName: String,
        type: GuestComponentType,
        enabled: Boolean = true,
        exported: Boolean,
        permissions: List<String> = emptyList()
    ) = GuestComponent(
        revisionId,
        packageName,
        GuestComponentNames.normalize(packageName, rawName),
        type,
        enabled,
        exported,
        permissions
    )

    private fun record(revisionId: String, components: List<GuestComponent>) = GuestPackageRecord(
        internalGuestId = revisionId,
        packageName = packageName,
        versionName = "1",
        versionCode = 1,
        apkPath = "/guests/$revisionId/base.apk",
        appLabel = "Fixture",
        importedAt = 1,
        componentSummary = ComponentSummary.fromComponents(components),
        revisionId = revisionId,
        components = components
    )
}
