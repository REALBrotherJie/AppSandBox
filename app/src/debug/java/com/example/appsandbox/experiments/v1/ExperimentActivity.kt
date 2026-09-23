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
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

class ExperimentActivity : Activity() {
    companion object { private var runs = 0 }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val mode = intent.getStringExtra("mode") ?: "v1"
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
                store.importApk(File(filesDir, "task15-input.apk").inputStream()) { path ->
                    reader.read(path, File(path).parentFile!!.name)
                }
            } else requireNotNull(store.latestRecord())
            val apk = File(record.apkPath)
            line("imported=${record.apkPath} sha256=${sha(apk)}")
            if (mode == "v1") {
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
        File(filesDir, "task15-$mode.txt").writeText(out.toString())
        setContentView(android.widget.TextView(this).apply { text = out.toString() })
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
