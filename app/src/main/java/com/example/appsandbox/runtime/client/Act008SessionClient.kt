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
    private val queued = ArrayDeque<() -> Unit>()
    private var service: Messenger? = null
    private var bound = false
    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
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
            service = Messenger(binder)
            bound = true
            while (queued.isNotEmpty()) queued.removeFirst().invoke()
        }
        override fun onServiceDisconnected(name: ComponentName) = failPending("runtime disconnected")
        override fun onBindingDied(name: ComponentName) = failPending("runtime binding died")
        override fun onNullBinding(name: ComponentName) = failPending("runtime unavailable")
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
        failPending("client closed")
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
        queued.clear()
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
            val requestId = UUID.randomUUID().toString()
            pending[requestId] = callback
            val data = Bundle().apply {
                putInt(Act008SessionProtocol.KEY_VERSION, Act008SessionProtocol.VERSION)
                putString(Act008SessionProtocol.KEY_REQUEST_ID, requestId)
                instanceId?.let { putString(Act008SessionProtocol.KEY_INSTANCE_ID, it) }
                operationId?.let { putString(Act008SessionProtocol.KEY_OPERATION_ID, it) }
                runId?.let { putString(Act008SessionProtocol.KEY_RUN_ID, it) }
            }
            val sendNow: () -> Unit = {
                val target = service
                if (target == null) {
                    pending.remove(requestId)
                    callback(Result.failure(IllegalStateException("runtime unavailable")))
                } else try {
                    target.send(Message.obtain(null, what).apply {
                        replyTo = replies
                        this.data = data
                    })
                    main.postDelayed({ pending.remove(requestId)?.invoke(Result.failure(IllegalStateException("runtime timeout"))) }, Act008SessionProtocol.REQUEST_TIMEOUT_MS)
                } catch (_: RemoteException) {
                    pending.remove(requestId)
                    callback(Result.failure(IllegalStateException("runtime unavailable")))
                }
            }
            if (!bound) {
                queued += sendNow
                val intent = Intent().setComponent(ComponentName(context.packageName, "com.example.appsandbox.experiments.act008.Act008GuestApplicationRuntimeService"))
                if (service == null && !context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    queued.remove(sendNow)
                    pending.remove(requestId)
                    callback(Result.failure(IllegalStateException("runtime bind failed")))
                }
            } else sendNow.invoke()
        }
    }

    private fun failPending(message: String) {
        service = null
        bound = false
        queued.clear()
        pending.values.forEach { it(Result.failure(IllegalStateException(message))) }
        pending.clear()
    }

    private fun rememberRun(instanceId: String, runId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(runKey(instanceId), runId).apply()
    }

    private fun runKey(instanceId: String) = "act008.run.$instanceId"

    private companion object {
        const val PREFS = "act008-session-client"
    }
}
