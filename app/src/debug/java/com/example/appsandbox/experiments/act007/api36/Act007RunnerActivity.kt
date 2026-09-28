package com.example.appsandbox.experiments.act007.api36

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

class Act007RunnerActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId") ?: return finish()
        val report = File(filesDir, "task55-$runId.result")
        report.delete()
        runCatching {
            require(android.os.Build.VERSION.SDK_INT == 36)
            val staged = File(requireNotNull(intent.getStringExtra("stagedApk")))
            val revision = GuestStore(this).importApk(staged.inputStream()) { GuestPackageReader(this).read(it, File(it).parentFile!!.name) }
            val store = GuestInstanceStore(this)
            val a = store.create(revision); val b = store.create(revision)
            val a1 = Act007ApplicationSessions.start(this, a, revision)
            check(a1.outcome == "RUNNING" && a1.constructor == 1 && a1.onCreate == 1)
            check(Act007ApplicationSessions.isActive(a.instanceId) && !Act007ApplicationSessions.isActive(b.instanceId))
            check(Act007ApplicationSessions.stop(a))
            val a2 = Act007ApplicationSessions.start(this, a, revision)
            val b1 = Act007ApplicationSessions.start(this, b, revision)
            check(a2.outcome == "RUNNING" && b1.outcome == "RUNNING")
            check(File(a.dataRoot, "files/guest-oncreate.txt").isFile && File(b.dataRoot, "files/guest-oncreate.txt").isFile)
            check(File(a.dataRoot).canonicalPath != File(b.dataRoot).canonicalPath)
            val bad = store.create(revision)
            val failure = Act007ApplicationSessions.start(this, bad, revision, "com.example.appsandbox.testguest.runtime.Exp003OnCreateThrowingApplication")
            check(failure.outcome == "FAILED" && failure.constructor == 1 && failure.onCreate == 1)
            report.writeText("FINAL=1\nstatus=PASS\nrunId=$runId\napi=36\ninstanceA=${a.instanceId}\ninstanceB=${b.instanceId}\nrestart=PASS\ndualIsolation=PASS\nthrowing=${failure.error}\napplicationOnCreate=REAL\nactivityAttach=0\nactivityLifecycle=0\n")
        }.onFailure { report.writeText("FINAL=1\nstatus=FAIL\nrunId=$runId\nerror=${it.javaClass.name}:${it.message}\n") }
        finish()
    }
}
