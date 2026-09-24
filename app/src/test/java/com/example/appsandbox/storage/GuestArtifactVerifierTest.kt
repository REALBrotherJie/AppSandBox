package com.example.appsandbox.storage

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuestArtifactVerifierTest {
    private fun record(file: File, sha: String? = GuestArtifactVerifier.sha256(file), size: Long = file.length(), schema: Int = 2) =
        GuestPackageRecord("guest", "com.example.guest", null, 1, file.path, "Guest", 1L, ComponentSummary(0, 0, 0, 0), "revision", sha, size, schema)

    @Test fun sha256KnownBytes() {
        val file = File.createTempFile("guest-artifact", ".apk").apply { writeText("abc"); setReadOnly() }
        try { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GuestArtifactVerifier.sha256(file)) }
        finally { file.delete() }
    }

    @Test fun validImmutableArtifact() {
        val file = File.createTempFile("guest-artifact", ".apk").apply { writeText("apk"); setReadOnly() }
        try { assertEquals(ArtifactState.VALID, GuestArtifactVerifier.verify(record(file)).state); assertTrue(!file.canWrite()) }
        finally { file.delete() }
    }

    @Test fun missingArtifactIsExplicit() {
        val file = File.createTempFile("guest-artifact", ".apk"); val r = record(file); file.delete()
        assertEquals(ArtifactState.MISSING_ARTIFACT, GuestArtifactVerifier.verify(r).state)
    }

    @Test fun hashMismatchIsExplicit() {
        val file = File.createTempFile("guest-artifact", ".apk").apply { writeText("changed"); setReadOnly() }
        try { assertEquals(ArtifactState.HASH_MISMATCH, GuestArtifactVerifier.verify(record(file, "00".repeat(32))).state) }
        finally { file.delete() }
    }

    @Test fun legacyWithoutHashNeedsReimport() {
        val file = File.createTempFile("guest-artifact", ".apk").apply { writeText("legacy"); setReadOnly() }
        try { assertEquals(ArtifactState.LEGACY_UNVERIFIED, GuestArtifactVerifier.verify(record(file, null, file.length(), 1)).state) }
        finally { file.delete() }
    }

    @Test fun sizeMismatchIsNotAccepted() {
        val file = File.createTempFile("guest-artifact", ".apk").apply { writeText("apk"); setReadOnly() }
        try { assertNotEquals(ArtifactState.VALID, GuestArtifactVerifier.verify(record(file, GuestArtifactVerifier.sha256(file), 99)).state) }
        finally { file.delete() }
    }
}
