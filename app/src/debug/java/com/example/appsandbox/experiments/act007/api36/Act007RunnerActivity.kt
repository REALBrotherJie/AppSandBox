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
            val expectedApi = intent.getIntExtra("expectedApi", 36)
            require(android.os.Build.VERSION.SDK_INT == expectedApi)
            if (intent.getStringExtra("mode") == "recover") {
                val instance = requireNotNull(GuestInstanceStore(this).get(requireNotNull(intent.getStringExtra("instanceId"))))
                val revision = requireNotNull(GuestStore(this).findRevision(instance.guestRevisionId))
                val appClass = archiveClass(revision)
                val (controller, _) = Act007ApplicationSessions.controller(this, instance)
                val recovered = controller.recoverInterrupted()
                stage = "recovery-state"
                check(recovered.any { it.request.runId == intent.getStringExtra("oldRunId") && it.failure == com.example.appsandbox.experiments.act007.core.GuestApplicationFailure.CRASH_RECOVERY })
                val executor = C1GuestApplicationSessionExecutor.create(this, instance, revision, appClass)
                val newRun = runId + "-recovered"
                val started = controller.start(Act007ApplicationSessions.request(instance, revision, newRun, "op-$newRun", appClass), Act007ApplicationSessions.expected(this, instance, revision, appClass), executor)
                stage = "recovery-start"
                check(started.state == GuestApplicationSessionState.RUNNING)
                stage = "recovery-marker"
                detail = executor.observe().toString()
                checkProbe(executor.persistedResults(), requireNotNull(intent.getStringExtra("expectedProbe")))
                checkResults(executor.onCreateResults())
                report.writeText("FINAL=1\nstatus=PASS\nrunId=$runId\nrecovery=CRASH_RECOVERY\nnewRun=$newRun\n")
                return@runCatching
            }
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
            val probeA = "A-" + java.util.UUID.randomUUID().toString()
            val probeB = "B-" + java.util.UUID.randomUUID().toString()
            File(a.dataRoot, "files").mkdirs(); File(a.dataRoot, "files/probe-seed").writeText(probeA)
            File(b.dataRoot, "files").mkdirs(); File(b.dataRoot, "files/probe-seed").writeText(probeB)
            val prefix = runId
            stage = "start-a"; val a1 = controllerA.start(Act007ApplicationSessions.request(a, revision, "$prefix-a1", "$prefix-op-a1", appClass), expectedA, execA)
            check(a1.state == GuestApplicationSessionState.RUNNING && a1.constructorCompleted == 1 && a1.onCreateCompleted == 1)
            stage = "observe-a"; detail = execA.observe().toString(); checkObserve(execA.observe(), a.dataRoot); checkProbe(execA.persistedResults(), probeA); checkResults(execA.onCreateResults())
            if (intent.getBooleanExtra("holdAfterStart", false)) {
                report.writeText("READY=1\nrunId=$runId\ninstanceA=${a.instanceId}\noldRunId=$prefix-a1\nexpectedProbe=$probeA\n")
                android.os.Handler(mainLooper).postDelayed({}, 60000)
                return@runCatching
            }
            stage = "results-a"; val resultsA = execA.onCreateResults(); val persistedA = execA.persistedResults(); detail = resultsA.toString() + persistedA.toString();
            check(resultsA.isNotEmpty())
            checkResults(resultsA)
            check(persistedA["preferences"] == "GUEST_ONCREATE_PREF" && persistedA["database"] == "GUEST_ONCREATE_DB")
            stage = "duplicate-command"; check(controllerA.start(Act007ApplicationSessions.request(a, revision, "$prefix-a1", "$prefix-op-a1", appClass), expectedA, execA).state == GuestApplicationSessionState.RUNNING)
            check(controllerA.start(Act007ApplicationSessions.request(a, revision, "$prefix-a1", "$prefix-op-a1-new", appClass), expectedA, execA).failure == com.example.appsandbox.experiments.act007.core.GuestApplicationFailure.DUPLICATE_RUN)
            stage = "stop-a"; check(controllerA.stop("$prefix-a1", "$prefix-op-stop-a").state == GuestApplicationSessionState.STOPPED)
            val execA2 = C1GuestApplicationSessionExecutor.create(this, a, revision, appClass)
            val a2 = controllerA.start(Act007ApplicationSessions.request(a, revision, "$prefix-a2", "$prefix-op-a2", appClass), expectedA, execA2)
            val (controllerB, _) = Act007ApplicationSessions.controller(this, b)
            val expectedB = Act007ApplicationSessions.expected(this, b, revision, appClass)
            val execB = C1GuestApplicationSessionExecutor.create(this, b, revision, appClass)
            val b1 = controllerB.start(Act007ApplicationSessions.request(b, revision, "$prefix-b1", "$prefix-op-b1", appClass), expectedB, execB)
            stage = "dual-running"; check(a2.state == GuestApplicationSessionState.RUNNING && b1.state == GuestApplicationSessionState.RUNNING)
            checkResults(execA2.onCreateResults()); checkResults(execB.onCreateResults())
            checkObserve(execA2.observe(), a.dataRoot); checkObserve(execB.observe(), b.dataRoot)
            checkProbe(execA2.persistedResults(), probeA); checkProbe(execB.persistedResults(), probeB); check(probeA != probeB)
            stage = "files"; check(File(a.dataRoot, "files/guest-oncreate.txt").readText() == "GUEST_ONCREATE_FILE" && File(b.dataRoot, "files/guest-oncreate.txt").readText() == "GUEST_ONCREATE_FILE")
            stage = "isolation"; detail = "aRoot=${File(a.dataRoot).canonicalPath};bRoot=${File(b.dataRoot).canonicalPath};aObserve=${execA2.observe()};bObserve=${execB.observe()}"; check(File(a.dataRoot).canonicalPath != File(b.dataRoot).canonicalPath)
            val badRevision = requireNotNull(throwingRevision)
            val bad = store.create(badRevision)
            val failureClass = archiveClass(badRevision)
            val (controllerBad, _) = Act007ApplicationSessions.controller(this, bad)
            val execBad = C1GuestApplicationSessionExecutor.create(this, bad, badRevision, failureClass)
            val failure = controllerBad.start(Act007ApplicationSessions.request(bad, badRevision, "run-fail", "op-fail", failureClass), Act007ApplicationSessions.expected(this, bad, badRevision, failureClass), execBad)
            stage = "throwing-state"; check(failure.state == GuestApplicationSessionState.FAILED && failure.failure == com.example.appsandbox.experiments.act007.core.GuestApplicationFailure.ON_CREATE_FAILED && failure.constructorAttempted == 1 && failure.constructorCompleted == 1 && failure.onCreateAttempted == 1 && failure.onCreateCompleted == 0)
            stage = "throwing-results"; check(failure.detail?.contains("RuntimeException") == true || failure.detail?.contains("onCreate") == true)
            stage = "failure-does-not-block-b"; check(controllerB.snapshots().any { it.request.runId == "$prefix-b1" && it.state == GuestApplicationSessionState.RUNNING })
            stage = "delete-isolation"; check(controllerB.stop("$prefix-b1", "$prefix-op-stop-b").state == GuestApplicationSessionState.STOPPED); check(controllerA.stop("$prefix-a2", "$prefix-op-stop-a2").state == GuestApplicationSessionState.STOPPED)
            check(store.delete(b.instanceId)); check(File(a.dataRoot).isDirectory && !File(b.dataRoot).exists())
            val survivorProbe = "A-SURVIVOR-" + java.util.UUID.randomUUID().toString()
            File(a.dataRoot, "files/probe-seed").writeText(survivorProbe)
            val execA3 = C1GuestApplicationSessionExecutor.create(this, a, revision, appClass)
            val a3 = controllerA.start(Act007ApplicationSessions.request(a, revision, "$prefix-a3", "$prefix-op-a3", appClass), expectedA, execA3)
            check(a3.state == GuestApplicationSessionState.RUNNING); checkResults(execA3.onCreateResults()); checkProbe(execA3.persistedResults(), survivorProbe); checkObserve(execA3.observe(), a.dataRoot)
            report.writeText("FINAL=1\nstatus=PASS\nrunId=$runId\napi=$expectedApi\ninstanceA=${a.instanceId}\ninstanceB=${b.instanceId}\nrestart=PASS\ndualIsolation=PASS\nthrowing=${failure.failure}\nthrowingResults=${execBad.onCreateResults()}\napplicationOnCreate=REAL\nactivityAttach=0\nactivityLifecycle=0\n")
        }.onFailure { report.writeText("FINAL=1\nstatus=FAIL\nrunId=$runId\nstage=$stage\ndetail=$detail\nerror=${it.javaClass.name}:${it.message}\n") }
        if (!intent.getBooleanExtra("holdAfterStart", false)) finish()
    }

    @Suppress("DEPRECATION")
    private fun archiveClass(revision: com.example.appsandbox.model.GuestPackageRecord): String =
        requireNotNull(packageManager.getPackageArchiveInfo(revision.apkPath, 0)?.applicationInfo?.className)

    private fun checkResults(results: Map<String, String>) {
        (1..8).forEach { number -> check(results.entries.any { it.key.startsWith("$number.") && it.value == "PASS" }) }
        check(results["clipboardClass"]?.contains("ClipboardManager") == true)
    }

    private fun checkProbe(results: Map<String, String>, expected: String) {
        check(results["probeFile"] == expected)
        check(results["probePreference"] == expected)
    }

    private fun checkObserve(values: Map<String, String>, dataRoot: String) {
        check(values["classLoader"]?.contains("DexClassLoader") == true)
        check(values["applicationContextIsThis"] == "true")
        check(values["filesDir"] == File(dataRoot, "files").canonicalPath)
        check(values["applicationInfoDataDir"] == File(dataRoot).canonicalPath)
    }
}
