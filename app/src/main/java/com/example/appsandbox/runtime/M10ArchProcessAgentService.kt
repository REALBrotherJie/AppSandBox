package com.example.appsandbox.runtime

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.service.VirtualServiceKey
import com.example.appsandbox.service.VirtualServiceRecord
import com.example.appsandbox.service.VirtualServiceRuntime
import com.example.appsandbox.stub.P2Service

/** Minimal architecture-review endpoint. Global lifecycle ownership remains in the coordinator. */
class M10ArchProcessAgentService : Service() {
    private var generation = -1L
    private var key: VirtualProcessKey? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper(), ::handle))

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(message: Message): Boolean {
        when (message.what) {
            M10ArchProtocol.MSG_BIND_PROCESS -> bindProcess(message)
            M10ArchProtocol.MSG_STALE_PROBE -> reply(message, Bundle().apply {
                val expected = message.data.getLong(M10ArchProtocol.KEY_GENERATION)
                putBoolean(M10ArchProtocol.KEY_REJECTED, expected != generation)
                putLong(M10ArchProtocol.KEY_GENERATION, generation)
                Log.i(TAG, "STALE_TRANSACTION expected=$expected actual=$generation rejected=${expected != generation}")
            })
            M10ArchProtocol.MSG_DIE -> Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 150)
        }
        return true
    }

    private fun bindProcess(message: Message) {
        val data = message.data
        val slot = data.getInt(M10ArchProtocol.KEY_SLOT)
        val actualSlot = Regex(":p(\\d+)$").find(Application.getProcessName())?.groupValues?.get(1)?.toInt() ?: -1
        require(slot == actualSlot) { "agent slot mismatch expected=$slot actual=$actualSlot" }
        val serviceInfo = requireNotNull(data.parcelable<ServiceInfo>(M10ArchProtocol.KEY_SERVICE_INFO))
        val packageName = requireNotNull(data.getString(M10ArchProtocol.KEY_PACKAGE))
        val instanceId = requireNotNull(data.getString(M10ArchProtocol.KEY_INSTANCE))
        generation = data.getLong(M10ArchProtocol.KEY_GENERATION)
        key = VirtualProcessKey(
            data.getLong(M10ArchProtocol.KEY_REVISION), packageName, instanceId,
            requireNotNull(data.getString(M10ArchProtocol.KEY_LOGICAL_PROCESS))
        )
        val prepared = GuestProcessBootstrap(applicationContext).prepareService(packageName, instanceId, slot, serviceInfo)
        val stub = ComponentName(this, P2Service::class.java)
        VirtualServiceRuntime.registerAgentRoute(VirtualServiceRecord(
            VirtualServiceKey(packageName, instanceId, ComponentName(packageName, serviceInfo.name)),
            RuntimeIdentity.create(this, packageName, instanceId, slot).virtualUidNumber,
            prepared, stub, lastStartId = generation.toInt()
        ))
        Log.i(TAG, "AGENT_READY slot=$slot pid=${Process.myPid()} generation=$generation key=$key native=BOUND webView=CONFIGURED")
        reply(message, Bundle().apply {
            putBoolean(M10ArchProtocol.KEY_READY, true)
            putInt(M10ArchProtocol.KEY_PID, Process.myPid())
            putInt(M10ArchProtocol.KEY_SLOT, slot)
            putLong(M10ArchProtocol.KEY_GENERATION, generation)
        })
    }

    private fun reply(message: Message, data: Bundle) {
        message.replyTo?.send(Message.obtain(null, M10ArchProtocol.MSG_REPLY).apply { this.data = data })
    }

    companion object { private const val TAG = "AppSandbox.M10.Arch" }
}

object M10ArchProtocol {
    const val MSG_BIND_PROCESS = 1
    const val MSG_STALE_PROBE = 2
    const val MSG_DIE = 3
    const val MSG_REPLY = 100
    const val KEY_PACKAGE = "package"
    const val KEY_INSTANCE = "instance"
    const val KEY_LOGICAL_PROCESS = "logicalProcess"
    const val KEY_REVISION = "revision"
    const val KEY_GENERATION = "generation"
    const val KEY_SLOT = "slot"
    const val KEY_PID = "pid"
    const val KEY_READY = "ready"
    const val KEY_REJECTED = "rejected"
    const val KEY_SERVICE_INFO = "serviceInfo"

}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Bundle.parcelable(key: String): T? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java) else getParcelable(key)
