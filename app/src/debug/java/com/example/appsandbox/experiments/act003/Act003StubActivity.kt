package com.example.appsandbox.experiments.act003

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.io.File

class Act003StubActivity : Activity() {
    private val validLaunchId by lazy { intent.getStringExtra(EXTRA_LAUNCH_ID)?.takeIf { it.isNotBlank() } }
    private val experiment by lazy { intent.getStringExtra(EXTRA_EXPERIMENT) }
    private val report by lazy {
        File(filesDir, when {
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
        const val EXPERIMENT_ACT004A = "act004a-negative"
        const val VALID_REPORT = "task22-stub-valid.txt"
        const val INVALID_REPORT = "task22-stub-invalid.txt"
        const val ACT004A_VALID_REPORT = "task24-host-stub.txt"
        const val ACT004A_INVALID_REPORT = "task24-host-stub-invalid.txt"
    }
}
