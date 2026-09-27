package com.example.appsandbox.experiments.act003

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.example.appsandbox.experiments.act005.Act005P1Selector
import com.example.appsandbox.experiments.act005.Act005Request
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File
import com.example.appsandbox.experiments.act006.api36.*

class Act003StubActivity : Activity() {
    private val validLaunchId by lazy { intent.getStringExtra(EXTRA_LAUNCH_ID)?.takeIf { it.isNotBlank() } }
    private val experiment by lazy { intent.getStringExtra(EXTRA_EXPERIMENT) }
    private val report by lazy {
        File(filesDir, when {
            experiment == EXPERIMENT_ACT006 -> "task52-${validLaunchId ?: "invalid"}.result"
            experiment == EXPERIMENT_ACT005 -> "task49-${validLaunchId ?: "invalid"}.result"
            experiment == EXPERIMENT_ACT004A && validLaunchId == null -> ACT004A_INVALID_REPORT
            experiment == EXPERIMENT_ACT004A -> ACT004A_VALID_REPORT
            validLaunchId == null -> INVALID_REPORT
            else -> VALID_REPORT
        })
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        report.delete()
        append("lifecycle=onCreate")
        if (validLaunchId == null) {
            append("launchResult=INVALID_LAUNCH_ID")
            append("guestActivityInstantiated=false")
            append("hostSurvived=true")
            finish()
            return
        }
        if (experiment == EXPERIMENT_ACT005) runAct005()
        if (experiment == EXPERIMENT_ACT006) runAct006()
        setContentView(TextView(this).apply { text = "ACT003_HOST_STUB" })
        append("launchResult=VALID")
        append("packageName=$packageName")
        append("componentName=${componentName.flattenToShortString()}")
        append("hostStub.objectClass=${javaClass.name}")
        append("taskId=$taskId")
        append("isTaskRoot=$isTaskRoot")
        append("applicationNonNull=${application != null}")
        append("baseContextNonNull=${baseContext != null}")
        append("intent.action=${intent.action}")
        append("launchId=$validLaunchId")
        append("instanceId=${intent.getStringExtra(EXTRA_INSTANCE_ID)}")
        append("logicalGuestPackage=${intent.getStringExtra(EXTRA_GUEST_PACKAGE)}")
        append("logicalGuestComponent=${intent.getStringExtra(EXTRA_GUEST_COMPONENT)}")
        append("revisionId=${intent.getStringExtra(EXTRA_REVISION_ID)}")
        append("originalAction=${intent.getStringExtra(EXTRA_ORIGINAL_ACTION)}")
        if (experiment == EXPERIMENT_ACT004A) append("logicalMapping=UNATTACHED")
        append("window.class=${window.javaClass.name}")
        append("window.decorView.class=${window.decorView.javaClass.name}")
        append("window.attributes.type=${window.attributes.type}")
        append("configuration.orientation=${resources.configuration.orientation}")
        append("guestActivityInstantiated=false")
        window.decorView.post { recordTokens("decorPost") }
    }

    override fun onStart() { super.onStart(); append("lifecycle=onStart") }
    override fun onResume() { super.onResume(); append("lifecycle=onResume") }
    override fun onPause() { append("lifecycle=onPause"); super.onPause() }
    override fun onStop() { append("lifecycle=onStop"); super.onStop() }
    override fun onDestroy() { append("lifecycle=onDestroy"); super.onDestroy() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        append("lifecycle=onWindowFocusChanged:$hasFocus")
        if (hasFocus && validLaunchId != null) recordTokens("windowFocus")
    }

    private fun recordTokens(source: String) {
        val decor = window.decorView
        append("tokenObservation=$source")
        append("decor.windowToken.nonNull=${decor.windowToken != null}")
        append("decor.applicationWindowToken.nonNull=${decor.applicationWindowToken != null}")
        append("hostStubTokenOnly=true")
        if (experiment == EXPERIMENT_ACT004A) append("guestTokenWindowTask=NOT_CLAIMED")
        append("hostSurvived=true")
    }

    private fun runAct005() {
        val launchId = requireNotNull(validLaunchId)
        val scenario = intent.getStringExtra(EXTRA_SCENARIO) ?: "valid"
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID).orEmpty()
        val storedInstance = runCatching { GuestInstanceStore(this).get(instanceId) }.getOrNull()
        val storedRevision = runCatching { GuestStore(this).findRevision(storedInstance?.guestRevisionId.orEmpty()) }.getOrNull()
        val guestClass = when (scenario) {
            "missing-class" -> "com.example.appsandbox.testguest.runtime.MissingActivity"
            "non-activity" -> "com.example.appsandbox.testguest.runtime.GuestProbe"
            "component-stale" -> "com.example.appsandbox.testguest.runtime.StaleActivity"
            else -> intent.getStringExtra(EXTRA_GUEST_COMPONENT).orEmpty()
        }
        val revision = when (scenario) {
            "missing-class", "non-activity" -> storedRevision?.copy(components = storedRevision.components +
                requireNotNull(storedRevision.components.firstOrNull { it.type == GuestComponentType.ACTIVITY }).copy(className = guestClass,
                    intentFilters = emptyList()))
            else -> storedRevision
        }
        val request = Act005Request(
            launchId = launchId,
            instanceId = instanceId,
            revisionId = if (scenario == "stale-revision") "stale-revision" else intent.getStringExtra(EXTRA_REVISION_ID).orEmpty(),
            artifactSha256 = if (scenario == "artifact-mismatch") "0".repeat(64) else intent.getStringExtra(EXTRA_ARTIFACT_SHA).orEmpty(),
            guestActivityClass = guestClass,
            hostStubComponent = componentName.flattenToShortString(),
            forcedApi = if (scenario == "api-mismatch") if (android.os.Build.VERSION.SDK_INT == 31) 36 else 31 else null,
            denyAccess = scenario == "access-denied"
        )
        val result = Act005P1Selector(codeCacheDir).select(request, storedInstance, revision)
        append("act005.scenario=$scenario")
        append("act005.adapter=${result.capability.adapter}")
        append("act005.capability=${result.capability.access}")
        append("act005.fingerprint=${result.capability.fingerprint}")
        append("act005.phases=${result.phases.joinToString("->")}")
        append("act005.reason=${result.reason}")
        append("act005.selectedClass=${result.selectedClass ?: "none"}")
        append("act005.hostFallback=${result.hostFallback}")
        append("guestObjectConstructed=${result.guestObjectConstructed}")
        append("guestAttached=${result.guestAttached}")
        append("guestLifecycle=${result.guestLifecycle}")
        append("hostFallback=ACT003_STUB")
    }

    private fun runAct006() {
        val launchId = requireNotNull(validLaunchId)
        val scenario = intent.getStringExtra(EXTRA_SCENARIO) ?: "valid"
        val instance = runCatching { GuestInstanceStore(this).get(intent.getStringExtra(EXTRA_INSTANCE_ID).orEmpty()) }.getOrNull()
        val revision = runCatching { GuestStore(this).findRevision(instance?.guestRevisionId.orEmpty()) }.getOrNull()
        val apk = revision?.apkPath?.let(::File)
        val expected = revision?.sha256.orEmpty()
        val result = if (apk == null || revision == null) Act006Result(Act006Reason.STALE_REVISION, false, "NOT_CALLED", false, false, false, false, false, false, false, "missing")
        else Act006Api36Adapter().attach(Act006Request(if (scenario == "api-mismatch") 31 else android.os.Build.VERSION.SDK_INT, apk, "com.example.appsandbox.testguest.runtime.GuestMainActivity", if (scenario == "sha-mismatch") "0".repeat(64) else expected, expected, this, scenario == "attach-fault"))
        append("act006.scenario=$scenario")
        append("act006.reason=${result.reason}")
        append("act006.constructor=${result.constructor}")
        append("act006.attachReturn=${result.attachReturn}")
        append("act006.baseContext=${result.baseContext}")
        append("act006.application=${result.application}")
        append("act006.intent=${result.intent}")
        append("act006.activityInfo=${result.activityInfo}")
        append("act006.window=${result.window}")
        append("act006.token=${result.token}")
        append("act006.lifecycle=${result.lifecycle}")
        append("act006.fingerprint=${result.fingerprint}")
        append("hostFallback=ACT003_STUB")
        append("guestActivityRecord=false")
    }

    private fun append(value: String) {
        report.parentFile?.mkdirs()
        report.appendText(value + "\n")
        android.util.Log.i("AppSandbox.Act003", value)
    }

    companion object {
        const val ACTION = "com.example.appsandbox.debug.ACT003"
        const val EXTRA_LAUNCH_ID = "launchId"
        const val EXTRA_GUEST_PACKAGE = "logicalGuestPackage"
        const val EXTRA_GUEST_COMPONENT = "logicalGuestComponent"
        const val EXTRA_REVISION_ID = "revisionId"
        const val EXTRA_INSTANCE_ID = "instanceId"
        const val EXTRA_ORIGINAL_ACTION = "originalAction"
        const val EXTRA_EXPERIMENT = "experiment"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_ARTIFACT_SHA = "artifactSha256"
        const val EXPERIMENT_ACT004A = "act004a-negative"
        const val EXPERIMENT_ACT005 = "act005-p1"
        const val EXPERIMENT_ACT006 = "act006-api36"
        const val VALID_REPORT = "task22-stub-valid.txt"
        const val INVALID_REPORT = "task22-stub-invalid.txt"
        const val ACT004A_VALID_REPORT = "task24-host-stub.txt"
        const val ACT004A_INVALID_REPORT = "task24-host-stub-invalid.txt"
    }
}
