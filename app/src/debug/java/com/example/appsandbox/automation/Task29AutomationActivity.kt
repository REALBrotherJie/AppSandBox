package com.example.appsandbox.automation

import android.app.Activity
import android.app.ActivityManager
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
import android.os.Process
import android.widget.TextView
import com.example.appsandbox.MainActivity
import com.example.appsandbox.imports.GuestImportCoordinator
import com.example.appsandbox.imports.GuestImportPhase
import com.example.appsandbox.runtime.GuestRuntimeError
import com.example.appsandbox.runtime.GuestRuntimeProtocol
import com.example.appsandbox.runtime.GuestRuntimeService
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.workspace.GuestWorkspaceLauncher
import java.io.File

class Task29AutomationActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        intent.getStringExtra(EXTRA_INSTANCE)?.let { id ->
            val resolved = GuestInstanceStore(this).list().singleOrNull { it.instanceId.startsWith(id, ignoreCase = true) }?.instanceId ?: id
            GuestWorkspaceLauncher.open(this, resolved)
            finish(); return
        }
        intent.getStringExtra(EXTRA_PACKAGE)?.let { packageName ->
            val ordinal = intent.getIntExtra(EXTRA_INSTANCE_ORDINAL, 0)
            val resolved = GuestInstanceStore(this).list().filter { it.guestPackageName == packageName }.sortedBy { it.createdAt }[ordinal].instanceId
            GuestWorkspaceLauncher.open(this, resolved)
            finish(); return
        }
        intent.getStringExtra(EXTRA_STALE_INSTANCE)?.let { instanceId ->
            startActivity(GuestWorkspaceLauncher.intent(this, instanceId))
            finish(); return
        }
        intent.getStringExtra(EXTRA_CREATE_PACKAGE)?.let { packageName ->
            val revision = GuestStore(this).recordsForPackage(packageName).maxBy { it.importedAt }
            val created = GuestInstanceStore(this).create(revision)
            setContentView(TextView(this).apply { text = "SUCCESS: created ${created.instanceId}"; textSize = 18f })
            return
        }
        intent.getStringExtra(EXTRA_DELETE_REVISION)?.let { prefix ->
            val store = GuestStore(this); val revision = store.records().single { it.revisionId.startsWith(prefix, true) }
            store.deleteRevision(revision.revisionId, GuestInstanceStore(this).list())
            setContentView(TextView(this).apply { text = "SUCCESS: revision deleted" }); return
        }
        intent.getStringExtra(EXTRA_TRY_DELETE_REVISION)?.let { prefix ->
            val store = GuestStore(this); val revision = store.records().single { it.revisionId.startsWith(prefix, true) }
            val text = runCatching { store.deleteRevision(revision.revisionId, GuestInstanceStore(this).list()); "UNEXPECTED: revision deleted" }
                .getOrElse { "BLOCKED: ${it.message}" }
            setContentView(TextView(this).apply { this.text = text; textSize = 18f }); return
        }
        if (intent.getBooleanExtra(EXTRA_RUNTIME_STATUS, false)) {
            setContentView(TextView(this).apply { text = runtimeStatus(); textSize = 18f; setPadding(32, 32, 32, 32) })
            return
        }
        if (intent.getBooleanExtra(EXTRA_KILL_RUNTIME, false)) {
            setContentView(TextView(this).apply { text = killRuntime(); textSize = 18f; setPadding(32, 32, 32, 32) })
            return
        }
        intent.getStringExtra(EXTRA_RUNTIME_DELETED_TOKEN_CHECK)?.let { prefix ->
            runtimeDeletedTokenCheck(prefix)
            return
        }
        val path = intent.getStringExtra(EXTRA_APK)
        val result = path?.let { GuestImportCoordinator(this).importFile(File(it)) }
        val text = when {
            result == null -> "FAILURE: missing staged APK"
            result.phase != GuestImportPhase.SUCCESS -> "${result.phase}: ${result.message}"
            else -> "SUCCESS: imported supported Guest contract v${result.record?.contractVersion}"
        }
        setContentView(TextView(this).apply { this.text = text; textSize = 18f; setPadding(32, 32, 32, 32) })
        if (result?.phase == GuestImportPhase.SUCCESS) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_IMPORT_RESULT, text).putExtra(MainActivity.EXTRA_AUTOMATION, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    private fun runtimeStatus(): String {
        val process = runtimeProcess()
        return if (process == null) "RUNTIME: not running" else "RUNTIME: pid=${process.pid} name=${process.processName} uid=${process.uid}"
    }

    private fun killRuntime(): String {
        val process = runtimeProcess() ?: return "RUNTIME: not running"
        Process.killProcess(process.pid)
        return "SUCCESS: killed runtime pid=${process.pid} uid=${process.uid}"
    }

    private fun runtimeProcess(): ActivityManager.RunningAppProcessInfo? {
        val target = "$packageName:guest_runtime"
        return getSystemService(ActivityManager::class.java)
            .runningAppProcesses
            ?.firstOrNull { it.processName == target }
    }

    private fun runtimeDeletedTokenCheck(prefix: String) {
        val output = TextView(this).apply { text = "RUNTIME: deleted token check running"; textSize = 18f; setPadding(32, 32, 32, 32) }
        setContentView(output)
        val store = GuestInstanceStore(this)
        val instance = store.list().singleOrNull { it.instanceId.startsWith(prefix, ignoreCase = true) }
        if (instance == null) {
            output.text = "FAILURE: instance not found"
            return
        }
        val main = Handler(Looper.getMainLooper())
        lateinit var connection: ServiceConnection
        lateinit var replies: Messenger
        var runtime: Messenger? = null
        replies = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                val data = message.data ?: Bundle.EMPTY
                when (data.getLong(GuestRuntimeProtocol.KEY_REQUEST_ID)) {
                    1L -> {
                        if (!data.getBoolean(GuestRuntimeProtocol.KEY_OK, false)) {
                            output.text = "FAILURE: open failed ${data.getString(GuestRuntimeProtocol.KEY_ERROR)}"
                            runCatching { unbindService(connection) }
                            return
                        }
                        val token = data.getString(GuestRuntimeProtocol.KEY_SESSION_TOKEN).orEmpty()
                        store.delete(instance.instanceId)
                        runtime?.send(Message.obtain(null, GuestRuntimeProtocol.MSG_READ).apply {
                            replyTo = replies
                            this.data = Bundle().apply {
                                putLong(GuestRuntimeProtocol.KEY_REQUEST_ID, 2L)
                                putString(GuestRuntimeProtocol.KEY_SESSION_TOKEN, token)
                            }
                        })
                    }
                    2L -> {
                        val error = data.getString(GuestRuntimeProtocol.KEY_ERROR)
                        output.text = if (!data.getBoolean(GuestRuntimeProtocol.KEY_OK, true) && error == GuestRuntimeError.DELETED_INSTANCE.wireName) {
                            "SUCCESS: old token rejected deleted_instance"
                        } else {
                            "FAILURE: old token result ok=${data.getBoolean(GuestRuntimeProtocol.KEY_OK, true)} error=$error"
                        }
                        runCatching { unbindService(connection) }
                    }
                }
            }
        })
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                runtime = Messenger(binder)
                runtime?.send(Message.obtain(null, GuestRuntimeProtocol.MSG_OPEN).apply {
                    replyTo = replies
                    data = Bundle().apply {
                        putLong(GuestRuntimeProtocol.KEY_REQUEST_ID, 1L)
                        putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, instance.instanceId)
                        putString(GuestRuntimeProtocol.KEY_REVISION_ID, instance.guestRevisionId)
                    }
                })
            }
            override fun onServiceDisconnected(name: ComponentName) {
                output.text = "FAILURE: runtime disconnected"
            }
        }
        main.postDelayed({
            if (output.text.toString().contains("running")) {
                output.text = "FAILURE: runtime deleted token check timeout"
                runCatching { unbindService(connection) }
            }
        }, 5_000L)
        if (!bindService(Intent(this, GuestRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            output.text = "FAILURE: runtime bind failed"
        }
    }

    companion object {
        const val EXTRA_APK = "stagedApk"; const val EXTRA_INSTANCE = "instanceId"; const val EXTRA_PACKAGE = "packageName"; const val EXTRA_INSTANCE_ORDINAL = "instanceOrdinal"; const val EXTRA_STALE_INSTANCE = "staleInstanceId"; const val EXTRA_CREATE_PACKAGE = "createPackage"; const val EXTRA_DELETE_REVISION = "deleteRevision"; const val EXTRA_TRY_DELETE_REVISION = "tryDeleteRevision"; const val EXTRA_RUNTIME_STATUS = "runtimeStatus"; const val EXTRA_KILL_RUNTIME = "killRuntime"; const val EXTRA_RUNTIME_DELETED_TOKEN_CHECK = "runtimeDeletedTokenCheck"
    }
}
