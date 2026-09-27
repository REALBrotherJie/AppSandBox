package com.example.appsandbox.resolver

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.resolver.GuestIntentFilterException
import com.example.appsandbox.model.resolver.GuestIntentFilterNormalizer
import com.example.appsandbox.model.resolver.GuestRawIntentData
import com.example.appsandbox.packageinfo.GuestManifestAttribute
import com.example.appsandbox.packageinfo.GuestManifestNode
import com.example.appsandbox.packageinfo.GuestBinaryXmlManifest
import com.example.appsandbox.packageinfo.GuestIntentFilterManifestException
import com.example.appsandbox.packageinfo.GuestIntentFilterManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuestImplicitIntentResolverTest {
    private val packageName = "com.example.fixture"

    @Test
    fun actionAndCategoryMatchingIsExactAndMissingCategoryDoesNotMatch() {
        val component = component(
            "revision-a",
            "ActionActivity",
            filter(
                "revision-a",
                "ActionActivity",
                actions = listOf("com.example.VIEW"),
                categories = listOf("com.example.DEFAULT", "com.example.IMAGE")
            )
        )
        val resolver = resolver(record("revision-a", listOf(component)))

        assertResolved(
            resolver.resolve(request("revision-a", action = "com.example.VIEW", categories = listOf("com.example.DEFAULT")))
        )
        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.VIEW", categories = listOf("com.example.MISSING"))),
            GuestImplicitResolutionReason.NO_MATCH
        )
        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.view")),
            GuestImplicitResolutionReason.NO_MATCH
        )
    }

    @Test
    fun MIMEExactAndSubtypeWildcardMatchOnlyConcreteRequests() {
        val component = component(
            "revision-a",
            "MimeActivity",
            filter(
                "revision-a",
                "MimeActivity",
                actions = listOf("com.example.VIEW"),
                data = listOf(GuestRawIntentData(mimeType = "image/*"))
            )
        )
        val resolver = resolver(record("revision-a", listOf(component)))

        assertResolved(resolver.resolve(request("revision-a", mimeType = "image/png")))
        assertRejected(
            resolver.resolve(request("revision-a", mimeType = "text/plain")),
            GuestImplicitResolutionReason.NO_MATCH
        )
        assertRejected(
            resolver.resolve(request("revision-a", mimeType = "image")),
            GuestImplicitResolutionReason.INVALID_REQUEST
        )
        assertRejected(
            resolver.resolve(request("revision-a", mimeType = "*/*")),
            GuestImplicitResolutionReason.INVALID_REQUEST
        )
    }

    @Test
    fun URIDataNormalizesSchemeAndHostAndRejectsUnsupportedRequestParts() {
        val component = component(
            "revision-a",
            "UriActivity",
            filter(
                "revision-a",
                "UriActivity",
                actions = listOf("com.example.VIEW"),
                data = listOf(
                    GuestRawIntentData(
                        scheme = "HTTPS",
                        host = "Example.COM",
                        path = "/images"
                    )
                )
            )
        )
        val resolver = resolver(record("revision-a", listOf(component)))

        assertResolved(resolver.resolve(request("revision-a", uri = "https://example.com/images")))
        assertRejected(
            resolver.resolve(request("revision-a", uri = "https://example.com/other")),
            GuestImplicitResolutionReason.NO_MATCH
        )
        assertRejected(
            resolver.resolve(request("revision-a", uri = "https://example.com/images?unsafe=true")),
            GuestImplicitResolutionReason.INVALID_REQUEST
        )
        assertRejected(
            resolver.resolve(request("revision-a", uri = "https://example.com/%ZZ")),
            GuestImplicitResolutionReason.INVALID_REQUEST
        )
    }

    @Test
    fun PriorityWinsBeforeSpecificityAndTiesAreStable() {
        val broad = component(
            "revision-a",
            "BroadActivity",
            filter(
                "revision-a",
                "BroadActivity",
                actions = listOf("com.example.VIEW"),
                categories = listOf("com.example.DEFAULT"),
                data = listOf(GuestRawIntentData(mimeType = "image/*")),
                priority = 20
            )
        )
        val specific = component(
            "revision-a",
            "SpecificActivity",
            filter(
                "revision-a",
                "SpecificActivity",
                actions = listOf("com.example.VIEW"),
                categories = listOf("com.example.DEFAULT"),
                data = listOf(GuestRawIntentData(mimeType = "image/png")),
                priority = 10
            )
        )
        val samePriorityA = component(
            "revision-a",
            "AActivity",
            filter(
                "revision-a",
                "AActivity",
                actions = listOf("com.example.VIEW"),
                categories = listOf("com.example.DEFAULT"),
                data = listOf(GuestRawIntentData(mimeType = "image/*")),
                priority = 10
            )
        )
        val resolver = resolver(record("revision-a", listOf(specific, broad, samePriorityA)))

        val result = resolver.resolve(
            request("revision-a", categories = listOf("com.example.DEFAULT"), mimeType = "image/png")
        ) as GuestImplicitResolutionResult.Resolved
        assertEquals(
            listOf(
                "$packageName.BroadActivity",
                "$packageName.SpecificActivity",
                "$packageName.AActivity"
            ),
            result.candidates.map { it.component.className }
        )
    }

    @Test
    fun callerPolicyAndRevisionBindingFailClosed() {
        val privateComponent = component(
            "revision-a",
            "PrivateActivity",
            filter("revision-a", "PrivateActivity", actions = listOf("com.example.VIEW")),
            exported = false
        )
        val disabledComponent = component(
            "revision-a",
            "DisabledActivity",
            filter("revision-a", "DisabledActivity", actions = listOf("com.example.DISABLED")),
            enabled = false
        )
        val protectedComponent = component(
            "revision-a",
            "ProtectedActivity",
            filter("revision-a", "ProtectedActivity", actions = listOf("com.example.PROTECTED")),
            permissions = listOf("com.example.permission.TEST")
        )
        val unbound = component(
            "revision-other",
            "UnboundActivity",
            filter("revision-other", "UnboundActivity", actions = listOf("com.example.UNBOUND"))
        )
        val revisions = mutableListOf(record("revision-a", listOf(privateComponent, disabledComponent, protectedComponent, unbound)))
        val resolver = GuestImplicitIntentResolver { id -> revisions.firstOrNull { it.revisionId == id } }

        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.VIEW")),
            GuestImplicitResolutionReason.NOT_EXPORTED
        )
        assertResolved(
            resolver.resolve(
                request(
                    "revision-a",
                    action = "com.example.VIEW",
                    callerScope = GuestCallerScope.GUEST_INTERNAL
                )
            )
        )
        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.DISABLED")),
            GuestImplicitResolutionReason.DISABLED
        )
        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.PROTECTED")),
            GuestImplicitResolutionReason.PERMISSION_REQUIRED
        )
        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.UNBOUND")),
            GuestImplicitResolutionReason.REVISION_MISMATCH
        )
        assertRejected(
            resolver.resolve(request("revision-a", packageName = "com.example.other")),
            GuestImplicitResolutionReason.PACKAGE_MISMATCH
        )
        revisions.clear()
        assertRejected(
            resolver.resolve(request("revision-a", action = "com.example.VIEW")),
            GuestImplicitResolutionReason.REVISION_NOT_FOUND
        )
    }

    @Test
    fun duplicateFiltersAreCollapsedAndUnsupportedPatternIsRejected() {
        val action = attr("name", "com.example.VIEW")
        val category = attr("name", "com.example.DEFAULT")
        val duplicateFilter = GuestManifestNode(
            name = "intent-filter",
            attributes = emptyList(),
            children = listOf(
                GuestManifestNode("action", listOf(action), emptyList()),
                GuestManifestNode("category", listOf(category), emptyList())
            )
        )
        val root = GuestManifestNode(
            name = "manifest",
            attributes = emptyList(),
            children = listOf(
                GuestManifestNode(
                    name = "application",
                    attributes = emptyList(),
                    children = listOf(
                        GuestManifestNode(
                            name = "activity",
                            attributes = listOf(attr("name", ".Activity")),
                            children = listOf(duplicateFilter, duplicateFilter)
                        )
                    )
                )
            )
        )
        val filters = GuestIntentFilterManifestParser.parse(root, packageName, "revision-a")
        assertEquals(1, filters.size)

        val unsupported = duplicateFilter.copy(
            children = duplicateFilter.children + GuestManifestNode(
                "data",
                listOf(attr("pathPrefix", "/unsupported")),
                emptyList()
            )
        )
        val rejectedRoot = root.copy(
            children = listOf(
                root.children.single().copy(
                    children = listOf(
                        root.children.single().children.single().copy(children = listOf(unsupported))
                    )
                )
            )
        )
        try {
            GuestIntentFilterManifestParser.parse(rejectedRoot, packageName, "revision-a")
            throw AssertionError("expected unsupported pattern rejection")
        } catch (error: GuestIntentFilterManifestException) {
            assertEquals("unsupported-filter-attribute", error.reason.code)
        }
    }

    @Test
    fun malformedFilterInputsHaveStableReasons() {
        assertFilterReason("duplicate-action") {
            filter(
                "revision-a",
                "Activity",
                actions = listOf("com.example.VIEW", "com.example.VIEW")
            )
        }
        assertFilterReason("multiple-data-declarations") {
            filter(
                "revision-a",
                "Activity",
                actions = listOf("com.example.VIEW"),
                data = listOf(
                    GuestRawIntentData(scheme = "https"),
                    GuestRawIntentData(scheme = "content")
                )
            )
        }
        assertFilterReason("invalid-mime") {
            filter(
                "revision-a",
                "Activity",
                actions = listOf("com.example.VIEW"),
                data = listOf(GuestRawIntentData(mimeType = "image"))
            )
        }
        assertFilterReason("unsupported-data-pattern") {
            filter(
                "revision-a",
                "Activity",
                actions = listOf("com.example.VIEW"),
                data = listOf(GuestRawIntentData(host = "example.com"))
            )
        }
    }

    @Test
    fun binaryManifestMalformedInputFailsClosed() {
        try {
            GuestBinaryXmlManifest.parse(byteArrayOf(0x03, 0x00, 0x08, 0x00))
            throw AssertionError("expected malformed binary manifest rejection")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("truncated") || error.message.orEmpty().contains("chunk"))
        }
    }

    @Test
    fun builtFixtureBinaryManifestProducesNormalizedFilters() {
        var root = File(System.getProperty("user.dir"))
        val relativeApk = "test-guests/IntentFilterFixtures/build/outputs/apk/intentV1/debug/IntentFilterFixtures-intentV1-debug.apk"
        repeat(5) {
            if (File(root, relativeApk).isFile) return@repeat
            root.parentFile?.let { root = it }
        }
        val apk = File(root, relativeApk)
        assertTrue("fixture APK must be built for manifest parser coverage", apk.isFile)
        val filters = GuestIntentFilterManifestParser.parse(
            GuestBinaryXmlManifest.read(apk.path),
            "com.example.appsandbox.intentfilterfixture",
            "revision-a"
        )
        assertEquals(6, filters.size)
        assertEquals(
            setOf("com.example.intent.VIEW", "com.example.intent.EDIT"),
            filters.single { it.componentClassName.endsWith("FilterComponents\$FilterActivity") }.actions.toSet()
        )
        assertEquals("https", filters.single { it.componentClassName.endsWith("FilterComponents\$FilterActivity") }.data?.scheme)
        assertEquals("example.com", filters.single { it.componentClassName.endsWith("FilterComponents\$FilterActivity") }.data?.host)
    }

    private fun resolver(record: GuestPackageRecord) =
        GuestImplicitIntentResolver { id -> record.takeIf { it.revisionId == id } }

    private fun request(
        revisionId: String,
        packageName: String = this.packageName,
        callerScope: GuestCallerScope = GuestCallerScope.HOST_EXTERNAL,
        action: String = "com.example.VIEW",
        categories: List<String> = emptyList(),
        mimeType: String? = null,
        uri: String? = null
    ) = GuestImplicitIntentRequest(
        revisionId,
        packageName,
        GuestComponentType.ACTIVITY,
        callerScope,
        action,
        categories,
        mimeType,
        uri
    )

    private fun filter(
        revisionId: String,
        className: String,
        actions: List<String>,
        categories: List<String> = emptyList(),
        data: List<GuestRawIntentData> = emptyList(),
        priority: Int = 0
    ) = GuestIntentFilterNormalizer.normalize(
        revisionId = revisionId,
        packageName = packageName,
        componentClassName = GuestComponentNames.normalize(packageName, className),
        componentType = GuestComponentType.ACTIVITY,
        actions = actions,
        categories = categories,
        dataDeclarations = data,
        priority = priority,
        autoVerify = false
    )

    private fun component(
        revisionId: String,
        className: String,
        filter: com.example.appsandbox.model.resolver.GuestIntentFilter,
        enabled: Boolean = true,
        exported: Boolean = true,
        permissions: List<String> = emptyList()
    ) = GuestComponent(
        revisionId = revisionId,
        packageName = packageName,
        className = GuestComponentNames.normalize(packageName, className),
        type = GuestComponentType.ACTIVITY,
        enabled = enabled,
        exported = exported,
        declaredPermissions = permissions,
        intentFilters = listOf(filter)
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

    private fun assertResolved(result: GuestImplicitResolutionResult) {
        assertTrue("expected resolved result but was $result", result is GuestImplicitResolutionResult.Resolved)
    }

    private fun assertRejected(
        result: GuestImplicitResolutionResult,
        expected: GuestImplicitResolutionReason
    ) {
        assertTrue("expected rejected result but was $result", result is GuestImplicitResolutionResult.Rejected)
        assertEquals(expected, (result as GuestImplicitResolutionResult.Rejected).reason)
    }

    private fun assertFilterReason(expected: String, action: () -> Unit) {
        try {
            action()
            throw AssertionError("expected $expected")
        } catch (error: GuestIntentFilterException) {
            assertEquals(expected, error.reason.code)
        }
    }

    private fun attr(name: String, value: String) =
        com.example.appsandbox.packageinfo.GuestManifestAttribute(
            GuestBinaryXmlManifest.ANDROID_NS,
            name,
            value
        )
}
