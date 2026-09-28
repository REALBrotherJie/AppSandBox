package com.example.appsandbox.automation

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.experiments.act007.api31.Act007ApplicationSessions
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

class Task55Api31Activity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId").orEmpty(); val action = intent.getStringExtra("action").orEmpty()
        val file = File(filesDir, "task55-$runId.result")
        val text = runCatching {
            when (action) {
                "setup" -> {
                    val record = GuestStore(this).importApk(File(requireNotNull(intent.getStringExtra("apk"))).inputStream()) { GuestPackageReader(this).read(it, File(it).parentFile!!.name) }
                    val a = GuestInstanceStore(this).create(record); val b = GuestInstanceStore(this).create(record)
                    "outcome=READY\ninstanceA=${a.instanceId}\ninstanceB=${b.instanceId}\nrevisionId=${record.revisionId}"
                }
                "start", "restart", "throwing" -> format(Act007ApplicationSessions.start(this, intent.getStringExtra("instanceId").orEmpty(), action == "throwing"))
                "stop" -> format(Act007ApplicationSessions.stop(intent.getStringExtra("instanceId").orEmpty()))
                "status" -> "outcome=${Act007ApplicationSessions.status(intent.getStringExtra("instanceId").orEmpty())}\nreason=NONE"
                else -> "outcome=REJECTED\nreason=INVALID_ACTION"
            }
        }.getOrElse { "outcome=REJECTED\nreason=INTERNAL:${it.javaClass.simpleName}" }
        val tmp = File(filesDir, file.name + ".tmp"); tmp.writeText("status=FINAL\nrunId=$runId\naction=$action\n$text\nactivityLifecycleCalled=false\nactivityAttachCalled=false\n"); check(tmp.renameTo(file)); finish()
    }
    private fun format(r: com.example.appsandbox.experiments.act007.api31.Act007SessionResult) =
        "outcome=${r.outcome}\nreason=${r.reason}\ninstanceId=${r.instanceId}\nconstructed=${r.constructed}\nonCreateAttempted=${r.onCreateAttempted}\nonCreateCompleted=${r.onCreateCompleted}\nloader=${r.loader}\ndataRoot=${r.dataRoot}"
}
