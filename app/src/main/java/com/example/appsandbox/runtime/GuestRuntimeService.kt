package com.example.appsandbox.runtime

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.example.appsandbox.contract.GuestAction

class GuestRuntimeService : Service() {
    private lateinit var engine: GuestRuntimeEngine
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        engine = GuestRuntimeEngine(GuestRuntimeRepositoryImpl(this))
        messenger = Messenger(IncomingHandler())
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val replyTo = message.replyTo ?: return
            val data = message.data ?: Bundle.EMPTY
            val reply = when (message.what) {
                GuestRuntimeProtocol.MSG_OPEN -> engine.openSession(
                    data.getString(GuestRuntimeProtocol.KEY_INSTANCE_ID),
                    data.getString(GuestRuntimeProtocol.KEY_REVISION_ID)
                )
                GuestRuntimeProtocol.MSG_READ -> engine.readState(data.getString(GuestRuntimeProtocol.KEY_SESSION_TOKEN))
                GuestRuntimeProtocol.MSG_EXECUTE -> engine.execute(
                    data.getString(GuestRuntimeProtocol.KEY_SESSION_TOKEN),
                    data.getString(GuestRuntimeProtocol.KEY_ACTION)?.let { runCatching { GuestAction.fromWireName(it) }.getOrNull() }
                )
                GuestRuntimeProtocol.MSG_CLOSE -> engine.closeSession(data.getString(GuestRuntimeProtocol.KEY_SESSION_TOKEN))
                else -> GuestRuntimeReply.Failure(GuestRuntimeError.RUNTIME_UNAVAILABLE, "unknown request")
            }
            runCatching { replyTo.send(Message.obtain(null, GuestRuntimeProtocol.MSG_REPLY).apply {
                this.data = encodeReply(data.getLong(GuestRuntimeProtocol.KEY_REQUEST_ID), reply)
            }) }
        }
    }

    private fun encodeReply(requestId: Long, reply: GuestRuntimeReply): Bundle = Bundle().apply {
        putLong(GuestRuntimeProtocol.KEY_REQUEST_ID, requestId)
        when (reply) {
            is GuestRuntimeReply.Opened -> {
                putBoolean(GuestRuntimeProtocol.KEY_OK, true)
                putString(GuestRuntimeProtocol.KEY_SESSION_TOKEN, reply.sessionToken)
                putInt(GuestRuntimeProtocol.KEY_COUNTER, reply.counter)
            }
            is GuestRuntimeReply.State -> {
                putBoolean(GuestRuntimeProtocol.KEY_OK, true)
                putInt(GuestRuntimeProtocol.KEY_COUNTER, reply.counter)
            }
            GuestRuntimeReply.Closed -> putBoolean(GuestRuntimeProtocol.KEY_OK, true)
            is GuestRuntimeReply.Failure -> {
                putBoolean(GuestRuntimeProtocol.KEY_OK, false)
                putString(GuestRuntimeProtocol.KEY_ERROR, reply.error.wireName)
                putString(GuestRuntimeProtocol.KEY_MESSAGE, reply.message)
            }
        }
    }
}
