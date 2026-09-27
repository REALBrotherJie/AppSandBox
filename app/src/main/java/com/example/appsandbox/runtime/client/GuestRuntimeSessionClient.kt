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
import com.example.appsandbox.contract.GuestAction
import com.example.appsandbox.contract.GuestActionSession
import com.example.appsandbox.contract.GuestActionSessionStatus
import com.example.appsandbox.runtime.GuestRuntimeError
import com.example.appsandbox.runtime.GuestRuntimeException
import com.example.appsandbox.runtime.GuestRuntimeProtocol
import com.example.appsandbox.runtime.GuestRuntimeService
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class GuestRuntimeSessionClient(
    private val context: Context,
    private val instanceId: String,
    private val revisionId: String
) : GuestActionSession {
    private val main = Handler(Looper.getMainLooper())
    private val requestIds = AtomicLong(1)
    private val state = GuestRuntimeClientStateMachine()
    private val pending = ArrayDeque<Operation>()
    private val callbacks = mutableMapOf<Long, Operation>()
    private var service: Messenger? = null
    private var bound = false
    private var statusListener: ((GuestActionSessionStatus) -> Unit)? = null
    private val bindTimeout = Runnable {
        state.bindTimedOut()
        failAll(GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime bind timeout")
    }
    private val replies = Messenger(ReplyHandler())
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            main.removeCallbacks(bindTimeout)
            if (state.state == GuestRuntimeClientState.CLOSED || state.state == GuestRuntimeClientState.FAILED) {
                runCatching { context.unbindService(this) }
                bound = false
                service = null
                return
            }
            service = Messenger(binder)
            bound = true
            state.bound()
            sendOpen()
        }

        override fun onServiceDisconnected(name: ComponentName) = handleRuntimeDeath()
        override fun onBindingDied(name: ComponentName) = handleRuntimeDeath()
        override fun onNullBinding(name: ComponentName) = failAll(GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
    }

    override fun readCounter(callback: (Result<Int>) -> Unit) {
        enqueue(Operation.Read(callback))
    }

    override fun execute(action: GuestAction, callback: (Result<Int>) -> Unit) {
        enqueue(Operation.Execute(action, callback))
    }

    override fun close() {
        val token = state.sessionToken
        if (token != null) send(GuestRuntimeProtocol.MSG_CLOSE, Bundle().apply {
            putString(GuestRuntimeProtocol.KEY_SESSION_TOKEN, token)
        }, null)
        callbacks.clear()
        pending.clear()
        state.close()
        main.removeCallbacks(bindTimeout)
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
    }

    override fun setStatusListener(listener: ((GuestActionSessionStatus) -> Unit)?) {
        main.post {
            statusListener = listener
            listener?.invoke(currentStatus())
        }
    }

    private fun enqueue(operation: Operation) {
        main.post {
            if (state.state == GuestRuntimeClientState.CLOSED || state.state == GuestRuntimeClientState.FAILED) {
                operation.fail(state.failure ?: GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
                return@post
            }
            pending += operation
            ensureStarted()
        }
    }

    private fun ensureStarted() {
        if (state.state == GuestRuntimeClientState.READY) {
            drainPending()
            return
        }
        if (state.state == GuestRuntimeClientState.IDLE) {
            state.start()
            notifyStatus()
        }
        if (state.state == GuestRuntimeClientState.BINDING && service == null && !bound) {
            val intent = Intent(context, GuestRuntimeService::class.java)
            main.removeCallbacks(bindTimeout)
            main.postDelayed(bindTimeout, BIND_TIMEOUT_MS)
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                main.removeCallbacks(bindTimeout)
                failAll(GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
            }
        }
    }

    private fun sendOpen() {
        send(GuestRuntimeProtocol.MSG_OPEN, Bundle().apply {
            putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, instanceId)
            putString(GuestRuntimeProtocol.KEY_REVISION_ID, revisionId)
        }, Operation.Open)
    }

    private fun drainPending() {
        val token = state.sessionToken ?: return
        while (pending.isNotEmpty()) {
            when (val operation = pending.removeFirst()) {
                is Operation.Read -> send(GuestRuntimeProtocol.MSG_READ, Bundle().apply {
                    putString(GuestRuntimeProtocol.KEY_SESSION_TOKEN, token)
                }, operation)
                is Operation.Execute -> send(GuestRuntimeProtocol.MSG_EXECUTE, Bundle().apply {
                    putString(GuestRuntimeProtocol.KEY_SESSION_TOKEN, token)
                    putString(GuestRuntimeProtocol.KEY_ACTION, operation.action.wireName)
                }, operation)
                Operation.Open -> Unit
            }
        }
    }

    private fun send(what: Int, data: Bundle, operation: Operation?) {
        val target = service ?: run {
            operation?.fail(GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
            return
        }
        val id = requestIds.getAndIncrement()
        data.putLong(GuestRuntimeProtocol.KEY_REQUEST_ID, id)
        operation?.let { callbacks[id] = it }
        try {
            target.send(Message.obtain(null, what).apply {
                replyTo = replies
                this.data = data
            })
        } catch (_: RemoteException) {
            callbacks.remove(id)
            handleRuntimeDeath()
            operation?.fail(GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
        }
    }

    private fun handleRuntimeDeath() {
        if (bound) runCatching { context.unbindService(connection) }
        service = null
        bound = false
        callbacks.values.forEach { pending += it }
        callbacks.clear()
        state.binderDied()
        if (state.state == GuestRuntimeClientState.BINDING) {
            notifyStatus()
            ensureStarted()
        } else {
            failAll(state.failure ?: GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
        }
    }

    private fun failAll(error: GuestRuntimeError, message: String) {
        main.removeCallbacks(bindTimeout)
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
        state.fail(error)
        notifyStatus()
        callbacks.values.forEach { it.fail(error, message) }
        callbacks.clear()
        pending.forEach { it.fail(error, message) }
        pending.clear()
    }

    private inner class ReplyHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = message.data ?: Bundle.EMPTY
            val operation = callbacks.remove(data.getLong(GuestRuntimeProtocol.KEY_REQUEST_ID)) ?: return
            if (!data.getBoolean(GuestRuntimeProtocol.KEY_OK, false)) {
                val error = GuestRuntimeError.fromWireName(data.getString(GuestRuntimeProtocol.KEY_ERROR))
                val text = data.getString(GuestRuntimeProtocol.KEY_MESSAGE) ?: error.wireName
                if (operation == Operation.Open) failAll(error, text) else operation.fail(error, text)
                return
            }
            when (operation) {
                Operation.Open -> {
                    val token = data.getString(GuestRuntimeProtocol.KEY_SESSION_TOKEN).orEmpty()
                    state.opened(token)
                    if (state.state == GuestRuntimeClientState.READY) {
                        notifyStatus()
                        drainPending()
                    } else {
                        failAll(GuestRuntimeError.RUNTIME_UNAVAILABLE, "runtime unavailable")
                    }
                }
                is Operation.Read -> operation.callback(Result.success(data.getInt(GuestRuntimeProtocol.KEY_COUNTER)))
                is Operation.Execute -> operation.callback(Result.success(data.getInt(GuestRuntimeProtocol.KEY_COUNTER)))
            }
        }
    }

    private sealed class Operation {
        data object Open : Operation()
        data class Read(val callback: (Result<Int>) -> Unit) : Operation()
        data class Execute(val action: GuestAction, val callback: (Result<Int>) -> Unit) : Operation()

        fun fail(error: GuestRuntimeError, message: String) {
            val result = Result.failure<Int>(GuestRuntimeException(error, message))
            when (this) {
                Open -> Unit
                is Read -> callback(result)
                is Execute -> callback(result)
            }
        }
    }

    private fun currentStatus(): GuestActionSessionStatus = when (state.state) {
        GuestRuntimeClientState.READY -> GuestActionSessionStatus.READY
        GuestRuntimeClientState.FAILED, GuestRuntimeClientState.CLOSED -> GuestActionSessionStatus.UNAVAILABLE
        GuestRuntimeClientState.IDLE, GuestRuntimeClientState.BINDING, GuestRuntimeClientState.OPENING -> GuestActionSessionStatus.CONNECTING
    }

    private fun notifyStatus() {
        statusListener?.invoke(currentStatus())
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 5_000L
    }
}
