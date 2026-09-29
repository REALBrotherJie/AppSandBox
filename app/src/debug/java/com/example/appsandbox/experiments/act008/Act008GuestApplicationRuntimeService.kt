package com.example.appsandbox.experiments.act008

import android.app.Service
import android.content.Intent
import android.os.*
import com.example.appsandbox.experiments.act007.core.*
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File
import java.util.LinkedHashMap

class Act008GuestApplicationRuntimeService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private lateinit var coordinator: Act008RuntimeCoordinator

    override fun onCreate() {
        super.onCreate()
        coordinator = Act008RuntimeCoordinator(this)
        coordinator.recoverAfterRuntimeStart()
        thread = HandlerThread("act008-runtime").apply { start() }
        messenger = Messenger(IncomingHandler(thread.looper))
    }

    override fun onBind(intent: Intent?) = messenger.binder
    override fun onDestroy() { thread.quitSafely(); super.onDestroy() }

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(message: Message) {
            val data = message.data ?: Bundle.EMPTY
            val requestId = data.getString(Act008SessionProtocol.KEY_REQUEST_ID).orEmpty()
            val invalid = Act008SessionProtocol.validateRequest(message.what, data)
            val snapshot = if (invalid != null) coordinator.rejected(data, invalid) else coordinator.handle(message.what, data)
            runCatching { message.replyTo?.send(Message.obtain(null, Act008SessionProtocol.MSG_REPLY).apply { this.data = Act008SessionProtocol.encode(snapshot) }) }
            if (message.what == Act008SessionProtocol.MSG_TERMINATE_RUNTIME && invalid == null) {
                postDelayed({ Process.killProcess(Process.myPid()) }, 100L)
            }
        }
    }
}

internal class Act008RuntimeCoordinator(private val service: Service) {
    private val registry = GuestApplicationSessionRegistry(File(service.filesDir, "act007-application-sessions.json"))
    private val controller = GuestApplicationSessionController(registry)
    private val replies = object : LinkedHashMap<String, Act008SessionSnapshot>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Act008SessionSnapshot>?) = size > 128
    }
    private val requestLedger = Act008RequestLedger()

    @Synchronized fun handle(message: Int, data: Bundle): Act008SessionSnapshot {
        val requestId = data.getString(Act008SessionProtocol.KEY_REQUEST_ID).orEmpty()
        val ledger = requestLedger.check(requestId, data.getString(Act008SessionProtocol.KEY_OPERATION_ID).orEmpty(), data.getString(Act008SessionProtocol.KEY_RUN_ID).orEmpty(), data.getString(Act008SessionProtocol.KEY_INSTANCE_ID).orEmpty())
        if (ledger == false) return base(data, Act008SessionState.FAILED, Act008Failure.DUPLICATE_REQUEST, "requestId already used")
        if (ledger == true) replies[requestId]?.let { return it }
        val result = when (message) {
            Act008SessionProtocol.MSG_START -> start(data)
            Act008SessionProtocol.MSG_READ -> read(data)
            Act008SessionProtocol.MSG_STOP -> stop(data)
            Act008SessionProtocol.MSG_RESTART -> restart(data)
            Act008SessionProtocol.MSG_DELETE -> delete(data)
            Act008SessionProtocol.MSG_TERMINATE_RUNTIME -> base(data, Act008SessionState.FAILED, Act008Failure.RUNTIME_UNAVAILABLE, "runtime self-termination requested")
            else -> rejected(data, Act008Failure.INVALID_REQUEST)
        }
        replies[requestId] = result
        return result
    }

    @Synchronized fun recoverAfterRuntimeStart() { runCatching { controller.recoverInterrupted() } }

    fun rejected(data: Bundle, failure: Act008Failure) = base(data, Act008SessionState.FAILED, failure, failure.name)

    private fun start(data: Bundle): Act008SessionSnapshot = runCatching {
        val instanceId = data.id(Act008SessionProtocol.KEY_INSTANCE_ID)
        val instance = requireNotNull(GuestInstanceStore(service).get(instanceId)) { "missing instance" }
        val revision = requireNotNull(GuestStore(service).findRevision(instance.guestRevisionId)) { "missing revision" }
        @Suppress("DEPRECATION")
        val appClass = requireNotNull(service.packageManager.getPackageArchiveInfo(revision.apkPath, 0)?.applicationInfo?.className)
        val request = GuestApplicationSessionRequest(
            data.id(Act008SessionProtocol.KEY_RUN_ID), data.id(Act008SessionProtocol.KEY_OPERATION_ID), instance.instanceId,
            revision.revisionId, requireNotNull(revision.sha256), revision.packageName, appClass, instance.dataRoot
        )
        val expected = GuestApplicationSessionExpected(instance.instanceId, revision.revisionId, requireNotNull(revision.sha256),
            revision.packageName, appClass, instance.dataRoot, File(service.filesDir, "guest-instances").canonicalPath)
        from(data, controller.start(request, expected, C1GuestApplicationSessionExecutor.create(service, instance, revision, appClass)))
    }.getOrElse { failure(data, it) }

    private fun read(data: Bundle): Act008SessionSnapshot {
        val instanceId = data.id(Act008SessionProtocol.KEY_INSTANCE_ID)
        return runCatching {
            val snapshots = controller.snapshots()
            val runId = data.getString(Act008SessionProtocol.KEY_RUN_ID).orEmpty()
            if (runId.isNotBlank()) snapshots.lastOrNull { it.request.instanceId == instanceId && it.request.runId == runId }
            else snapshots.lastOrNull { it.request.instanceId == instanceId }
        }
            .fold({ it?.let { snapshot -> from(data, snapshot) } ?: base(data, Act008SessionState.NEW) }, { failure(data, it) })
    }

    private fun stop(data: Bundle): Act008SessionSnapshot = runCatching {
        from(data, controller.stop(data.id(Act008SessionProtocol.KEY_RUN_ID), data.id(Act008SessionProtocol.KEY_OPERATION_ID)))
    }.getOrElse { failure(data, it) }

    private fun restart(data: Bundle): Act008SessionSnapshot {
        val instanceId = data.id(Act008SessionProtocol.KEY_INSTANCE_ID)
        val active = runCatching { controller.snapshots().lastOrNull { it.request.instanceId == instanceId && it.state == GuestApplicationSessionState.RUNNING } }.getOrNull()
        if (active != null) {
            val stop = controller.stop(active.request.runId, data.id(Act008SessionProtocol.KEY_OPERATION_ID) + ":stop")
            if (stop.state != GuestApplicationSessionState.STOPPED) return from(data, stop)
        }
        return start(data)
    }

    private fun delete(data: Bundle): Act008SessionSnapshot = runCatching {
        val instanceId = data.id(Act008SessionProtocol.KEY_INSTANCE_ID)
        val active = controller.snapshots().any { it.request.instanceId == instanceId && it.state in setOf(GuestApplicationSessionState.NEW, GuestApplicationSessionState.STARTING, GuestApplicationSessionState.RUNNING, GuestApplicationSessionState.STOPPING) }
        if (active) return base(data, Act008SessionState.FAILED, Act008Failure.DELETE_ACTIVE, "active session")
        check(GuestInstanceStore(service).delete(instanceId)) { "instance not deleted" }
        base(data, Act008SessionState.STOPPED)
    }.getOrElse { failure(data, it) }

    private fun from(data: Bundle, snapshot: GuestApplicationSessionSnapshot) = Act008SessionSnapshot(
        data.getString(Act008SessionProtocol.KEY_REQUEST_ID).orEmpty(), data.getString(Act008SessionProtocol.KEY_OPERATION_ID).orEmpty(),
        snapshot.request.runId, snapshot.request.instanceId, Act008SessionState.valueOf(snapshot.state.name), map(snapshot.failure),
        snapshot.detail.orEmpty(), Process.myPid(), snapshot.updatedAt
    )

    private fun base(data: Bundle, state: Act008SessionState, failure: Act008Failure = Act008Failure.NONE, detail: String = "") =
        Act008SessionSnapshot(data.getString(Act008SessionProtocol.KEY_REQUEST_ID).orEmpty(), data.getString(Act008SessionProtocol.KEY_OPERATION_ID).orEmpty(),
            data.getString(Act008SessionProtocol.KEY_RUN_ID).orEmpty(), data.getString(Act008SessionProtocol.KEY_INSTANCE_ID).orEmpty(), state, failure,
            detail, Process.myPid(), System.currentTimeMillis())

    private fun failure(data: Bundle, error: Throwable): Act008SessionSnapshot {
        val failure = when {
            error.message?.contains("construct", true) == true -> Act008Failure.CONSTRUCTION_FAILED
            error.message?.contains("onCreate", true) == true -> Act008Failure.ON_CREATE_FAILED
            error is IllegalStateException && error.message?.contains("registry", true) == true -> Act008Failure.REGISTRY_CORRUPT
            else -> Act008Failure.BINDING_MISMATCH
        }
        return base(data, Act008SessionState.FAILED, failure, error.javaClass.name + ":" + error.message)
    }

    private fun map(failure: GuestApplicationFailure) = when (failure) {
        GuestApplicationFailure.NONE -> Act008Failure.NONE
        GuestApplicationFailure.DUPLICATE_OPERATION, GuestApplicationFailure.DUPLICATE_RUN -> Act008Failure.DUPLICATE_REQUEST
        GuestApplicationFailure.CONCURRENT_OPERATION -> Act008Failure.CONCURRENT_OPERATION
        GuestApplicationFailure.CONSTRUCTION_FAILED -> Act008Failure.CONSTRUCTION_FAILED
        GuestApplicationFailure.ON_CREATE_FAILED -> Act008Failure.ON_CREATE_FAILED
        GuestApplicationFailure.CRASH_RECOVERY -> Act008Failure.CRASH_RECOVERY
        GuestApplicationFailure.INVALID_TRANSITION -> Act008Failure.STALE_RUN
        else -> Act008Failure.BINDING_MISMATCH
    }

    private fun Bundle.id(key: String) = requireNotNull(getString(key)).also { require(it.isNotBlank()) }
}
