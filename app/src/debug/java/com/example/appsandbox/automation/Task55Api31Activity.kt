package com.example.appsandbox.automation

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.appsandbox.experiments.act007.api31.Act007ApplicationSessions
import java.util.UUID

class Task55Api31Activity : Activity() {
    private lateinit var instanceId: String
    private var currentRunId: String? = null
    private lateinit var stateView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceId = intent.getStringExtra("instanceId").orEmpty()
        currentRunId = Act007ApplicationSessions.latest(this, instanceId)?.request?.runId
        if (intent.getStringExtra("action") == null) buildWorkspaceUi() else runAutomation()
    }

    private fun buildWorkspaceUi() {
        stateView = TextView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Controlled Guest Application session\ninstance=$instanceId"
            textSize = 18f
        })
        root.addView(stateView)
        root.addView(Button(this).apply {
            text = "Start"
            setOnClickListener { startSession() }
        })
        root.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { stopSession() }
        })
        root.addView(Button(this).apply {
            text = "Restart"
            setOnClickListener { restartSession() }
        })
        setContentView(root)
        renderState()
    }

    private fun startSession() {
        val runId = UUID.randomUUID().toString()
        val result = Act007ApplicationSessions.start(this, instanceId, runId, UUID.randomUUID().toString())
        if (result.outcome == "RUNNING") currentRunId = runId
        stateView.text = Act007ApplicationSessions.format(result)
    }

    private fun stopSession(): Boolean {
        val runId = currentRunId ?: run {
            stateView.text = "No active session"
            return false
        }
        val result = Act007ApplicationSessions.stop(this, runId, UUID.randomUUID().toString())
        stateView.text = Act007ApplicationSessions.format(result)
        if (result.outcome == "STOPPED") currentRunId = null
        return result.outcome == "STOPPED"
    }

    private fun restartSession() {
        if (currentRunId != null && !stopSession()) return
        startSession()
    }

    private fun renderState() {
        val snapshot = Act007ApplicationSessions.latest(this, instanceId)
        stateView.text = snapshot?.let {
            "state=${it.state}\nfailure=${it.failure}\nrunId=${it.request.runId.take(32)}\n" +
                (it.detail?.take(240) ?: "")
        } ?: "state=NEW"
    }

    private fun runAutomation() {
        val commandId = intent.getStringExtra("runId").orEmpty()
        val action = intent.getStringExtra("action").orEmpty()
        val output = runCatching { execute(action, commandId) }
            .getOrElse { "outcome=REJECTED\nreason=INTERNAL:${it.javaClass.simpleName}" }
        filesDir.resolve("task55-$commandId.result").writeText(
            "status=FINAL\nrunId=$commandId\naction=$action\n$output\n" +
                "activityLifecycleCalled=false\nactivityAttachCalled=false\n"
        )
        finish()
    }

    private fun execute(action: String, commandId: String): String = when (action) {
        "start", "restart" -> Act007ApplicationSessions.format(
            Act007ApplicationSessions.start(
                this, intent.getStringExtra("instanceId").orEmpty(),
                commandId, intent.getStringExtra("operationId").orEmpty()
            )
        )
        "stop" -> Act007ApplicationSessions.format(
            Act007ApplicationSessions.stop(
                this, intent.getStringExtra("sessionRunId").orEmpty(),
                intent.getStringExtra("operationId").orEmpty()
            )
        )
        "status" -> renderStateForAutomation()
        "delete" -> "outcome=DELETE\nreason=NONE\nremoved=" +
            Act007ApplicationSessions.delete(this, intent.getStringExtra("instanceId").orEmpty())
        else -> "outcome=REJECTED\nreason=INVALID_ACTION"
    }

    private fun renderStateForAutomation(): String {
        val snapshot = Act007ApplicationSessions.latest(this, intent.getStringExtra("instanceId").orEmpty())
        return snapshot?.let { "outcome=${it.state}\nreason=${it.failure}\nrunId=${it.request.runId}" }
            ?: "outcome=NEW\nreason=NONE"
    }
}
