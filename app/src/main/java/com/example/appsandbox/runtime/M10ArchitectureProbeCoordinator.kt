package com.example.appsandbox.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import com.example.appsandbox.stub.P2Service
import com.example.appsandbox.service.VirtualServiceRuntime
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Host-main-process coordinator used only to resolve the M10 process architecture review. */
class M10ArchitectureProbeCoordinator(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val transactionId = UUID.randomUUID().toString()
    private var generation = nextGeneration()
    private var oldGeneration = -1L
    private var restarted = false
    private var agent: Messenger? = null
    private var agentConnection: ServiceConnection? = null
    private var serviceConnection: ServiceConnection? = null
    private val completed = AtomicBoolean(false)

    fun run() {
        Log.i(TAG, "COORDINATOR pid=${Process.myPid()} state=ALLOCATING transaction=$transactionId state=QUEUED generation=$generation")
        bindAgent()
    }

    private fun bindAgent() {
        Log.i(TAG, "PROCESS_RECORD slot=2 generation=$generation state=STARTING_PHYSICAL_PROCESS pending=1 webView=NOT_CONFIGURED")
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                agent = Messenger(binder)
                binder.linkToDeath({ handler.post(::agentDied) }, 0)
                Log.i(TAG, "AGENT_CONNECTED slot=2 binder=$binder generation=$generation transaction=$transactionId state=QUEUED")
                sendBind()
            }
            override fun onServiceDisconnected(name: ComponentName) = agentDied()
        }
        agentConnection = connection
        check(context.bindService(Intent(context, P2ProcessAgent::class.java), connection, Context.BIND_AUTO_CREATE))
    }

    private fun sendBind() {
        val info = serviceInfo()
        val key = VirtualProcessKey.canonicalProcessName(PACKAGE, info.processName)
        agent!!.send(Message.obtain(null, M10ArchProtocol.MSG_BIND_PROCESS).apply {
            data = Bundle().apply {
                putString(M10ArchProtocol.KEY_PACKAGE, PACKAGE)
                putString(M10ArchProtocol.KEY_INSTANCE, INSTANCE)
                putString(M10ArchProtocol.KEY_LOGICAL_PROCESS, key)
                putLong(M10ArchProtocol.KEY_REVISION, File(info.applicationInfo.sourceDir).lastModified())
                putLong(M10ArchProtocol.KEY_GENERATION, generation)
                putInt(M10ArchProtocol.KEY_SLOT, 2)
                putString(M10ArchProtocol.KEY_COMPONENT_KIND, M10ArchProtocol.COMPONENT_SERVICE)
                putParcelable(M10ArchProtocol.KEY_SERVICE_INFO, info)
            }
            replyTo = Messenger(Handler(Looper.getMainLooper()) { reply ->
                if (reply.data.getBoolean(M10ArchProtocol.KEY_READY)) onReady(reply.data) else onStaleReply(reply.data)
                true
            })
        })
    }

    private fun onReady(data: Bundle) {
        check(data.getLong(M10ArchProtocol.KEY_GENERATION) == generation)
        Log.i(TAG, "PROCESS_READY slot=2 pid=${data.getInt(M10ArchProtocol.KEY_PID)} generation=$generation transaction=$transactionId queued=1 dispatch=EXACTLY_ONCE")
        if (restarted) {
            agent!!.send(Message.obtain(null, M10ArchProtocol.MSG_STALE_PROBE).apply {
                this.data = Bundle().apply { putLong(M10ArchProtocol.KEY_GENERATION, oldGeneration) }
                replyTo = Messenger(Handler(Looper.getMainLooper()) { stale -> onStaleReply(stale.data); true })
            })
        } else dispatchService()
    }

    private fun onStaleReply(data: Bundle) {
        val rejected = data.getBoolean(M10ArchProtocol.KEY_REJECTED)
        Log.i(TAG, "STALE_GENERATION_PROOF old=$oldGeneration current=${data.getLong(M10ArchProtocol.KEY_GENERATION)} rejected=$rejected")
        if (rejected) dispatchService()
    }

    private fun dispatchService() {
        val original = Intent().setComponent(ComponentName(PACKAGE, SERVICE))
        val routed = VirtualServiceRuntime.routedProbeIntent(ComponentName(context, P2Service::class.java), original)
        context.startService(Intent(routed))
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "REMOTE_SERVICE_BINDER clientPid=${Process.myPid()} binder=$binder generation=$generation")
                Messenger(binder).send(Message.obtain(null, 1).apply {
                    replyTo = Messenger(Handler(Looper.getMainLooper()) { response ->
                        val serverPid = response.data.getInt("pid")
                        Log.i(TAG, "REMOTE_BINDER_ROUNDTRIP transaction=$transactionId clientPid=${Process.myPid()} serverPid=$serverPid process=${response.data.getString("process")} counter=${response.data.getInt("counter")} generation=$generation")
                        if (!restarted) {
                            runCatching { serviceConnection?.let(context::unbindService) }
                            context.stopService(VirtualServiceRuntime.routedProbeIntent(
                                ComponentName(context, P2Service::class.java),
                                Intent().setComponent(ComponentName(PACKAGE, SERVICE))
                            ))
                            handler.postDelayed({ agent!!.send(Message.obtain(null, M10ArchProtocol.MSG_DIE)) }, 150)
                        } else completed.set(true)
                        true
                    })
                })
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        serviceConnection = connection
        check(context.bindService(routed, connection, Context.BIND_AUTO_CREATE))
    }

    private fun agentDied() {
        if (completed.get() || restarted) return
        restarted = true
        oldGeneration = generation
        generation = nextGeneration()
        runCatching { serviceConnection?.let(context::unbindService) }
        runCatching { agentConnection?.let(context::unbindService) }
        agent = null
        Log.i(TAG, "AGENT_DEAD slot=2 oldGeneration=$oldGeneration newGeneration=$generation transaction=$transactionId")
        handler.postDelayed(::bindAgent, 500)
    }

    @Suppress("DEPRECATION")
    private fun serviceInfo(): ServiceInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getServiceInfo(ComponentName(PACKAGE, SERVICE), PackageManager.ComponentInfoFlags.of(0))
    } else context.packageManager.getServiceInfo(ComponentName(PACKAGE, SERVICE), 0)

    private fun nextGeneration(): Long {
        val prefs = context.getSharedPreferences("m10_arch_generation", Context.MODE_PRIVATE)
        val next = prefs.getLong("next", 0L) + 1
        prefs.edit().putLong("next", next).commit()
        return next
    }

    companion object {
        private const val TAG = "AppSandbox.M10.Arch"
        private const val PACKAGE = "com.reel.demo2"
        private const val SERVICE = "com.reel.demo2.service.RemoteProcessService"
        private const val INSTANCE = "m10-arch-review"
    }
}
