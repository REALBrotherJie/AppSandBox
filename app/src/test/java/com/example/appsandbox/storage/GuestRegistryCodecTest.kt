package com.example.appsandbox.storage

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestRegistryCodecTest {
    private val component = GuestComponent(
        revisionId = "revision-a",
        packageName = "com.example.fixture",
        className = "com.example.fixture.VisibleActivity",
        type = GuestComponentType.ACTIVITY,
        enabled = true,
        exported = true
    )
    private val record = GuestPackageRecord(
        internalGuestId = "guest-a",
        packageName = "com.example.fixture",
        versionName = "1",
        versionCode = 1,
        apkPath = "/guests/guest-a/base.apk",
        appLabel = "Fixture",
        importedAt = 1,
        componentSummary = ComponentSummary.fromComponents(listOf(component)),
        revisionId = "revision-a",
        schemaVersion = GuestPackageRecord.CURRENT_SCHEMA_VERSION,
        components = listOf(component)
    )

    @Test
    fun schemaThreeRoundTripPreservesRevisionBoundManifest() {
        val decoded = GuestRegistryCodec.decode(GuestRegistryCodec.encode(listOf(record)))
        assertEquals(listOf(record), decoded)
        assertEquals("revision-a", decoded.single().components.single().revisionId)
    }

    @Test
    fun oldRegistrySchemaFailsClosedWithoutArrayFallback() {
        val old = """{"schemaVersion":2,"records":[]}"""
        try {
            GuestRegistryCodec.decode(old)
            throw AssertionError("expected schema rejection")
        } catch (error: GuestRegistryCodecException) {
            assertTrue(error.message.orEmpty().contains("Unsupported Guest registry schema"))
        }
    }

    @Test
    fun missingOrUnboundComponentManifestFailsClosed() {
        val root = JSONObject(GuestRegistryCodec.encode(listOf(record)))
        val records = root.getJSONArray("records")
        records.getJSONObject(0).remove("components")
        try {
            GuestRegistryCodec.decode(root.toString())
            throw AssertionError("expected missing component rejection")
        } catch (error: GuestRegistryCodecException) {
            assertTrue(error.message.orEmpty().contains("component manifest"))
        }

        val boundRoot = JSONObject(GuestRegistryCodec.encode(listOf(record)))
        val componentArray = boundRoot.getJSONArray("records").getJSONObject(0).getJSONArray("components")
        componentArray.getJSONObject(0).put("revisionId", "revision-other")
        try {
            GuestRegistryCodec.decode(boundRoot.toString())
            throw AssertionError("expected binding rejection")
        } catch (error: GuestRegistryCodecException) {
            assertTrue(error.message.orEmpty().contains("not bound"))
        }
    }
}
