package com.example.appsandbox.runtime

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.example.appsandbox.stub.StubProcessPool
import com.example.appsandbox.stub.StubServices

class GuestRuntimeService : Service() {
    private val pool = StubProcessPool(StubServices.SLOT_COUNT)
    private val messenger by lazy { Messenger(IncomingHandler()) }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = message.data ?: Bundle.EMPTY
            val reply = Bundle()
            runCatching {
                when (message.what) {
                    GuestRuntimeProtocol.MSG_ALLOCATE -> {
                        val instanceId = requireNotNull(data.getString(GuestRuntimeProtocol.KEY_INSTANCE_ID))
                        val preferred = data.getInt(GuestRuntimeProtocol.KEY_PREFERRED_SLOT, -1).takeIf { it >= 0 }
                        val slot = pool.allocate(instanceId, preferred)
                        startService(StubServices.intent(this@GuestRuntimeService, slot))
                        reply.putInt(GuestRuntimeProtocol.KEY_SLOT, slot)
                    }
                    GuestRuntimeProtocol.MSG_QUERY -> reply.putInt(GuestRuntimeProtocol.KEY_SLOT, pool.query(requireNotNull(data.getString(GuestRuntimeProtocol.KEY_INSTANCE_ID))) ?: -1)
                    GuestRuntimeProtocol.MSG_RELEASE -> {
                        val instanceId = requireNotNull(data.getString(GuestRuntimeProtocol.KEY_INSTANCE_ID))
                        val slot = pool.release(instanceId)
                        if (slot != null) stopService(StubServices.intent(this@GuestRuntimeService, slot))
                        reply.putInt(GuestRuntimeProtocol.KEY_SLOT, slot ?: -1)
                    }
                    else -> error("unknown runtime request ${message.what}")
                }
                reply.putBoolean(GuestRuntimeProtocol.KEY_OK, true)
            }.onFailure {
                reply.putBoolean(GuestRuntimeProtocol.KEY_OK, false)
                reply.putString(GuestRuntimeProtocol.KEY_MESSAGE, it.message ?: it.javaClass.simpleName)
            }
            message.replyTo?.send(Message.obtain(null, GuestRuntimeProtocol.MSG_REPLY).apply { this.data = reply })
        }
    }
}
