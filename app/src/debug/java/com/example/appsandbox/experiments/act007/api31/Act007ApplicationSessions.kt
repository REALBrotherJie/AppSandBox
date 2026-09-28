package com.example.appsandbox.experiments.act007.api31

import android.content.Context
import com.example.appsandbox.experiments.act007.core.*
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

data class Act007SessionResult(
    val outcome: String,
    val reason: String,
    val instanceId: String,
    val runId: String = "",
    val detail: String = "",
    val constructed: Int = 0,
    val onCreateAttempted: Int = 0,
    val onCreateCompleted: Int = 0
)

object Act007ApplicationSessions {
    private fun controller(context: Context) = GuestApplicationSessionController(
        GuestApplicationSessionRegistry(File(context.filesDir, "act007-application-sessions.json"))
    )

    fun latest(context: Context, instanceId: String): GuestApplicationSessionSnapshot? =
        controller(context).snapshots().lastOrNull { it.request.instanceId == instanceId }

    @JvmStatic fun canDelete(context: Context, instanceId: String): Boolean =
        latest(context, instanceId)?.state !in setOf(
            GuestApplicationSessionState.NEW,
            GuestApplicationSessionState.STARTING,
            GuestApplicationSessionState.RUNNING,
            GuestApplicationSessionState.STOPPING
        )

    fun start(context: Context, instanceId: String, runId: String, operationId: String): Act007SessionResult = try {
        val instance = requireNotNull(GuestInstanceStore(context).get(instanceId))
        val revision = requireNotNull(GuestStore(context).findRevision(instance.guestRevisionId))
        val applicationClass = requireNotNull(
            context.packageManager.getPackageArchiveInfo(revision.apkPath, 0)?.applicationInfo?.className
        )
        val request = GuestApplicationSessionRequest(
            runId, operationId, instance.instanceId, instance.guestRevisionId,
            instance.guestSha256, instance.guestPackageName, applicationClass, instance.dataRoot
        )
        val expected = GuestApplicationSessionExpected(
            instance.instanceId, instance.guestRevisionId, instance.guestSha256,
            instance.guestPackageName, applicationClass, instance.dataRoot,
            File(context.filesDir, "guest-instances").canonicalPath
        )
        val snapshot = controller(context).start(
            request, expected,
            C1GuestApplicationSessionExecutor.create(context, instance, revision, applicationClass)
        )
        Act007SessionResult(
            snapshot.state.name, snapshot.failure.name, instanceId, runId,
            snapshot.detail.orEmpty(), snapshot.constructorCompleted, snapshot.onCreateAttempted, snapshot.onCreateCompleted
        )
    } catch (error: Throwable) {
        Act007SessionResult("FAILED", "INTERNAL", instanceId, runId, error.message.orEmpty())
    }

    fun stop(context: Context, runId: String, operationId: String): Act007SessionResult {
        val snapshot = controller(context).stop(runId, operationId)
        return Act007SessionResult(
            snapshot.state.name, snapshot.failure.name,
            snapshot.request.instanceId, runId, snapshot.detail.orEmpty()
        )
    }

    fun delete(context: Context, instanceId: String): Boolean {
        check(canDelete(context, instanceId)) { "ACTIVE_SESSION" }
        return GuestInstanceStore(context).delete(instanceId)
    }

    fun format(result: Act007SessionResult): String = buildString {
        appendLine("outcome=${result.outcome}")
        appendLine("reason=${result.reason}")
        appendLine("instanceId=${result.instanceId}")
        appendLine("sessionRunId=${result.runId}")
        appendLine("constructed=${result.constructed}")
        appendLine("onCreateAttempted=${result.onCreateAttempted}")
        appendLine("onCreateCompleted=${result.onCreateCompleted}")
        if (result.detail.isNotBlank()) append("detail=${result.detail.take(240)}")
    }.trimEnd()
}
