package com.example.appsandbox.experiments.v1

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.experiments.exp001.Exp001Runner
import com.example.appsandbox.experiments.exp002.Exp002Runner
import com.example.appsandbox.experiments.exp003a.Exp003aRunner
import com.example.appsandbox.experiments.exp003b0.Exp003b0Runner
import com.example.appsandbox.experiments.act001.Act001Runner
import com.example.appsandbox.experiments.act002.Act002Runner
import com.example.appsandbox.experiments.act003.Act003Runner
import com.example.appsandbox.experiments.act004a.Act004aNegativeRunner
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

class ExperimentActivity : Activity() {
    companion object { private var runs = 0 }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val mode = intent.getStringExtra("mode") ?: "v1"
        if (state != null) {
            val report = reportFile(mode)
            if (mode == "act001" && report.exists()) {
                File(filesDir, "task19-recreation.txt").writeText("stateRestored=true\nreport=${report.name}\nreportContainsConclusion=${report.readText().contains("conclusion=ACT-001_CONFIRMED_L0")}")
            }
            setContentView(android.widget.TextView(this).apply { text = if (report.exists()) report.readText() else "No completed experiment" })
            return
        }
        val out = StringBuilder()
        fun line(s: String) { out.appendLine(s); Log.i("Task15", s) }
        line("mode=$mode PID=${Process.myPid()} runCount=${++runs} API=${android.os.Build.VERSION.SDK_INT}")
        if (mode == "c1" || mode == "c1-read") File(filesDir, "task15-c1-gate.txt").delete()
        if (mode == "layout") File(filesDir, "task15-layout-gate.txt").delete()
        try {
            check(runs == 1) { "Fresh process required" }
            val store = GuestStore(this)
            val record = if (intent.getBooleanExtra("import", false)) {
                val reader = GuestPackageReader(this)
                val inputName = when (mode) {
                    "act001" -> "task18-input.apk"
                    "act002" -> "task21-input.apk"
                    "act003", "act003-invalid" -> "task22-input.apk"
                    "act004a-negative", "act004a-invalid" -> "task24-input.apk"
                    else -> "task15-input.apk"
                }
                store.importApk(File(filesDir, inputName).inputStream()) { path ->
                    reader.read(path, File(path).parentFile!!.name)
                }
            } else requireNotNull(store.latestRecord())
            val apk = File(record.apkPath)
            line("imported=${record.apkPath} sha256=${sha(apk)}")
            if (mode == "writable-negative") {
                val writable = File(cacheDir, "task16-writable-negative.apk").apply { parentFile!!.mkdirs(); apk.copyTo(this, overwrite = true) }
                line("writable.path=$writable canWrite=${writable.canWrite()}")
                line("writable.load=${runCatching {
                    val loader = DexClassLoader(writable.path, codeCacheDir.path, null, classLoader)
                    loader.loadClass("com.example.appsandbox.testguest.runtime.GuestProbe")
                        .getMethod("ping", String::class.java).invoke(loader.loadClass("com.example.appsandbox.testguest.runtime.GuestProbe").getDeclaredConstructor().newInstance(), "TASK16")
                }.getOrElse { "${it.javaClass.name}:${it.message}" }}")
                writable.delete()
            } else if (mode == "v1") {
                fun load(label: String, file: File) {
                    var step = "constructClassLoader"
                    try {
                        line("$label writable=${file.canWrite()}")
                        val loader = DexClassLoader(file.path, codeCacheDir.path, null, classLoader)
                        step = "loadClass"
                        val clazz = loader.loadClass("com.example.appsandbox.testguest.runtime.GuestProbe")
                        step = "invoke"
                        line("$label result=${clazz.getMethod("ping", String::class.java).invoke(clazz.getDeclaredConstructor().newInstance(), "V1")}")
                    } catch (e: Throwable) { line("$label step=$step exception=${e.javaClass.name}:${e.message}") }
                }
                load("writable", apk)
                val copy = readonly(apk)
                load("readonly", copy)
            } else if (mode == "act001") {
                line(Act001Runner.run(this, record))
            } else if (mode == "act002") {
                line(Act002Runner.run(this, record))
            } else if (mode == "act003" || mode == "act003-invalid") {
                line(Act003Runner.run(this, record, mode == "act003-invalid"))
            } else if (mode == "act004a-negative" || mode == "act004a-invalid") {
                line(Act004aNegativeRunner.run(this, record, mode == "act004a-invalid"))
            } else if (mode == "production-exp001") {
                line("production.canRead=${apk.canRead()} canWrite=${apk.canWrite()} size=${apk.length()}")
                line("production.reopenForWrite=${runCatching { java.io.FileOutputStream(apk, true).use { } ; "UNEXPECTED_SUCCESS" }.getOrElse { "${it.javaClass.name}:${it.message}" }}")
                line(Exp001Runner.run(this, record))
            } else {
                val copy = readonly(apk)
                line("experimentCopy=${copy.path} sha256=${sha(copy)} readOnly=${!copy.canWrite()}")
                val experimental = record.copy(apkPath = copy.path)
                line(when (mode) {
                    "exp001" -> Exp001Runner.run(this, experimental)
                    "exp002" -> Exp002Runner.run(this, experimental)
                    "exp003a" -> Exp003aRunner.run(this, experimental)
                    "exp003b0" -> Exp003b0Runner.run(this, experimental)
                    "c1" -> com.example.appsandbox.experiments.exp003c1.C1Experiment.run(this, experimental)
                    "c1-read" -> com.example.appsandbox.experiments.exp003c1.C1Experiment.run(this, experimental, true)
                    "layout" -> com.example.appsandbox.experiments.exp003c1.LayoutExperiment.run(this, experimental)
                    "oncreate", "oncreate-read", "oncreate-error" -> com.example.appsandbox.experiments.exp003c.OnCreateExperiment.run(this, experimental, mode)
                    "multi-a", "multi-b" -> com.example.appsandbox.experiments.exp003b1.MultiInstanceExperiment.run(this, experimental, mode == "multi-a")
                    else -> error("Unknown experiment $mode")
                })
            }
        } catch (e: Throwable) { line("FAILED=${e.javaClass.name}:${e.message}"); Log.e("Task15", "Failure", e) }
        line("END hostSurvived=true")
        reportFile(mode).writeText(out.toString())
        setContentView(android.widget.TextView(this).apply { text = out.toString() })
        if (mode == "act001" && intent.getBooleanExtra("recreate", false)) {
            window.decorView.postDelayed({ recreate() }, 500L)
        }
    }
    private fun reportFile(mode: String): File = File(filesDir, "${reportPrefix(mode)}-$mode.txt")
    private fun reportPrefix(mode: String): String = when (mode) {
        "act001" -> "task18"
        "act002" -> "task21"
        "act003", "act003-invalid" -> "task22"
        "act004a-negative", "act004a-invalid" -> "task24"
        "production-exp001", "writable-negative" -> "task16"
        else -> "task15"
    }
    private fun readonly(apk: File): File {
        val destination = File(cacheDir, "task15/${java.util.UUID.randomUUID()}/base.apk")
        destination.parentFile!!.mkdirs()
        apk.copyTo(destination)
        check(destination.setReadOnly())
        return destination
    }
    private fun sha(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
