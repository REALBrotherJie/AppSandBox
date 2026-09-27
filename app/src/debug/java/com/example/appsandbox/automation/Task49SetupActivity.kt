package com.example.appsandbox.automation

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import com.example.appsandbox.experiments.act003.Act003StubActivity
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

class Task49SetupActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId") ?: return finish()
        val report = File(filesDir, "task49-setup-$runId.result")
        report.writeText("status=STARTED\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=setup\n")
        runCatching {
            val store = GuestStore(this)
            val staged = intent.getStringExtra("stagedApk")
            if (staged != null) {
                val record = store.importApk(File(staged).inputStream()) { path -> GuestPackageReader(this).read(path, File(path).parentFile!!.name) }
                val instance = GuestInstanceStore(this).create(record)
                report.writeText("status=PASS\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=setup\ninstanceId=${instance.instanceId}\nrevisionId=${record.revisionId}\nsha256=${record.sha256}\n")
            } else {
                val instance = requireNotNull(GuestInstanceStore(this).get(requireNotNull(intent.getStringExtra("instanceId"))))
                val record = requireNotNull(store.findRevision(instance.guestRevisionId))
                startActivity(Intent(this, Act003StubActivity::class.java).apply {
                    action = Act003StubActivity.ACTION
                    putExtra(Act003StubActivity.EXTRA_EXPERIMENT, if (intent.getStringExtra("experiment") == Act003StubActivity.EXPERIMENT_ACT006) Act003StubActivity.EXPERIMENT_ACT006 else Act003StubActivity.EXPERIMENT_ACT005)
                    putExtra(Act003StubActivity.EXTRA_LAUNCH_ID, runId)
                    putExtra(Act003StubActivity.EXTRA_INSTANCE_ID, instance.instanceId)
                    putExtra(Act003StubActivity.EXTRA_REVISION_ID, record.revisionId)
                    putExtra(Act003StubActivity.EXTRA_ARTIFACT_SHA, record.sha256)
                    putExtra(Act003StubActivity.EXTRA_GUEST_PACKAGE, record.packageName)
                    putExtra(Act003StubActivity.EXTRA_GUEST_COMPONENT, "com.example.appsandbox.testguest.runtime.GuestMainActivity")
                    putExtra(Act003StubActivity.EXTRA_SCENARIO, intent.getStringExtra("scenario") ?: "valid")
                })
                report.writeText("status=PASS\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=launch\n")
            }
        }.onFailure { report.writeText("status=FAIL\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=setup\nerrorCode=${it.javaClass.simpleName}\n") }
        finish()
    }
}
