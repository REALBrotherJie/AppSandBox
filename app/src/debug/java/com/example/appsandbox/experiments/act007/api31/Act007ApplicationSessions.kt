package com.example.appsandbox.experiments.act007.api31

import android.content.Context
import com.example.appsandbox.experiments.act007.core.*
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.packageinfo.GuestPackageReader
import java.io.File

data class Act007SessionResult(val outcome: String, val reason: String, val instanceId: String, val constructed: Int = 0, val onCreateAttempted: Int = 0, val onCreateCompleted: Int = 0, val loader: String = "none", val dataRoot: String = "none")
object Act007ApplicationSessions {
    private fun controller(context: Context) = GuestApplicationSessionController(GuestApplicationSessionRegistry(File(context.filesDir, "act007-sessions.json")))
    fun start(context: Context, instanceId: String, throwing: Boolean = false): Act007SessionResult {
        val instance = GuestInstanceStore(context).get(instanceId) ?: return Act007SessionResult("REJECTED", "MISSING_INSTANCE", instanceId)
        val revision = GuestStore(context).findRevision(instance.guestRevisionId) ?: return Act007SessionResult("REJECTED", "MISSING_REVISION", instanceId)
        val declared = requireNotNull(GuestPackageReader(context).readApplicationInfo(revision.apkPath).className)
        val target = if (throwing) "com.example.appsandbox.testguest.runtime.Exp003OnCreateThrowingApplication" else declared
        val sha = requireNotNull(revision.sha256)
        val request = GuestApplicationSessionRequest("run-${System.nanoTime()}", "start-${System.nanoTime()}", instance.instanceId, revision.revisionId, sha, revision.packageName, target, instance.dataRoot)
        val expected = GuestApplicationSessionExpected(instance.instanceId, revision.revisionId, sha, revision.packageName, target, instance.dataRoot, File(context.filesDir, "guest-instances").path)
        val snapshot = runCatching { controller(context).start(request, expected, C1GuestApplicationSessionExecutor.create(context, instance, revision, target)) }.getOrElse { return Act007SessionResult("REJECTED", "CONSTRUCTION_FAILED:${it.javaClass.simpleName}", instanceId) }
        return Act007SessionResult(snapshot.state.name, snapshot.failure.name, instanceId, snapshot.constructorCompleted, snapshot.onCreateAttempted, snapshot.onCreateCompleted, "DexClassLoader", instance.dataRoot)
    }
    fun stop(context: Context, instanceId: String): Act007SessionResult {
        val current = controller(context).snapshots().firstOrNull { it.request.instanceId == instanceId && it.state == GuestApplicationSessionState.RUNNING } ?: return Act007SessionResult("REJECTED", "NOT_RUNNING", instanceId)
        val stopped = controller(context).stop(current.request.runId, "stop-${System.nanoTime()}")
        return Act007SessionResult(stopped.state.name, stopped.failure.name, instanceId)
    }
    fun status(context: Context, instanceId: String) = if (controller(context).snapshots().any { it.request.instanceId == instanceId && it.state == GuestApplicationSessionState.RUNNING }) "RUNNING" else "STOPPED"
}
