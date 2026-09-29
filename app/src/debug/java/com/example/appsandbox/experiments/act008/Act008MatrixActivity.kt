package com.example.appsandbox.experiments.act008

import android.app.Activity
import android.content.*
import android.os.*
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File
import java.util.UUID

class Act008MatrixActivity : Activity() {
    private lateinit var report: File
    private val timeout = Handler(Looper.getMainLooper())
    private var completed = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        completed = false
        val commandId = intent.getStringExtra("commandId").orEmpty()
        report = File(filesDir, "task56-$commandId.result").also { it.delete() }
        if (commandId.isBlank()) return finish()
        if (intent.getStringExtra("action") == "setup") runSetup(commandId) else runIpc(commandId)
    }

    private fun runSetup(commandId: String) = runCatching {
        val store = GuestStore(this)
        fun import(extra: String) = store.importApk(File(requireNotNull(intent.getStringExtra(extra))).inputStream()) {
            GuestPackageReader(this).read(it, File(it).parentFile!!.name)
        }
        val normal = import("normalApk")
        val throwing = import("throwingApk")
        val constructorCrash = import("constructorCrashApk")
        val blocking = import("blockingApk")
        val instances = GuestInstanceStore(this)
        val a = instances.create(normal)
        val b = instances.create(normal)
        val badOnCreate = instances.create(throwing)
        val badConstructor = instances.create(constructorCrash)
        val blocked = instances.create(blocking)
        val probeA = "A-" + UUID.randomUUID()
        val probeB = "B-" + UUID.randomUUID()
        seed(a.dataRoot, probeA)
        seed(b.dataRoot, probeB)
        final(commandId, "PASS", listOf(
            "hostPid=${Process.myPid()}", "instanceA=${a.instanceId}", "instanceB=${b.instanceId}",
            "onCreateCrash=${badOnCreate.instanceId}", "constructorCrash=${badConstructor.instanceId}",
            "blocking=${blocked.instanceId}", "probeA=$probeA", "probeB=$probeB"
        ))
    }.onFailure { final(commandId, "FAIL", listOf("failure=SETUP", "detail=${safe(it)}")) }.also { finish() }

    private fun runIpc(commandId: String) {
        val requestId = intent.getStringExtra("requestId") ?: UUID.randomUUID().toString()
        val action = intent.getStringExtra("action").orEmpty()
        val what = when (action) {
            "start" -> Act008SessionProtocol.MSG_START
            "read" -> Act008SessionProtocol.MSG_READ
            "stop" -> Act008SessionProtocol.MSG_STOP
            "restart" -> Act008SessionProtocol.MSG_RESTART
            "delete" -> Act008SessionProtocol.MSG_DELETE
            "terminate" -> Act008SessionProtocol.MSG_TERMINATE_RUNTIME
            else -> return final(commandId, "FAIL", listOf("failure=INVALID_ACTION")).also { finish() }
        }
        val reply = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if (message.what != Act008SessionProtocol.MSG_REPLY) return
                val snapshot = Act008SessionProtocol.decode(message.data) ?: return completeFailure(commandId, "MALFORMED_REPLY")
                if (snapshot.requestId != requestId) return completeFailure(commandId, "REQUEST_ID_MISMATCH")
                complete(commandId, snapshot)
            }
        })
        var recovering = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val data = Bundle().apply {
                    putInt(Act008SessionProtocol.KEY_VERSION, Act008SessionProtocol.VERSION)
                    putString(Act008SessionProtocol.KEY_REQUEST_ID, requestId)
                    putString(Act008SessionProtocol.KEY_OPERATION_ID, intent.getStringExtra("operationId").orEmpty())
                    putString(Act008SessionProtocol.KEY_RUN_ID, intent.getStringExtra("sessionRunId").orEmpty())
                    putString(Act008SessionProtocol.KEY_INSTANCE_ID, intent.getStringExtra("instanceId").orEmpty())
                }
                runCatching { Messenger(binder).send(Message.obtain(null, what).apply { replyTo = reply; this.data = data }) }
                    .onFailure { completeFailure(commandId, "SEND_FAILED") }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (what != Act008SessionProtocol.MSG_START || recovering) return completeFailure(commandId, "RUNTIME_DISCONNECTED")
                recovering = true
                activeConnection = null
                timeout.postDelayed({ bindRecoveryRead(commandId, requestId, reply) }, 200L)
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        activeConnection = connection
        val timeoutMs = (intent.getStringExtra("timeoutMs")?.toLongOrNull()
            ?: intent.getLongExtra("timeoutMs", Act008SessionProtocol.REQUEST_TIMEOUT_MS + 2_000L)).coerceIn(1_000L, 30_000L)
        timeout.postDelayed({ completeFailure(commandId, "TIMEOUT") }, timeoutMs)
        if (!bindService(Intent(this, Act008GuestApplicationRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            completeFailure(commandId, "BIND_FAILED")
        }
    }

    private fun bindRecoveryRead(commandId: String, requestId: String, reply: Messenger) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val data = Bundle().apply {
                    putInt(Act008SessionProtocol.KEY_VERSION, Act008SessionProtocol.VERSION)
                    putString(Act008SessionProtocol.KEY_REQUEST_ID, requestId)
                    putString(Act008SessionProtocol.KEY_OPERATION_ID, intent.getStringExtra("operationId").orEmpty())
                    putString(Act008SessionProtocol.KEY_RUN_ID, intent.getStringExtra("sessionRunId").orEmpty())
                    putString(Act008SessionProtocol.KEY_INSTANCE_ID, intent.getStringExtra("instanceId").orEmpty())
                }
                runCatching {
                    Messenger(binder).send(Message.obtain(null, Act008SessionProtocol.MSG_READ).apply {
                        replyTo = reply
                        this.data = data
                    })
                }.onFailure { completeFailure(commandId, "RECOVERY_SEND_FAILED") }
            }
            override fun onServiceDisconnected(name: ComponentName) = completeFailure(commandId, "RECOVERY_DISCONNECTED")
            override fun onBindingDied(name: ComponentName) = completeFailure(commandId, "RECOVERY_BINDING_DIED")
        }
        activeConnection = connection
        if (!bindService(Intent(this, Act008GuestApplicationRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            completeFailure(commandId, "RECOVERY_BIND_FAILED")
        }
    }

    private var activeConnection: ServiceConnection? = null
    private fun complete(commandId: String, snapshot: Act008SessionSnapshot) {
        if (completed) return
        completed = true
        timeout.removeCallbacksAndMessages(null)
        activeConnection?.let { runCatching { unbindService(it) } }
        final(commandId, "PASS", listOf(
            "requestId=${snapshot.requestId}", "operationId=${snapshot.operationId}", "sessionRunId=${snapshot.runId}",
            "instanceId=${snapshot.instanceId}", "state=${snapshot.state}", "failure=${snapshot.failure}",
            "ownerPid=${snapshot.ownerPid}", "hostPid=${Process.myPid()}", "detail=${snapshot.detail.replace('\n', ' ')}"
        ))
        finish()
    }

    private fun completeFailure(commandId: String, failure: String) {
        if (completed) return
        completed = true
        timeout.removeCallbacksAndMessages(null)
        activeConnection?.let { runCatching { unbindService(it) } }
        final(commandId, "FAIL", listOf("failure=$failure", "hostPid=${Process.myPid()}"))
        finish()
    }

    private fun seed(root: String, value: String) {
        File(root, "files").mkdirs()
        File(root, "files/probe-seed").writeText(value)
    }

    private fun final(commandId: String, status: String, values: List<String>) {
        report.writeText((listOf("FINAL=1", "status=$status", "commandId=$commandId", "api=${Build.VERSION.SDK_INT}") + values).joinToString("\n", postfix = "\n"))
    }
    private fun safe(error: Throwable) = (error.javaClass.name + ":" + error.message).replace('\n', ' ').take(400)
}
