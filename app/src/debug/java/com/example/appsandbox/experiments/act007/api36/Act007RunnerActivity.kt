package com.example.appsandbox.experiments.act007.api36

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File
import com.example.appsandbox.experiments.act007.core.C1GuestApplicationSessionExecutor
import com.example.appsandbox.experiments.act007.core.GuestApplicationSessionState

class Act007RunnerActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId") ?: return finish()
        val report = File(filesDir, "task55-$runId.result")
        report.delete()
        var stage = "setup"
        var detail = ""
        runCatching {
            require(android.os.Build.VERSION.SDK_INT == 36)
            val staged = File(requireNotNull(intent.getStringExtra("stagedApk")))
            val revision = GuestStore(this).importApk(staged.inputStream()) { GuestPackageReader(this).read(it, File(it).parentFile!!.name) }
            val throwingPath = intent.getStringExtra("throwingApk")
            val throwingRevision = throwingPath?.let { path -> GuestStore(this).importApk(File(path).inputStream()) { GuestPackageReader(this).read(it, File(it).parentFile!!.name) } }
            val store = GuestInstanceStore(this)
            val a = store.create(revision); val b = store.create(revision)
            val appClass = archiveClass(revision)
            val (controllerA, _) = Act007ApplicationSessions.controller(this, a)
            val expectedA = Act007ApplicationSessions.expected(this, a, revision, appClass)
            val execA = C1GuestApplicationSessionExecutor.create(this, a, revision, appClass)
            stage = "start-a"; val a1 = controllerA.start(Act007ApplicationSessions.request(a, revision, "run-a1", "op-a1", appClass), expectedA, execA)
            check(a1.state == GuestApplicationSessionState.RUNNING && a1.constructorCompleted == 1 && a1.onCreateCompleted == 1)
            stage = "observe-a"; detail = execA.observe().toString(); check(execA.observe()["classLoader"]?.contains("DexClassLoader") == true && execA.observe()["applicationContextIsThis"] == "true")
            stage = "results-a"; val resultsA = execA.onCreateResults(); val persistedA = execA.persistedResults(); detail = resultsA.toString() + persistedA.toString(); check(resultsA.values.count { it == "PASS" } >= 6 && persistedA.values.contains("GUEST_ONCREATE_PREF") && persistedA.values.contains("GUEST_ONCREATE_DB"))
            stage = "stop-a"; check(controllerA.stop("run-a1", "op-stop-a").state == GuestApplicationSessionState.STOPPED)
            val execA2 = C1GuestApplicationSessionExecutor.create(this, a, revision, appClass)
            val a2 = controllerA.start(Act007ApplicationSessions.request(a, revision, "run-a2", "op-a2", appClass), expectedA, execA2)
            val (controllerB, _) = Act007ApplicationSessions.controller(this, b)
            val expectedB = Act007ApplicationSessions.expected(this, b, revision, appClass)
            val execB = C1GuestApplicationSessionExecutor.create(this, b, revision, appClass)
            val b1 = controllerB.start(Act007ApplicationSessions.request(b, revision, "run-b1", "op-b1", appClass), expectedB, execB)
            stage = "dual-running"; check(a2.state == GuestApplicationSessionState.RUNNING && b1.state == GuestApplicationSessionState.RUNNING)
            stage = "files"; check(File(a.dataRoot, "files/guest-oncreate.txt").isFile && File(b.dataRoot, "files/guest-oncreate.txt").isFile)
            stage = "isolation"; detail = "aRoot=${File(a.dataRoot).canonicalPath};bRoot=${File(b.dataRoot).canonicalPath};aObserve=${execA2.observe()};bObserve=${execB.observe()}"; check(File(a.dataRoot).canonicalPath != File(b.dataRoot).canonicalPath)
            val badRevision = requireNotNull(throwingRevision)
            val bad = store.create(badRevision)
            val failureClass = archiveClass(badRevision)
            val (controllerBad, _) = Act007ApplicationSessions.controller(this, bad)
            val execBad = C1GuestApplicationSessionExecutor.create(this, bad, badRevision, failureClass)
            val failure = controllerBad.start(Act007ApplicationSessions.request(bad, badRevision, "run-fail", "op-fail", failureClass), Act007ApplicationSessions.expected(this, bad, badRevision, failureClass), execBad)
            stage = "throwing-state"; check(failure.state == GuestApplicationSessionState.FAILED && failure.constructorCompleted == 1 && failure.onCreateAttempted == 1)
            stage = "throwing-results"; check(failure.detail?.contains("RuntimeException") == true || failure.detail?.contains("onCreate") == true)
            report.writeText("FINAL=1\nstatus=PASS\nrunId=$runId\napi=36\ninstanceA=${a.instanceId}\ninstanceB=${b.instanceId}\nrestart=PASS\ndualIsolation=PASS\nthrowing=${failure.failure}\nthrowingResults=${execBad.onCreateResults()}\napplicationOnCreate=REAL\nactivityAttach=0\nactivityLifecycle=0\n")
        }.onFailure { report.writeText("FINAL=1\nstatus=FAIL\nrunId=$runId\nstage=$stage\ndetail=$detail\nerror=${it.javaClass.name}:${it.message}\n") }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun archiveClass(revision: com.example.appsandbox.model.GuestPackageRecord): String =
        requireNotNull(packageManager.getPackageArchiveInfo(revision.apkPath, 0)?.applicationInfo?.className)
}
