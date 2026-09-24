package com.example.appsandbox.storage

import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.security.MessageDigest

enum class ArtifactState { VALID, NO_RECORD, CORRUPT, MISSING_ARTIFACT, HASH_MISMATCH, LEGACY_UNVERIFIED }
data class ArtifactVerification(val state: ArtifactState, val actualSha256: String? = null, val message: String? = null)

object GuestArtifactVerifier {
    fun verify(record: GuestPackageRecord): ArtifactVerification {
        if (record.sha256.isNullOrBlank() || record.fileSize < 0L || record.schemaVersion < 2) return ArtifactVerification(ArtifactState.LEGACY_UNVERIFIED, message = "Revision has no trusted integrity metadata")
        val file = File(record.apkPath)
        if (!file.isFile) return ArtifactVerification(ArtifactState.MISSING_ARTIFACT, message = record.apkPath)
        if (file.length() != record.fileSize) return ArtifactVerification(ArtifactState.HASH_MISMATCH, message = "size=${file.length()} expected=${record.fileSize}")
        val actual = sha256(file)
        if (!actual.equals(record.sha256, true)) return ArtifactVerification(ArtifactState.HASH_MISMATCH, actual, "expected=${record.sha256}")
        if (file.canWrite()) return ArtifactVerification(ArtifactState.HASH_MISMATCH, actual, "artifact is writable")
        return ArtifactVerification(ArtifactState.VALID, actual)
    }
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(16 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class GuestStoreException(val state: ArtifactState, message: String) : IllegalStateException(message)
