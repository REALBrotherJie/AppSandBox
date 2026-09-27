package com.example.appsandbox.runtime

import android.content.Context
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.storage.GuestInstanceBinding
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

class GuestRuntimeRepositoryImpl(private val context: Context) : GuestRuntimeRepository {
    override fun resolve(instanceId: String, revisionId: String): GuestRuntimeResolvedSession {
        val instance = try {
            GuestInstanceStore(context).get(instanceId)
        } catch (error: Throwable) {
            throw GuestRuntimeException(GuestRuntimeError.INVALID_IDENTITY, "instance registry unavailable", error)
        } ?: throw GuestRuntimeException(GuestRuntimeError.DELETED_INSTANCE, "deleted instance")

        if (instance.guestRevisionId != revisionId) {
            throw GuestRuntimeException(GuestRuntimeError.REVISION_MISMATCH, "revision mismatch")
        }
        val revision = GuestStore(context).findRevision(revisionId)
            ?: throw GuestRuntimeException(GuestRuntimeError.REVISION_MISMATCH, "missing revision")
        GuestInstanceBinding.validate(instance, revision)?.let { reason ->
            val error = if (reason.contains("APK", true) || reason.contains("SHA", true)) {
                GuestRuntimeError.ARTIFACT_MISMATCH
            } else {
                GuestRuntimeError.REVISION_MISMATCH
            }
            throw GuestRuntimeException(error, reason)
        }
        val artifactState = GuestArtifactVerifier.verify(revision)
        if (artifactState.state != ArtifactState.VALID) {
            throw GuestRuntimeException(GuestRuntimeError.ARTIFACT_MISMATCH, artifactState.message ?: "artifact mismatch")
        }
        val artifact = File(instance.guestApkPath)
        if (!artifact.isFile || !GuestArtifactVerifier.sha256(artifact).equals(instance.guestSha256, true)) {
            throw GuestRuntimeException(GuestRuntimeError.ARTIFACT_MISMATCH, "artifact mismatch")
        }
        val contract = try {
            GuestPackageReader(context).readContract(artifact.path)
        } catch (error: Throwable) {
            throw GuestRuntimeException(GuestRuntimeError.ARTIFACT_MISMATCH, error.message ?: "contract unavailable", error)
        }
        if (contract.version != 2 || revision.contractVersion != 2) {
            throw GuestRuntimeException(GuestRuntimeError.REVISION_MISMATCH, "runtime requires contract v2")
        }
        return GuestRuntimeResolvedSession(
            instanceId = instance.instanceId,
            revisionId = revision.revisionId,
            dataRoot = File(instance.dataRoot),
            allowedActions = contract.actions.map { it.action }.toSet()
        )
    }
}
