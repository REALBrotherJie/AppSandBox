package com.example.appsandbox.experiments.act006.api31

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.experiments.act006.core.Act006Expected
import com.example.appsandbox.experiments.act006.core.Act006InputSnapshot
import com.example.appsandbox.experiments.act006.core.Act006Phase
import com.example.appsandbox.experiments.act006.core.Act006StateMachine
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class Act006Runner : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId").orEmpty(); val caseId = intent.getStringExtra("caseId").orEmpty()
        val result = runCatching { runCase(runId, caseId) }.getOrElse { failure("INTERNAL_ERROR", it) }
        writeFinal(runId, caseId, result)
        if (result.outcome == "PROCESS_RECOVERY_REQUIRED") android.os.Process.killProcess(android.os.Process.myPid()) else finish()
    }

    private fun runCase(runId: String, caseId: String): Act006AttachResult {
        if (runId.isBlank() || caseId.isBlank()) return failure("INVALID_INPUT")
        if (launches.putIfAbsent(runId, caseId) != null) return failure("DUPLICATE_LAUNCH")
        val instance = GuestInstanceStore(this).get(intent.getStringExtra("instanceId").orEmpty()) ?: return failure("STALE_REVISION")
        val revision = GuestStore(this).findRevision(instance.guestRevisionId) ?: return failure("STALE_REVISION")
        if (caseId == "stale-revision" || instance.guestRevisionId != revision.revisionId || instance.guestPackageName != revision.packageName) return failure("STALE_REVISION")
        val actualSha = sha256(File(revision.apkPath))
        if (caseId == "artifact-mismatch" || revision.sha256 != actualSha || instance.guestSha256 != actualSha ||
            GuestArtifactVerifier.verify(revision).state != ArtifactState.VALID) return failure("ARTIFACT_MISMATCH")
        if (File(revision.apkPath).canWrite()) return failure("ARTIFACT_NOT_READ_ONLY")
        val declared = revision.components.singleOrNull { it.type == GuestComponentType.ACTIVITY } ?: return failure("COMPONENT_MISMATCH")
        val className = when (caseId) { "missing-class" -> declared.className + "Missing"; "non-activity" -> "com.example.appsandbox.testguest.runtime.GuestProbe"; else -> declared.className }
        val loader = DexClassLoader(revision.apkPath, codeCacheDir.path, null, javaClass.classLoader)
        val raw = try { Class.forName(className, false, loader) } catch (error: ClassNotFoundException) { return failure("MISSING_CLASS", error) }
        if (raw.classLoader !== loader) return failure("WRONG_CLASSLOADER")
        if (!Activity::class.java.isAssignableFrom(raw)) return failure("NON_ACTIVITY_CLASS")
        @Suppress("UNCHECKED_CAST") val guestClass = raw as Class<out Activity>
        val adapter = Act006Api31Adapter()
        val requestedFingerprint = if (caseId == "wrong-fingerprint") "wrong" else adapter.fingerprint
        val preparation = adapter.prepare(this, requestedFingerprint, caseId == "access-denied")
        if (preparation is Act006Api31Adapter.Preparation.Rejected) return preparation.result
        preparation as Act006Api31Adapter.Preparation.Ready
        val snapshot = Act006InputSnapshot(runId, "$runId-operation", instance.instanceId, revision.revisionId,
            actualSha, className, componentName.flattenToShortString(), requestedFingerprint)
        val expected = Act006Expected(instance.instanceId, revision.revisionId, actualSha, declared.className,
            componentName.flattenToShortString(), adapter.fingerprint)
        val executor = Act006Api31Executor(guestClass, preparation)
        val core = Act006StateMachine(runId, executor).execute(snapshot, expected)
        return Act006AttachResult(
            outcome = if (core.phase == Act006Phase.REJECTED) "REJECTED" else "PROCESS_RECOVERY_REQUIRED",
            reason = core.reason.name,
            classLoaded = true,
            constructorAttempted = core.counters.constructorAttempted == 1,
            constructed = core.counters.constructorCompleted == 1,
            attachExecutorAttempted = core.counters.attachAttempted == 1,
            attachInvokeAttempted = executor.hiddenInvokeAttempted.get(),
            attachCompleted = executor.hiddenInvokeCompleted.get(),
            lifecycle = core.counters.lifecycleAttempted != 0,
            exceptionType = if (core.error == null) "none" else "executor",
            detail = core.error ?: "none"
        )
    }

    private fun failure(reason: String, error: Throwable? = null) = Act006AttachResult("REJECTED", reason,
        exceptionType = error?.javaClass?.name ?: "none", detail = error?.message ?: "none")
    private fun writeFinal(runId: String, caseId: String, result: Act006AttachResult) {
        val destination = File(filesDir, "task51-$runId.result"); val temp = File(filesDir, destination.name + ".tmp")
        temp.writeText(listOf("status=FINAL", "runId=$runId", "caseId=$caseId", "api=${android.os.Build.VERSION.SDK_INT}",
            "outcome=${result.outcome}", "reason=${result.reason}", "adapterFingerprint=${Act006Api31Adapter().fingerprint}",
            "classLoaded=${result.classLoaded}", "constructorAttempted=${result.constructorAttempted}", "guestConstructed=${result.constructed}",
            "attachExecutorAttempted=${result.attachExecutorAttempted}", "attachInvokeAttempted=${result.attachInvokeAttempted}",
            "attachCompleted=${result.attachCompleted}", "guestLifecycle=${result.lifecycle}", "exceptionType=${result.exceptionType}",
            "detail=${result.detail}", "hostComponent=${componentName.flattenToShortString()}", "hostTaskId=$taskId").joinToString("\n", postfix = "\n"))
        check(temp.renameTo(destination))
    }
    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    companion object { private val launches = ConcurrentHashMap<String, String>() }
}
