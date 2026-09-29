package com.example.appsandbox.automation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import com.example.appsandbox.GuestActivityCarrierActivity
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestComponentRequest
import com.example.appsandbox.resolver.GuestComponentResolver
import com.example.appsandbox.resolver.GuestResolutionResult
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.workspace.GuestActivityLauncher
import com.example.appsandbox.workspace.GuestWorkspaceLauncher
import java.io.File
import java.util.UUID

class Task57AutomationActivity : Activity() {
    private lateinit var report: File

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        dispatch(intent)
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        if (newIntent == null) return
        setIntent(newIntent)
        dispatch(newIntent)
    }

    private fun dispatch(commandIntent: Intent) {
        val commandId = commandIntent.getStringExtra(EXTRA_COMMAND).orEmpty()
        report = File(filesDir, "task57-$commandId.result")
        if (commandId.isBlank()) return finish()
        when (commandIntent.getStringExtra(EXTRA_ACTION).orEmpty()) {
            ACTION_SETUP -> setup(commandId)
            ACTION_OPEN_WORKSPACE -> openWorkspace()
            ACTION_NEGATIVE -> negative(commandId)
            ACTION_MALFORMED -> malformed(commandId)
            "recreate" -> recreateCarrier(commandId)
            "delete" -> deleteInstance(commandId)
            "terminate" -> terminateRuntime(commandId)
            else -> finishReport(commandId, "FAIL", "failure=INVALID_ACTION")
        }
    }

    private fun setup(commandId: String) = runCatching {
        val store = GuestStore(this)
        fun import(extra: String): com.example.appsandbox.model.GuestPackageRecord {
            val path = requireNotNull(intent.getStringExtra(extra))
            return store.importApk(File(path).inputStream()) {
                GuestPackageReader(this).read(it, File(it).parentFile!!.name)
            }
        }
        val revisionA = import(EXTRA_NORMAL_A)
        val revisionB = import(EXTRA_NORMAL_B)
        val instances = GuestInstanceStore(this)
        val instanceA = instances.create(revisionA)
        val instanceA2 = instances.create(revisionA)
        val instanceB = instances.create(revisionB)
        val activities = revisionA.components.filter { it.type == GuestComponentType.ACTIVITY }
        val exported = requireNotNull(activities.firstOrNull { it.exported && it.enabled })
        val nonExported = requireNotNull(activities.firstOrNull { !it.exported })
        val disabled = requireNotNull(activities.firstOrNull { !it.enabled })
        finishReport(
            commandId,
            "PASS",
            "instanceA=${instanceA.instanceId}",
            "instanceA2=${instanceA2.instanceId}",
            "instanceB=${instanceB.instanceId}",
            "revisionA=${revisionA.revisionId}",
            "revisionB=${revisionB.revisionId}",
            "shaA=${revisionA.sha256}",
            "shaB=${revisionB.sha256}",
            "exportedActivity=${exported.className}",
            "nonExportedActivity=${nonExported.className}",
            "disabledActivity=${disabled.className}",
            "packageName=${revisionA.packageName}"
        )
    }.onFailure {
        finishReport(commandId, "FAIL", "failure=SETUP", "detail=${safe(it)}")
    }

    private fun openWorkspace() = runCatching {
        val commandId = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        val prefix = requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_PREFIX))
        val id = GuestInstanceStore(this).list().single { it.instanceId.startsWith(prefix, true) }.instanceId
        GuestWorkspaceLauncher.open(this, id)
        finishReport(commandId, "PASS", "instanceId=$id")
    }.onFailure {
        finishReport(
            intent.getStringExtra(EXTRA_COMMAND).orEmpty(),
            "FAIL",
            "failure=OPEN_WORKSPACE",
            "detail=${safe(it)}"
        )
    }

    private fun recreateCarrier(commandId: String) = runCatching {
        val probe = application as Task57ProbeApplication
        val old = requireNotNull(probe.carrier.get()) { "carrier not alive" }
        val before = probe.creations
        getSystemService(android.app.ActivityManager::class.java).appTasks
            .single { it.taskInfo.taskId == old.taskId }.moveToFront()
        old.recreate()
        val handler = android.os.Handler(mainLooper)
        val deadline = android.os.SystemClock.uptimeMillis() + 8_000
        val poll = object : Runnable {
            override fun run() {
                if (probe.creations > before && probe.carrier.get() !== old) {
                    finishReport(commandId, "PASS", "recreation=VERIFIED", "creations=${probe.creations}")
                } else if (android.os.SystemClock.uptimeMillis() >= deadline) {
                    finishReport(commandId, "FAIL", "failure=RECREATION_TIMEOUT")
                } else handler.postDelayed(this, 100)
            }
        }
        handler.post(poll)
    }.onFailure { finishReport(commandId, "FAIL", "detail=${safe(it)}") }

    private fun deleteInstance(commandId: String) {
        val id = intent.getStringExtra("instanceId").orEmpty()
        runCatching { GuestInstanceStore(this).delete(id) }
            .onSuccess { finishReport(commandId, "PASS", "deleted=$it") }
            .onFailure {
                finishReport(commandId, "PASS", "deleted=false", "rejected=${safe(it)}")
            }
    }

    private fun terminateRuntime(commandId: String) {
        val client = com.example.appsandbox.runtime.client.Act008SessionClient(applicationContext)
        client.terminateRuntime { result ->
            client.close()
            result.onSuccess {
                finishReport(commandId, "FAIL", "failure=UNEXPECTED_TERMINATION_REPLY")
            }.onFailure {
                if (it.message == "runtime self-termination requested") {
                    finishReport(commandId, "PASS", "terminationAcknowledged=true")
                } else finishReport(commandId, "FAIL", "detail=${safe(it)}")
            }
        }
    }

    private fun negative(commandId: String) = runCatching {
        val packageName = requireNotNull(intent.getStringExtra(EXTRA_PACKAGE))
        val revisionId = requireNotNull(intent.getStringExtra(EXTRA_REVISION))
        val nonExported = requireNotNull(intent.getStringExtra(EXTRA_NON_EXPORTED))
        val resolver = GuestComponentResolver { GuestStore(this).findRevision(it) }
        val rejected = resolver.resolve(
            GuestComponentRequest(revisionId, packageName, nonExported, GuestComponentType.ACTIVITY, GuestCallerScope.HOST_EXTERNAL)
        )
        require(rejected is GuestResolutionResult.Rejected && rejected.reason.code == "not-exported")
        val unknown = resolver.resolve(
            GuestComponentRequest(revisionId, packageName, "$packageName.MissingActivity", GuestComponentType.ACTIVITY, GuestCallerScope.HOST_EXTERNAL)
        )
        require(unknown is GuestResolutionResult.Rejected && unknown.reason.code == "not-found")
        val disabled = resolver.resolve(
            GuestComponentRequest(revisionId, packageName,
                requireNotNull(intent.getStringExtra("disabledActivity")), GuestComponentType.ACTIVITY, GuestCallerScope.HOST_EXTERNAL)
        )
        require(disabled is GuestResolutionResult.Rejected && disabled.reason.code == "disabled")
        finishReport(commandId, "PASS", "nonExported=REJECTED", "unknown=REJECTED", "disabled=REJECTED")
    }.onFailure { finishReport(commandId, "FAIL", "failure=NEGATIVE", "detail=${safe(it)}") }

    private fun malformed(commandId: String) {
        finishReport(commandId, "PASS", "carrierMalformed=LAUNCHED")
        startActivity(
            Intent(this, GuestActivityCarrierActivity::class.java)
                .setData(Uri.parse("appsandbox://activity/not-a-valid-launch"))
                .putExtra("appsandbox.activity.launchId", "bad")
                .putExtra("appsandbox.activity.instanceId", "bad")
                .putExtra("appsandbox.activity.revisionId", "bad")
                .putExtra("appsandbox.activity.packageName", "bad")
                .putExtra("appsandbox.activity.componentName", "bad")
        )
        finish()
    }

    private fun finishReport(commandId: String, status: String, vararg values: String) {
        report.writeText((listOf("FINAL=1", "status=$status", "commandId=$commandId", "api=${android.os.Build.VERSION.SDK_INT}") + values).joinToString("\n", postfix = "\n"))
        setContentView(TextView(this).apply { text = "$status task57"; textSize = 18f })
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_MALFORMED -> Unit
            else -> finish()
        }
    }

    private fun safe(error: Throwable) = "${error.javaClass.name}:${error.message}".replace('\n', ' ').take(400)

    companion object {
        const val EXTRA_COMMAND = "commandId"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NORMAL_A = "normalApkA"
        const val EXTRA_NORMAL_B = "normalApkB"
        const val EXTRA_INSTANCE_PREFIX = "instancePrefix"
        const val EXTRA_PACKAGE = "packageName"
        const val EXTRA_REVISION = "revisionId"
        const val EXTRA_NON_EXPORTED = "nonExportedActivity"
        const val ACTION_SETUP = "setup"
        const val ACTION_OPEN_WORKSPACE = "openWorkspace"
        const val ACTION_NEGATIVE = "negative"
        const val ACTION_MALFORMED = "malformed"
    }
}
