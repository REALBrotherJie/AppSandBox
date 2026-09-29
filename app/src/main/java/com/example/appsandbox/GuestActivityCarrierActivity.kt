package com.example.appsandbox

import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.appsandbox.activity.GuestActivityLaunchPolicy
import com.example.appsandbox.activity.GuestActivityLaunchSpec
import com.example.appsandbox.activity.LogicalActivityRecord
import com.example.appsandbox.activity.LogicalActivityState
import com.example.appsandbox.activity.LogicalActivityStore
import com.example.appsandbox.contract.GuestActionSession
import com.example.appsandbox.contract.GuestActionViewBinder
import com.example.appsandbox.contract.GuestViewContract
import com.example.appsandbox.experiments.GuestWorkspaceContext
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.resolver.GuestComponentRequest
import com.example.appsandbox.resolver.GuestComponentResolver
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.storage.GuestInstanceBinding
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.runtime.client.GuestRuntimeSessionClient
import java.io.File
import dalvik.system.DexClassLoader

class GuestActivityCarrierActivity : Activity() {
    private lateinit var spec: GuestActivityLaunchSpec
    private lateinit var record: com.example.appsandbox.model.GuestInstanceRecord
    private lateinit var activityRecord: LogicalActivityRecord
    private lateinit var state: TextView
    private lateinit var guestRoot: LinearLayout
    private var contract: GuestViewContract? = null
    private var actionSession: GuestActionSession? = null
    private var runtimeSession: GuestRuntimeSessionClient? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { loadAndRender() }.onFailure { showFailure(it.message ?: "Logical Activity unavailable") }
    }

    override fun onDestroy() {
        actionSession?.close()
        actionSession = null
        runtimeSession?.close()
        runtimeSession = null
        super.onDestroy()
    }

    @Deprecated("Use the carrier result contract for this logical Activity subset.")
    override fun onBackPressed() {
        finishWithResult(Activity.RESULT_CANCELED, "back")
    }

    private fun loadAndRender() {
        spec = GuestActivityLaunchPolicy.validate(
            intent.getStringExtra(GuestActivityLaunchPolicy.EXTRA_LAUNCH_ID),
            intent.getStringExtra(GuestActivityLaunchPolicy.EXTRA_INSTANCE_ID),
            intent.getStringExtra(GuestActivityLaunchPolicy.EXTRA_REVISION_ID),
            intent.getStringExtra(GuestActivityLaunchPolicy.EXTRA_PACKAGE_NAME),
            intent.getStringExtra(GuestActivityLaunchPolicy.EXTRA_COMPONENT_NAME),
            intent.dataString
        )
        record = requireNotNull(GuestInstanceStore(this).get(spec.instanceId)) { "Instance unavailable" }
        val revision = requireNotNull(GuestStore(this).findRevision(spec.revisionId)) { "Revision unavailable" }
        require(GuestInstanceBinding.validate(record, revision) == null) { "Instance/revision binding mismatch" }
        require(record.guestPackageName == spec.packageName) { "Instance package mismatch" }
        require(GuestArtifactVerifier.verify(revision).state.name == "VALID") { "Guest artifact is unavailable" }
        val component = when (
            val result = GuestComponentResolver { id -> GuestStore(this).findRevision(id) }.resolve(
                GuestComponentRequest(
                    spec.revisionId,
                    spec.packageName,
                    spec.componentName,
                    GuestComponentType.ACTIVITY,
                    GuestCallerScope.HOST_EXTERNAL
                )
            )
        ) {
            is com.example.appsandbox.resolver.GuestResolutionResult.Resolved -> result.component
            is com.example.appsandbox.resolver.GuestResolutionResult.Rejected ->
                error("Guest Activity rejected: ${result.reason.code}")
        }
        require(component.className == spec.componentName) { "Resolved Activity mismatch" }
        contract = GuestPackageReader(this).readContract(revision.apkPath)
        require(contract!!.version == 2) { "Unsupported logical Activity contract" }
        activityRecord = LogicalActivityStore(File(record.dataRoot)).begin(
            LogicalActivityRecord(
                spec.launchId,
                record.instanceId,
                revision.revisionId,
                revision.packageName,
                requireNotNull(revision.sha256),
                component.className
            )
        )
        title = "${revision.packageName} / ${component.className.substringAfterLast('.')}"
        setTaskDescription(ActivityManager.TaskDescription(title.toString()))
        buildUi(revision.apkPath, component.className)
    }

    private fun buildUi(apkPath: String, componentName: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Logical Activity\npackage=${spec.packageName}\ncomponent=$componentName\ninstance=${spec.instanceId}\nrevision=${spec.revisionId}"
            textSize = 16f
        })
        state = TextView(this).apply { textSize = 18f; setPadding(0, 16, 0, 16) }
        root.addView(state)
        guestRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(guestRoot, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Return logical result"
            setOnClickListener { finishWithResult(Activity.RESULT_OK, "completed") }
        })
        root.addView(Button(this).apply {
            text = "Back to workspace"
            setOnClickListener { finishWithResult(Activity.RESULT_CANCELED, "back") }
        })
        setContentView(root)

        val appInfo = GuestPackageReader(this).readApplicationInfo(apkPath)
        val resources = packageManager.getResourcesForApplication(appInfo)
        val loader = DexClassLoader(apkPath, codeCacheDir.path, null, classLoader)
        val context = GuestWorkspaceContext(this, loader, resources, appInfo, File(record.dataRoot))
        val currentContract = requireNotNull(contract)
        val layoutId = context.resources.getIdentifier(currentContract.layoutName, "layout", context.packageName)
        require(layoutId != 0) { "Unsupported Guest: layout resource missing" }
        val guestView = android.view.LayoutInflater.from(context).inflate(layoutId, guestRoot, false)
        val session = GuestRuntimeSessionClient(applicationContext, record.instanceId, spec.revisionId)
        runtimeSession = session
        actionSession = session
        GuestActionViewBinder(
            session,
            onState = { state.text = it },
            onFailure = { state.text = "Guest action failed: $it" }
        ).bind(guestView, context.resources, context.packageName, currentContract)
        guestRoot.addView(guestView)
    }

    private fun finishWithResult(resultCode: Int, message: String) {
        if (completed) return
        completed = true
        runCatching {
            LogicalActivityStore(File(record.dataRoot)).complete(spec.launchId, resultCode, message)
        }.onFailure {
            completed = false
            showFailure(it.message ?: "Unable to persist logical Activity result")
            return
        }
        setResult(
            resultCode,
            android.content.Intent()
                .putExtra(GuestActivityLaunchPolicy.EXTRA_RESULT_LAUNCH_ID, spec.launchId)
                .putExtra(GuestActivityLaunchPolicy.EXTRA_RESULT_MESSAGE, message)
        )
        finish()
    }

    private fun showFailure(message: String) {
        setContentView(TextView(this).apply {
            text = "Logical Activity unavailable: $message"
            textSize = 18f
            setPadding(24, 24, 24, 24)
        })
        setResult(
            Activity.RESULT_CANCELED,
            android.content.Intent()
                .putExtra(GuestActivityLaunchPolicy.EXTRA_RESULT_LAUNCH_ID, intent.getStringExtra(GuestActivityLaunchPolicy.EXTRA_LAUNCH_ID))
                .putExtra(GuestActivityLaunchPolicy.EXTRA_RESULT_MESSAGE, message)
        )
    }
}
