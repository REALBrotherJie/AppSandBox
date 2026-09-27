package com.example.appsandbox

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.appsandbox.contract.GuestActionViewBinder
import com.example.appsandbox.contract.GuestActionSession
import com.example.appsandbox.contract.GuestViewContract
import com.example.appsandbox.experiments.GuestWorkspaceContext
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.runtime.client.GuestRuntimeSessionClient
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.storage.GuestInstanceBinding
import com.example.appsandbox.workspace.GuestWorkspaceLaunchPolicy
import com.example.appsandbox.workspace.GuestWorkspaceTaskPolicy
import dalvik.system.DexClassLoader
import java.io.File

class GuestWorkspaceActivity : Activity() {
    private lateinit var instance: GuestInstanceRecord
    private lateinit var state: TextView
    private lateinit var guestRoot: LinearLayout
    private lateinit var store: GuestInstanceStore
    private lateinit var contract: GuestViewContract
    private var actionSession: GuestActionSession? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuestInstanceStore(this)
        reload(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reload(intent)
    }
    override fun onResume() {
        super.onResume()
        reload(intent)
    }
    override fun onDestroy() {
        actionSession?.close()
        actionSession = null
        super.onDestroy()
    }
    private fun reload(source: Intent) {
        actionSession?.close()
        actionSession = null
        val spec = runCatching {
            GuestWorkspaceLaunchPolicy.validate(source.getStringExtra(EXTRA_INSTANCE_ID), source.dataString)
        }.getOrElse { show("Workspace intent is invalid: ${it.message}"); return }
        val found = runCatching { store.get(spec.instanceId) }.getOrElse {
            show("Instance unavailable or registry is corrupted"); return
        }
        if (found == null) { show("Instance unavailable or registry is corrupted"); return }
        val revision = runCatching { GuestStore(this).findRevision(found.guestRevisionId) }.getOrNull()
        val bindingError = revision?.let { GuestInstanceBinding.validate(found, it) }
        if (revision == null || bindingError != null) {
            show("Guest revision binding is unavailable or inconsistent${bindingError?.let { ": $it" } ?: ""}. Restore the original revision.")
            return
        }
        val artifact = File(found.guestApkPath)
        if (!artifact.isFile || !GuestArtifactVerifier.sha256(artifact).equals(found.guestSha256, true)) {
            show("Guest revision is missing or changed. Re-import the supported Guest before using this instance.")
            return
        }
        contract = runCatching { GuestPackageReader(this).readContract(artifact.path) }.getOrElse {
            show(it.message ?: "Unsupported Guest contract"); return
        }
        if (contract.version != revision.contractVersion) {
            show("Guest contract version changed. Restore the original revision."); return
        }
        instance = found
        val recentsLabel = GuestWorkspaceTaskPolicy.recentsLabel(instance.guestPackageName, instance.instanceId)
        title = recentsLabel
        setTaskDescription(ActivityManager.TaskDescription(recentsLabel))
        buildUi(); render()
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "Guest workspace\npackage=${instance.guestPackageName}\ninstance=${instance.instanceId}\nrevision=${instance.guestRevisionId}\ncontract=v${contract.version}"; textSize = 16f })
        state = TextView(this).apply { textSize = 22f; setPadding(0, 24, 0, 24) }
        val actions = LinearLayout(this)
        if (contract.version == 1) {
            actions.addView(Button(this).apply { text = "Increment"; setOnClickListener { writeCounter(counter() + 1); render() } })
            actions.addView(Button(this).apply { text = "Reset"; setOnClickListener { writeCounter(0); render() } })
        }
        actions.addView(Button(this).apply { text = "Close workspace"; setOnClickListener { finishAndRemoveTask() } })
        actions.addView(Button(this).apply { text = "Delete current instance"; setOnClickListener {
            android.app.AlertDialog.Builder(this@GuestWorkspaceActivity).setTitle("Delete current instance?").setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm delete instance") { _, _ -> runCatching { store.delete(instance.instanceId) }.onSuccess { finishAndRemoveTask() }.onFailure { show(it.message ?: "Unable to delete instance") } }.show()
        } })
        guestRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(state); root.addView(actions); root.addView(guestRoot, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }
    private fun render() {
        state.text = if (contract.version == 1) "Contract v1 static view | Counter: ${counter()}" else "Contract v2 actions validating"
        guestRoot.removeAllViews()
        runCatching {
            val apk = File(instance.guestApkPath); val reader = GuestPackageReader(this); val info = reader.readApplicationInfo(apk.path)
            val loader = DexClassLoader(apk.path, codeCacheDir.path, null, classLoader)
            val context = GuestWorkspaceContext(this, loader, packageManager.getResourcesForApplication(info), info, File(instance.dataRoot))
            val layoutId = context.resources.getIdentifier(contract.layoutName, "layout", context.packageName)
            require(layoutId != 0) { "Unsupported Guest: layout resource missing" }
            val guestView = android.view.LayoutInflater.from(context).inflate(layoutId, guestRoot, false)
            if (contract.version == 2) {
                val session = GuestRuntimeSessionClient(applicationContext, instance.instanceId, instance.guestRevisionId)
                actionSession = session
                GuestActionViewBinder(
                    session,
                    onState = { state.text = it },
                    onFailure = { state.text = "Guest action failed: $it" }
                ).bind(guestView, context.resources, context.packageName, contract)
            }
            guestRoot.addView(guestView)
        }.onFailure {
            guestRoot.removeAllViews()
            state.text = "Guest actions unavailable: ${it.message ?: "Unsupported Guest"}"
            guestRoot.addView(TextView(this).apply { text = "Guest view disabled" })
        }
    }
    private fun stateFile() = File(instance.dataRoot, "files/counter.txt")
    private fun counter() = runCatching { stateFile().readText().trim().toInt() }.getOrDefault(0)
    private fun writeCounter(value: Int) { stateFile().apply { parentFile!!.mkdirs(); writeText(value.toString()) } }
    private fun show(message: String) { setContentView(TextView(this).apply { text = message; textSize = 18f; setPadding(24, 24, 24, 24) }) }
    companion object { const val EXTRA_INSTANCE_ID = "instanceId" }
}
