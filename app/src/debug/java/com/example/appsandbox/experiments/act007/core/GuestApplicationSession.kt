package com.example.appsandbox.experiments.act007.core

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.appsandbox.experiments.exp003c1.Exp003c1ControlledContext
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import dalvik.system.DexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class GuestApplicationSessionState { NEW, STARTING, RUNNING, STOPPING, STOPPED, FAILED }
enum class GuestApplicationFailure { NONE, INVALID_INPUT, STALE_INSTANCE, STALE_REVISION, ARTIFACT_MISMATCH, PACKAGE_MISMATCH, APPLICATION_CLASS_MISMATCH, NON_APPLICATION_CLASS, WRONG_CLASSLOADER, DATA_ROOT_MISMATCH, PATH_ESCAPE, DUPLICATE_OPERATION, DUPLICATE_RUN, CONCURRENT_OPERATION, CONSTRUCTION_FAILED, ON_CREATE_FAILED, CRASH_RECOVERY, INVALID_TRANSITION }

data class GuestApplicationSessionRequest(
    val runId: String, val operationId: String, val instanceId: String, val revisionId: String,
    val artifactSha256: String, val packageName: String, val applicationClass: String,
    val dataRoot: String
)

data class GuestApplicationSessionExpected(
    val instanceId: String, val revisionId: String, val artifactSha256: String,
    val packageName: String, val applicationClass: String, val dataRoot: String,
    val allowedDataRoot: String
)

data class GuestApplicationSessionSnapshot(
    val request: GuestApplicationSessionRequest,
    val state: GuestApplicationSessionState,
    val failure: GuestApplicationFailure = GuestApplicationFailure.NONE,
    val constructorAttempted: Int = 0,
    val constructorCompleted: Int = 0,
    val onCreateAttempted: Int = 0,
    val onCreateCompleted: Int = 0,
    val updatedAt: Long,
    val detail: String? = null,
    val lastOperationId: String? = null
)

interface GuestApplicationSessionExecutor {
    fun construct(request: GuestApplicationSessionRequest): Any
    fun callOnCreate(application: Any, request: GuestApplicationSessionRequest)
    fun close(application: Any?) = Unit
}

interface GuestApplicationSessionRegistryIo {
    fun write(path: File, text: String)
    fun copy(from: File, to: File, overwrite: Boolean): Boolean
    fun delete(path: File): Boolean
    fun publish(from: File, to: File): Boolean
}

private object DefaultGuestApplicationSessionRegistryIo : GuestApplicationSessionRegistryIo {
    override fun write(path: File, text: String) = path.writeText(text)
    override fun copy(from: File, to: File, overwrite: Boolean) = from.copyTo(to, overwrite).let { true }
    override fun delete(path: File) = !path.exists() || path.delete()
    override fun publish(from: File, to: File) = from.renameTo(to)
}

class GuestApplicationSessionRegistry(private val file: File, private val io: GuestApplicationSessionRegistryIo = DefaultGuestApplicationSessionRegistryIo) {
    internal val namespace: String get() = file.absoluteFile.normalize().path
    private val lock = locks.computeIfAbsent(file.absoluteFile.normalize().path) { Any() }

    fun readAll(): List<GuestApplicationSessionSnapshot> = synchronized(lock) {
        recoverInterruptedWrite()
        if (!file.exists()) return@synchronized emptyList()
        decode(file.readText())
    }

    fun update(block: (List<GuestApplicationSessionSnapshot>) -> List<GuestApplicationSessionSnapshot>) = synchronized(lock) {
        write(block(readAll()))
    }

    private fun write(values: List<GuestApplicationSessionSnapshot>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        val backup = File(file.parentFile, file.name + ".bak")
        try {
            io.write(temp, encode(values))
            if (file.exists()) check(io.copy(file, backup, true)) { "cannot backup session registry" }
            if (file.exists()) check(io.delete(file)) { "cannot remove old session registry" }
            check(io.publish(temp, file)) { "cannot publish session registry" }
            io.delete(backup)
        } catch (error: Throwable) {
            if (!file.exists() && backup.exists()) io.copy(backup, file, true)
            throw error
        } finally { io.delete(temp) }
    }

    private fun recoverInterruptedWrite() {
        val backup = File(file.parentFile, file.name + ".bak")
        if (!file.exists() && backup.exists()) check(io.copy(backup, file, true))
        if (file.exists()) {
            runCatching { decode(file.readText()) }.onSuccess { io.delete(backup) }
                .onFailure { if (backup.exists()) { io.delete(file); io.copy(backup, file, true); decode(file.readText()); io.delete(backup) } }
        }
        io.delete(File(file.parentFile, file.name + ".tmp"))
    }

    private fun encode(values: List<GuestApplicationSessionSnapshot>) = JSONArray(values.map { value ->
        JSONObject().put("runId", value.request.runId).put("operationId", value.request.operationId)
            .put("instanceId", value.request.instanceId).put("revisionId", value.request.revisionId)
            .put("artifactSha256", value.request.artifactSha256).put("packageName", value.request.packageName)
            .put("applicationClass", value.request.applicationClass).put("dataRoot", value.request.dataRoot)
            .put("state", value.state.name).put("failure", value.failure.name)
            .put("constructorAttempted", value.constructorAttempted).put("constructorCompleted", value.constructorCompleted)
            .put("onCreateAttempted", value.onCreateAttempted).put("onCreateCompleted", value.onCreateCompleted)
            .put("updatedAt", value.updatedAt).put("detail", value.detail).put("lastOperationId", value.lastOperationId)
    }).toString()

    private fun decode(raw: String): List<GuestApplicationSessionSnapshot> = try {
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            val request = GuestApplicationSessionRequest(o.getString("runId"), o.getString("operationId"),
                o.getString("instanceId"), o.getString("revisionId"), o.getString("artifactSha256"),
                o.getString("packageName"), o.getString("applicationClass"), o.getString("dataRoot"))
            GuestApplicationSessionSnapshot(request, GuestApplicationSessionState.valueOf(o.getString("state")),
                GuestApplicationFailure.valueOf(o.getString("failure")), o.getInt("constructorAttempted"),
                o.getInt("constructorCompleted"), o.getInt("onCreateAttempted"), o.getInt("onCreateCompleted"),
                o.getLong("updatedAt"), o.optString("detail").takeIf { it.isNotEmpty() }, o.optString("lastOperationId").takeIf { it.isNotEmpty() })
        }.also { values ->
            require(values.map { it.request.runId }.distinct().size == values.size)
            require(values.all(::validSnapshot))
        }
    } catch (error: Throwable) { throw IllegalStateException("corrupt Guest Application session registry", error) }

    private fun validSnapshot(s: GuestApplicationSessionSnapshot): Boolean {
        val counts = listOf(s.constructorAttempted, s.constructorCompleted, s.onCreateAttempted, s.onCreateCompleted)
        if (counts.any { it !in 0..1 } || s.constructorCompleted > s.constructorAttempted || s.onCreateCompleted > s.onCreateAttempted) return false
        return when (s.state) {
            GuestApplicationSessionState.NEW -> counts.all { it == 0 } && s.failure == GuestApplicationFailure.NONE
            GuestApplicationSessionState.STARTING -> counts == listOf(1, 0, 0, 0) || counts == listOf(1, 1, 1, 0)
            GuestApplicationSessionState.RUNNING -> counts == listOf(1, 1, 1, 1) && s.failure == GuestApplicationFailure.NONE
            GuestApplicationSessionState.STOPPING, GuestApplicationSessionState.STOPPED -> counts == listOf(1, 1, 1, 1)
            GuestApplicationSessionState.FAILED -> s.failure != GuestApplicationFailure.NONE
        }
    }

    companion object { private val locks = ConcurrentHashMap<String, Any>() }
}

class GuestApplicationSessionController(
    private val registry: GuestApplicationSessionRegistry,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val live get() = liveSessions
    private val namespace get() = registry.namespace

    fun start(request: GuestApplicationSessionRequest, expected: GuestApplicationSessionExpected, executor: GuestApplicationSessionExecutor): GuestApplicationSessionSnapshot {
        validate(request, expected)?.let { return rejected(request, it) }
        var reserved: GuestApplicationSessionSnapshot? = null
        var claimed = false
        try { registry.update { all ->
            val sameOperation = all.firstOrNull { it.request.operationId == request.operationId }
            if (sameOperation != null) {
                reserved = if (sameOperation.request == request) sameOperation else rejected(request, GuestApplicationFailure.DUPLICATE_OPERATION)
                return@update all
            }
            if (all.any { it.request.runId == request.runId }) { reserved = rejected(request, GuestApplicationFailure.DUPLICATE_RUN); return@update all }
            if (all.any { it.lastOperationId == request.operationId }) { reserved = rejected(request, GuestApplicationFailure.DUPLICATE_OPERATION); return@update all }
            if (all.any { it.request.instanceId == request.instanceId && it.state in setOf(GuestApplicationSessionState.NEW, GuestApplicationSessionState.STARTING, GuestApplicationSessionState.RUNNING, GuestApplicationSessionState.STOPPING) }) {
                reserved = rejected(request, GuestApplicationFailure.CONCURRENT_OPERATION); return@update all
            }
            reserved = GuestApplicationSessionSnapshot(request, GuestApplicationSessionState.STARTING,
                constructorAttempted = 1, updatedAt = now())
            inFlight[sessionKey(request.runId)] = true
            claimed = true
            all + reserved!!
        } } catch (error: Throwable) {
            inFlight.remove(sessionKey(request.runId))
            throw error
        }
        val initial = requireNotNull(reserved)
        if (!claimed) return initial
        val starting = initial
        val application = try { executor.construct(request) } catch (error: Throwable) {
            inFlight.remove(sessionKey(request.runId))
            return finishFailure(starting, GuestApplicationFailure.CONSTRUCTION_FAILED, error)
        }
        try { update(starting.copy(constructorCompleted = 1, onCreateAttempted = 1, updatedAt = now())) } catch (error: Throwable) {
            inFlight.remove(sessionKey(request.runId)); runCatching { executor.close(application) }
            return finishFailure(starting, GuestApplicationFailure.INVALID_TRANSITION, error)
        }
        return try {
            executor.callOnCreate(application, request)
            val running = starting.copy(state = GuestApplicationSessionState.RUNNING, constructorCompleted = 1,
                onCreateAttempted = 1, onCreateCompleted = 1, updatedAt = now())
            live[sessionKey(request.runId)] = executor to application
            try { update(running); inFlight.remove(sessionKey(request.runId)); running } catch (error: Throwable) {
                live.remove(sessionKey(request.runId)); inFlight.remove(sessionKey(request.runId))
                runCatching { executor.close(application) }
                finishFailure(starting.copy(constructorCompleted = 1, onCreateAttempted = 1), GuestApplicationFailure.INVALID_TRANSITION, error)
            }
        } catch (error: Throwable) {
            inFlight.remove(sessionKey(request.runId)); runCatching { executor.close(application) }
            finishFailure(starting.copy(constructorCompleted = 1, onCreateAttempted = 1), GuestApplicationFailure.ON_CREATE_FAILED, error)
        }
    }

    fun stop(runId: String, operationId: String): GuestApplicationSessionSnapshot {
        if (operationId.isBlank()) return rejected(emptyRequest(runId, operationId), GuestApplicationFailure.INVALID_INPUT)
        var stopping: GuestApplicationSessionSnapshot? = null
        var rejected: GuestApplicationSessionSnapshot? = null
        registry.update { all ->
            val current = all.firstOrNull { it.request.runId == runId }
            if (current == null) { rejected = rejected(emptyRequest(runId, operationId), GuestApplicationFailure.INVALID_INPUT); return@update all }
            if (current.state == GuestApplicationSessionState.STOPPED && current.lastOperationId == operationId) { stopping = current; return@update all }
            if (all.any { it.request.operationId == operationId && it.request.runId != runId } ||
                all.any { it.lastOperationId == operationId && it.request.runId != runId }) {
                rejected = rejected(current.request, GuestApplicationFailure.DUPLICATE_OPERATION); return@update all
            }
            if (current.state != GuestApplicationSessionState.RUNNING) { rejected = rejected(current.request.copy(operationId = operationId), GuestApplicationFailure.INVALID_TRANSITION); return@update all }
            if (current.lastOperationId != null && current.lastOperationId != operationId) { rejected = rejected(current.request, GuestApplicationFailure.DUPLICATE_OPERATION); return@update all }
            stopping = current.copy(state = GuestApplicationSessionState.STOPPING, updatedAt = now(), lastOperationId = operationId)
            inFlight[sessionKey(runId)] = true
            all.map { if (it.request.runId == runId) stopping!! else it }
        }
        rejected?.let { return it }
        val reserved = requireNotNull(stopping)
        if (reserved.state == GuestApplicationSessionState.STOPPED) return reserved
        val handle = live.remove(sessionKey(runId))
        if (handle == null) { inFlight.remove(sessionKey(runId)); return finishFailure(reserved, GuestApplicationFailure.CRASH_RECOVERY, IllegalStateException("live session unavailable")) }
        return try {
            handle.first.close(handle.second)
            reserved.copy(state = GuestApplicationSessionState.STOPPED, updatedAt = now()).also { stopped -> update(stopped); inFlight.remove(sessionKey(runId)) }
        } catch (error: Throwable) { inFlight.remove(sessionKey(runId)); finishFailure(reserved, GuestApplicationFailure.INVALID_TRANSITION, error) }
    }

    fun recoverInterrupted(): List<GuestApplicationSessionSnapshot> {
        val recovered = mutableListOf<GuestApplicationSessionSnapshot>()
        registry.update { all -> all.map { snapshot ->
            if (!inFlight.containsKey(sessionKey(snapshot.request.runId)) &&
                (snapshot.state in setOf(GuestApplicationSessionState.NEW, GuestApplicationSessionState.STARTING, GuestApplicationSessionState.STOPPING) ||
                (snapshot.state == GuestApplicationSessionState.RUNNING && !live.containsKey(sessionKey(snapshot.request.runId))))) {
                snapshot.copy(state = GuestApplicationSessionState.FAILED, failure = GuestApplicationFailure.CRASH_RECOVERY,
                    updatedAt = now()).also(recovered::add)
            } else snapshot
        } }
        return recovered
    }

    fun snapshots(): List<GuestApplicationSessionSnapshot> = registry.readAll()

    private fun validate(r: GuestApplicationSessionRequest, e: GuestApplicationSessionExpected): GuestApplicationFailure? {
        if (listOf(r.runId, r.operationId, r.instanceId, r.revisionId, r.artifactSha256, r.packageName, r.applicationClass, r.dataRoot).any(String::isBlank)) return GuestApplicationFailure.INVALID_INPUT
        if (r.instanceId != e.instanceId) return GuestApplicationFailure.STALE_INSTANCE
        if (r.revisionId != e.revisionId) return GuestApplicationFailure.STALE_REVISION
        if (!r.artifactSha256.equals(e.artifactSha256, true)) return GuestApplicationFailure.ARTIFACT_MISMATCH
        if (r.packageName != e.packageName) return GuestApplicationFailure.PACKAGE_MISMATCH
        if (r.applicationClass != e.applicationClass) return GuestApplicationFailure.APPLICATION_CLASS_MISMATCH
        val root = runCatching { File(r.dataRoot).canonicalFile }.getOrElse { return GuestApplicationFailure.DATA_ROOT_MISMATCH }
        val expectedRoot = runCatching { File(e.dataRoot).canonicalFile }.getOrElse { return GuestApplicationFailure.DATA_ROOT_MISMATCH }
        val allowed = runCatching { File(e.allowedDataRoot).canonicalFile }.getOrElse { return GuestApplicationFailure.PATH_ESCAPE }
        if (root != expectedRoot) return GuestApplicationFailure.DATA_ROOT_MISMATCH
        if (root.parentFile != allowed || hasSymlinkInPath(File(e.allowedDataRoot), File(r.dataRoot))) return GuestApplicationFailure.PATH_ESCAPE
        return null
    }

    private fun finishFailure(base: GuestApplicationSessionSnapshot, failure: GuestApplicationFailure, error: Throwable) =
        base.copy(state = GuestApplicationSessionState.FAILED, failure = failure, updatedAt = now(),
            detail = error.javaClass.name + ":" + error.message).also(::update)
    private fun update(snapshot: GuestApplicationSessionSnapshot) = registry.update { all -> all.map { if (it.request.runId == snapshot.request.runId) snapshot else it } }
    private fun rejected(request: GuestApplicationSessionRequest, failure: GuestApplicationFailure) = GuestApplicationSessionSnapshot(request, GuestApplicationSessionState.FAILED, failure, updatedAt = now())
    private fun emptyRequest(runId: String, operationId: String) = GuestApplicationSessionRequest(runId, operationId, "", "", "", "", "", "")
    private fun sessionKey(runId: String) = "$namespace::$runId"

    private fun hasSymlinkInPath(base: File, target: File): Boolean {
        var current = target.absoluteFile
        val basePath = base.absoluteFile.toPath()
        while (current.toPath().startsWith(basePath)) {
            if (java.nio.file.Files.isSymbolicLink(current.toPath())) return true
            if (current == base.absoluteFile) return false
            current = current.parentFile ?: return true
        }
        return true
    }

    companion object {
        private val liveSessions = ConcurrentHashMap<String, Pair<GuestApplicationSessionExecutor, Any?>>()
        private val inFlight = ConcurrentHashMap<String, Boolean>()
        internal fun clearLiveForTest() { liveSessions.clear() }
    }
}

class C1GuestApplicationSessionExecutor private constructor(
    private val instrumentation: Instrumentation,
    private val context: Exp003c1ControlledContext,
    private val loader: DexClassLoader,
    private val applicationClass: Class<out Application>
) : GuestApplicationSessionExecutor {
    private var lastApplication: Application? = null
    override fun construct(request: GuestApplicationSessionRequest): Any {
        check(applicationClass.classLoader === loader) { GuestApplicationFailure.WRONG_CLASSLOADER.name }
        return instrumentation.newApplication(loader, applicationClass.name, context).also { context.bindApplication(it); lastApplication = it }
    }
    override fun callOnCreate(application: Any, request: GuestApplicationSessionRequest) = instrumentation.callApplicationOnCreate(application as Application)

    fun observe(): Map<String, String> = lastApplication?.let { app ->
        runCatching { app.javaClass.getMethod("observe").invoke(app) as Map<String, String> }.getOrDefault(emptyMap())
    } ?: emptyMap()
    fun onCreateResults(): Map<String, String> = lastApplication?.let { app ->
        runCatching { app.javaClass.getMethod("onCreateResults").invoke(app) as Map<String, String> }.getOrDefault(emptyMap())
    } ?: emptyMap()
    fun persistedResults(): Map<String, String> = lastApplication?.let { app ->
        runCatching { app.javaClass.getMethod("persistedResults").invoke(app) as Map<String, String> }.getOrDefault(emptyMap())
    } ?: emptyMap()

    companion object {
        fun create(host: Context, instance: GuestInstanceRecord, revision: GuestPackageRecord, applicationClassName: String): C1GuestApplicationSessionExecutor {
            check(instance.guestRevisionId == revision.revisionId && instance.guestPackageName == revision.packageName)
            check(instance.guestApkPath == revision.apkPath && instance.guestSha256.equals(revision.sha256, true))
            check(GuestArtifactVerifier.verify(revision).state == ArtifactState.VALID)
            val dataRoot = File(instance.dataRoot).canonicalFile
            check(dataRoot.isDirectory && dataRoot.parentFile == File(host.filesDir, "guest-instances").canonicalFile)
            val loader = DexClassLoader(revision.apkPath, host.codeCacheDir.path, null, host.classLoader)
            val info = archiveInfo(host, revision).apply { dataDir = dataRoot.path }
            check(info.packageName == revision.packageName && info.className == applicationClassName)
            val raw = Class.forName(applicationClassName, false, loader)
            check(raw.classLoader === loader) { GuestApplicationFailure.WRONG_CLASSLOADER.name }
            check(Application::class.java.isAssignableFrom(raw)) { GuestApplicationFailure.NON_APPLICATION_CLASS.name }
            @Suppress("UNCHECKED_CAST") val type = raw as Class<out Application>
            val context = Exp003c1ControlledContext(host.applicationContext, loader,
                host.packageManager.getResourcesForApplication(info), info, dataRoot, instance.instanceId)
            return C1GuestApplicationSessionExecutor(Instrumentation(), context, loader, type)
        }

        @Suppress("DEPRECATION")
        private fun archiveInfo(host: Context, revision: GuestPackageRecord) =
            ApplicationInfo(requireNotNull(host.packageManager.getPackageArchiveInfo(revision.apkPath, 0)?.applicationInfo)).apply {
                sourceDir = revision.apkPath; publicSourceDir = revision.apkPath
            }
    }
}
