package com.example.appsandbox.runtime.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.example.appsandbox.BuildConfig
import com.example.appsandbox.experiments.act008.Act008Failure
import com.example.appsandbox.experiments.act008.Act008SessionProtocol
import com.example.appsandbox.experiments.act008.Act008SessionSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Act008SessionClient(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, (Result<Act008SessionSnapshot>) -> Unit>()
    private val queued = LinkedHashMap<String, () -> Unit>()
    private var service: Messenger? = null
    // A successful bind owns a registration even before connection, or after disconnection.
    private var bound = false
    @Volatile private var closed = false
    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (closed) return
            val snapshot = Act008SessionProtocol.decode(message.data ?: Bundle.EMPTY)
            val requestId = snapshot?.requestId ?: return
            pending.remove(requestId)?.invoke(
                snapshot.takeIf { it.failure == Act008Failure.NONE }
                    ?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException(snapshot?.detail ?: "runtime request failed"))
            )
        }
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed || !bound) return
            service = Messenger(binder)
            val requests = queued.values.toList()
            queued.clear()
            requests.forEach { it.invoke() }
        }
        override fun onServiceDisconnected(name: ComponentName) = failPending("runtime disconnected")
        override fun onBindingDied(name: ComponentName) {
            releaseBinding()
            failPending("runtime binding died")
        }
        override fun onNullBinding(name: ComponentName) {
            releaseBinding()
            failPending("runtime unavailable")
        }
    }

    fun start(instanceId: String, operationId: String, runId: String, callback: (Result<Act008SessionSnapshot>) -> Unit) =
        send(Act008SessionProtocol.MSG_START, instanceId, operationId, runId) { result ->
            result.onSuccess { rememberRun(instanceId, it.runId) }
            callback(result)
        }
    fun read(instanceId: String, callback: (Result<Act008SessionSnapshot>) -> Unit) =
        read(instanceId, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(runKey(instanceId), null), callback)
    fun read(instanceId: String, runId: String?, callback: (Result<Act008SessionSnapshot>) -> Unit) {
        if (runId.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("no persisted session run")))
        } else send(Act008SessionProtocol.MSG_READ, instanceId, null, runId, callback)
    }
    fun stop(instanceId: String, operationId: String, runId: String, callback: (Result<Act008SessionSnapshot>) -> Unit) =
        send(Act008SessionProtocol.MSG_STOP, instanceId, operationId, runId, callback)
    fun restart(instanceId: String, operationId: String, runId: String, callback: (Result<Act008SessionSnapshot>) -> Unit) =
        send(Act008SessionProtocol.MSG_RESTART, instanceId, operationId, runId) { result ->
            result.onSuccess { rememberRun(instanceId, it.runId) }
            callback(result)
        }
    fun delete(instanceId: String, callback: (Result<Act008SessionSnapshot>) -> Unit) =
        send(Act008SessionProtocol.MSG_DELETE, instanceId, null, null, callback)
    fun terminateRuntime(callback: (Result<Act008SessionSnapshot>) -> Unit) =
        send(Act008SessionProtocol.MSG_TERMINATE_RUNTIME, null, null, null, callback)

    fun close() {
        closed = true
        if (Looper.myLooper() == main.looper) closeOnMain() else main.post { closeOnMain() }
    }

    private fun closeOnMain() {
        releaseBinding()
        failPending("client closed")
    }

    private fun releaseBinding() {
        val registered = bound
        bound = false
        service = null
        if (registered) runCatching { context.unbindService(connection) }
    }

    private fun send(
        what: Int,
        instanceId: String?,
        operationId: String?,
        runId: String?,
        callback: (Result<Act008SessionSnapshot>) -> Unit
    ) {
        if (!BuildConfig.DEBUG) {
            callback(Result.failure(IllegalStateException("process-bound session unavailable in release")))
            return
        }
        main.post {
            if (closed) {
                callback(Result.failure(IllegalStateException("client closed")))
                return@post
            }
            val requestId = UUID.randomUUID().toString()
            pending[requestId] = callback
            main.postDelayed({
                queued.remove(requestId)
                pending.remove(requestId)?.invoke(Result.failure(IllegalStateException("runtime timeout")))
            }, Act008SessionProtocol.REQUEST_TIMEOUT_MS)
            val data = Bundle().apply {
                putInt(Act008SessionProtocol.KEY_VERSION, Act008SessionProtocol.VERSION)
                putString(Act008SessionProtocol.KEY_REQUEST_ID, requestId)
                instanceId?.let { putString(Act008SessionProtocol.KEY_INSTANCE_ID, it) }
                operationId?.let { putString(Act008SessionProtocol.KEY_OPERATION_ID, it) }
                runId?.let { putString(Act008SessionProtocol.KEY_RUN_ID, it) }
            }
            val sendNow: () -> Unit = {
                val target = service
                if (closed || target == null) {
                    pending.remove(requestId)?.invoke(Result.failure(IllegalStateException("runtime unavailable")))
                } else try {
                    target.send(Message.obtain(null, what).apply {
                        replyTo = replies
                        this.data = data
                    })
                } catch (_: RemoteException) {
                    pending.remove(requestId)?.invoke(Result.failure(IllegalStateException("runtime unavailable")))
                }
            }
            if (service == null) {
                queued[requestId] = sendNow
                if (!bound) {
                    val intent = Intent().setComponent(ComponentName(context.packageName, "com.example.appsandbox.experiments.act008.Act008GuestApplicationRuntimeService"))
                    bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
                    if (!bound) failPending("runtime bind failed")
                }
            } else sendNow.invoke()
        }
    }

    private fun failPending(message: String) {
        service = null
        queued.clear()
        val callbacks = pending.values.toList()
        pending.clear()
        callbacks.forEach { callback ->
            runCatching { callback(Result.failure(IllegalStateException(message))) }
        }
    }

    private fun rememberRun(instanceId: String, runId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(runKey(instanceId), runId).apply()
    }

    private fun runKey(instanceId: String) = "act008.run.$instanceId"

    private companion object {
        const val PREFS = "act008-session-client"
    }
}
