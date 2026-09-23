package com.example.appsandbox.experiments.exp003c1

import android.app.Activity
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File

object LayoutExperiment {
    fun run(host: Activity, record: GuestPackageRecord): String {
        val c = C1Experiment.create(host, record, "layout").context
        val output = mutableListOf<String>()
        var ordinary = false
        for (name in listOf("exp002_test_layout", "exp003_themed_layout")) {
            try {
                val id = c.resources.getIdentifier(name, "layout", c.packageName)
                val view = LayoutInflater.from(c).inflate(id, null) as ViewGroup
                val child = view.getChildAt(0) as TextView
                val text = child.text.toString() == C1Experiment.MARKER
                val context = view.context === c && child.context === c
                val color = child.currentTextColor == 0xff12ab34.toInt()
                output += "$name root=${view.javaClass.name} child=${child.javaClass.name} text=${child.text} contextIsC1=$context color=${Integer.toHexString(child.currentTextColor)}"
                output += "$name=${if (text && context && (name != "exp003_themed_layout" || color)) "PASS" else "FAIL"}"
                if (name == "exp002_test_layout") ordinary = text && context
            } catch (e: Throwable) { output += "$name=FAIL ${e.javaClass.name}:${e.message}" }
        }
        val land = c.createConfigurationContext(Configuration(c.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE })
        val landView = LayoutInflater.from(land).inflate(land.resources.getIdentifier("exp002_test_layout", "layout", c.packageName), null)
        val value = land.getString(land.resources.getIdentifier("exp002_config_value", "string", c.packageName))
        output += "landscape=$value contextIsDerived=${landView.context === land}"
        output += "hostInflater=${runCatching {
            val view = LayoutInflater.from(host).inflate(c.resources.getIdentifier("exp002_test_layout", "layout", c.packageName), null)
            "${view.javaClass.name}:context=${view.context.javaClass.name}"
        }.getOrElse { "${it.javaClass.name}:${it.message}" }}"
        File(host.filesDir, "task15-layout-gate.txt").writeText("${android.os.Build.VERSION.SDK_INT}:$ordinary")
        output += "gate.layout=$ordinary"
        return output.joinToString("\n")
    }
}
