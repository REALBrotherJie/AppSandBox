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
import com.example.appsandbox.activity.GuestActivityLaunchPolicy
import com.example.appsandbox.activity.LogicalActivityStore
import com.example.appsandbox.experiments.GuestWorkspaceContext
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.runtime.client.GuestRuntimeSessionClient
import com.example.appsandbox.runtime.client.Act008SessionClient
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.storage.GuestInstanceBinding
import com.example.appsandbox.workspace.GuestWorkspaceLaunchPolicy
import com.example.appsandbox.workspace.GuestWorkspaceTaskPolicy
import com.example.appsandbox.workspace.GuestActivityLauncher
import com.example.appsandbox.workspace.GuestActivityUiState
import dalvik.system.DexClassLoader
import java.io.File

class GuestWorkspaceActivity : Activity() {
    private lateinit var instance: GuestInstanceRecord
    private lateinit var revision: GuestPackageRecord
    private lateinit var state: TextView
    private lateinit var logicalResult: TextView
    private lateinit var logicalStatus: TextView
    private lateinit var guestRoot: LinearLayout
    private lateinit var store: GuestInstanceStore
    private lateinit var contract: GuestViewContract
    private var actionSession: GuestActionSession? = null
    private var appSessionClient: Act008SessionClient? = null
    private var appRunId: String? = null
    private var logicalActivityLaunchId: String? = null
    private var skipReloadAfterResult = false
    private var guestRenderGeneration = 0L
    private var appSessionGeneration = 0L
    private val logicalActivityRequestCode = 9001
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logicalActivityLaunchId = savedInstanceState?.getString("logicalActivityLaunchId")
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
        if (skipReloadAfterResult) {
            skipReloadAfterResult = false
            return
        }
        reload(intent)
    }
    override fun onDestroy() {
        guestRenderGeneration++
        appSessionGeneration++
        actionSession?.close()
        actionSession = null
        appSessionClient?.close()
        appSessionClient = null
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("logicalActivityLaunchId", logicalActivityLaunchId)
        super.onSaveInstanceState(outState)
    }
    private fun reload(source: Intent) {
        guestRenderGeneration++
        appSessionGeneration++
        appSessionClient?.close()
        appSessionClient = null
        actionSession?.close()
        actionSession = null
        val spec = runCatching {
            GuestWorkspaceLaunchPolicy.validate(source.getStringExtra(EXTRA_INSTANCE_ID), source.dataString)
        }.getOrElse { show("Workspace intent is invalid: ${it.message}"); return }
        val found = runCatching { store.get(spec.instanceId) }.getOrElse {
            show("Instance unavailable or registry is corrupted"); return
        }
        if (found == null) { show("Instance unavailable or registry is corrupted"); return }
        val loadedRevision = runCatching { GuestStore(this).findRevision(found.guestRevisionId) }.getOrNull()
        val bindingError = loadedRevision?.let { GuestInstanceBinding.validate(found, it) }
        if (loadedRevision == null || bindingError != null) {
            show("Guest revision binding is unavailable or inconsistent${bindingError?.let { ": $it" } ?: ""}. Restore the original revision.")
            return
        }
        revision = loadedRevision
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != logicalActivityRequestCode) return
        if (!::instance.isInitialized || !::revision.isInitialized || !::state.isInitialized) return
        val expected = logicalActivityLaunchId
        val actual = data?.getStringExtra(GuestActivityLaunchPolicy.EXTRA_RESULT_LAUNCH_ID)
        val actualInstance = data?.getStringExtra(GuestActivityLaunchPolicy.EXTRA_INSTANCE_ID)
        val actualRevision = data?.getStringExtra(GuestActivityLaunchPolicy.EXTRA_REVISION_ID)
        val persisted = runCatching {
            LogicalActivityStore(File(instance.dataRoot)).current()
        }.getOrNull()
        val accepted = GuestActivityUiState.acceptsResult(
            persisted,
            expected,
            actual,
            instance.instanceId,
            actualInstance,
            revision.revisionId,
            actualRevision,
            resultCode
        ) && GuestActivityUiState.boundTo(
            persisted!!,
            instance.instanceId,
            revision.revisionId,
            revision.packageName,
            instance.guestSha256
        )
        if (!accepted) {
            logicalStatus.text = "Ignored stale logical Activity result"
            refreshLogicalResult()
            return
        }
        skipReloadAfterResult = true
        logicalActivityLaunchId = null
        logicalStatus.text = ""
        refreshLogicalResult()
        render()
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "Guest workspace\npackage=${instance.guestPackageName}\ninstance=${instance.instanceId}\nrevision=${instance.guestRevisionId}\ncontract=v${contract.version}"; textSize = 16f })
        state = TextView(this).apply { textSize = 22f; setPadding(0, 24, 0, 24) }
        logicalResult = TextView(this).apply { textSize = 16f }
        logicalStatus = TextView(this).apply { textSize = 16f }
        root.addView(logicalResult)
        root.addView(logicalStatus)
        refreshLogicalResult()
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (BuildConfig.DEBUG) {
            val appState = TextView(this).apply { textSize = 16f }
            root.addView(appState)
            appSessionClient?.close()
            val client = Act008SessionClient(applicationContext)
            appSessionClient = client
            val callbackGeneration = ++appSessionGeneration
            fun render(result: Result<com.example.appsandbox.experiments.act008.Act008SessionSnapshot>) {
                if (callbackGeneration != appSessionGeneration) return
                appState.text = result.fold(
                    { snapshot ->
                        appRunId = snapshot.runId.takeIf { it.isNotBlank() }
                        "session=" + snapshot.state + " failure=" + snapshot.failure + " runId=" + snapshot.runId.take(32) + " detail=" + snapshot.detail.take(240)
                    },
                    { error -> "session unavailable: " + (error.message ?: "").take(240) }
                )
            }
            actions.addView(Button(this).apply {
                text = "Start session"
                setOnClickListener {
                    val run = java.util.UUID.randomUUID().toString()
                    client.start(instance.instanceId, java.util.UUID.randomUUID().toString(), run, ::render)
                    appRunId = run
                }
            })
            actions.addView(Button(this).apply {
                text = "Stop session"
                setOnClickListener {
                    val run = appRunId ?: return@setOnClickListener
                    client.stop(instance.instanceId, java.util.UUID.randomUUID().toString(), run, ::render)
                }
            })
            actions.addView(Button(this).apply {
                text = "Restart session"
                setOnClickListener {
                    val run = java.util.UUID.randomUUID().toString()
                    client.restart(instance.instanceId, java.util.UUID.randomUUID().toString(), run, ::render)
                    appRunId = run
                }
            })
            client.read(instance.instanceId, ::render)
        }
        if (contract.version == 1) {
            actions.addView(Button(this).apply { text = "Increment"; setOnClickListener { writeCounter(counter() + 1); render() } })
            actions.addView(Button(this).apply { text = "Reset"; setOnClickListener { writeCounter(0); render() } })
        }
        revision.components
            .filter { it.type == GuestComponentType.ACTIVITY }
            .forEach { component ->
                actions.addView(Button(this).apply {
                    text = "Open Activity ${component.className.substringAfterLast('.')}"
                    setOnClickListener {
                        runCatching {
                            logicalActivityLaunchId = GuestActivityLauncher.openForResult(
                                this@GuestWorkspaceActivity,
                                instance.instanceId,
                                revision.revisionId,
                                revision.packageName,
                                component.className,
                                logicalActivityRequestCode
                            )
                        }.onSuccess { logicalStatus.text = "" }
                            .onFailure { logicalStatus.text = it.message ?: "Unable to open logical Activity" }
                    }
                })
            }
        actions.addView(Button(this).apply { text = "Close workspace"; setOnClickListener { finishAndRemoveTask() } })
        actions.addView(Button(this).apply { text = "Delete current instance"; setOnClickListener {
            android.app.AlertDialog.Builder(this@GuestWorkspaceActivity).setTitle("Delete current instance?").setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm delete instance") { _, _ ->
                    if (BuildConfig.DEBUG) {
                        val client = appSessionClient ?: Act008SessionClient(applicationContext)
                        client.delete(instance.instanceId) { result ->
                            result.onSuccess { finishAndRemoveTask() }
                                .onFailure { show(it.message ?: "Unable to delete instance") }
                        }
                    } else runCatching { store.delete(instance.instanceId) }
                        .onSuccess { finishAndRemoveTask() }
                        .onFailure { show(it.message ?: "Unable to delete instance") }
                }.show()
        } })
        guestRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(state)
        root.addView(actions)
        root.addView(guestRoot, LinearLayout.LayoutParams(-1, -2))
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
    private fun refreshLogicalResult() {
        logicalResult.text = runCatching {
            GuestActivityUiState.completedResult(
                LogicalActivityStore(File(instance.dataRoot)).current(),
                instance.instanceId,
                revision.revisionId,
                revision.packageName,
                instance.guestSha256
            )?.let {
                "Logical Activity result\ncomponent=${it.componentName}\nlaunch=${it.launchId}\nresultCode=${it.resultCode}\nmessage=${it.resultMessage.orEmpty()}"
            } ?: "No completed logical Activity result"
        }.getOrElse { "Logical Activity result unavailable: ${it.message ?: "Invalid persisted state"}" }
    }
    private fun render() {
        val callbackGeneration = ++guestRenderGeneration
        actionSession?.close()
        actionSession = null
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
                    onState = { if (callbackGeneration == guestRenderGeneration) state.text = it },
                    onFailure = { if (callbackGeneration == guestRenderGeneration) state.text = "Guest action failed: $it" }
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
