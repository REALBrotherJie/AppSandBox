package com.example.appsandbox.experiments.act007.api36

import android.content.Context
import com.example.appsandbox.experiments.act007.core.*
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.packageinfo.GuestPackageReader
import java.io.File

data class Act007SessionResult(val outcome: String, val constructor: Int = 0, val onCreate: Int = 0, val loader: String = "", val error: String = "")

object Act007ApplicationSessions {
    private fun controller(host: Context) = GuestApplicationSessionController(GuestApplicationSessionRegistry(File(host.filesDir, "act007-sessions.json")))
    fun start(host: Context, instance: GuestInstanceRecord, revision: GuestPackageRecord, className: String? = null): Act007SessionResult {
        require(android.os.Build.VERSION.SDK_INT == 36) { "API36 required" }
        val declared = requireNotNull(GuestPackageReader(host).readApplicationInfo(revision.apkPath).className)
        val target = className ?: declared
        val sha = requireNotNull(revision.sha256)
        val request = GuestApplicationSessionRequest("run-${System.nanoTime()}", "start-${System.nanoTime()}", instance.instanceId, revision.revisionId, sha, revision.packageName, target, instance.dataRoot)
        val expected = GuestApplicationSessionExpected(instance.instanceId, revision.revisionId, sha, revision.packageName, target, instance.dataRoot, File(host.filesDir, "guest-instances").path)
        val snapshot = runCatching { controller(host).start(request, expected, C1GuestApplicationSessionExecutor.create(host, instance, revision, target)) }.getOrElse { return Act007SessionResult("FAILED", error = it.javaClass.name + ":" + it.message) }
        return Act007SessionResult(snapshot.state.name, snapshot.constructorCompleted, snapshot.onCreateCompleted, "DexClassLoader", snapshot.detail ?: "")
    }
    fun stop(host: Context, instance: GuestInstanceRecord): Boolean = controller(host).snapshots().firstOrNull { it.request.instanceId == instance.instanceId && it.state == GuestApplicationSessionState.RUNNING }?.let { controller(host).stop(it.request.runId, "stop-${System.nanoTime()}"); true } ?: false
    fun isActive(host: Context, instanceId: String) = controller(host).snapshots().any { it.request.instanceId == instanceId && it.state == GuestApplicationSessionState.RUNNING }
}
