package com.example.appsandbox.experiments.exp003c

import android.app.Activity
import android.app.Instrumentation
import com.example.appsandbox.experiments.exp003c1.C1Experiment
import com.example.appsandbox.experiments.exp003c1.GateEvidence
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File

object OnCreateExperiment {
    fun run(host: Activity, record: GuestPackageRecord, mode: String): String {
        val expected = GateEvidence.stamp(record, true)
        val gates = listOf("c1", "layout").associateWith { File(host.filesDir, "task15-$it-gate.txt").let { file -> file.exists() && file.readText() == expected } }
        if (gates.values.any { !it }) return "SKIPPED (GATE FAILED): $gates"
        val setup = C1Experiment.create(host, record, "oncreate")
        val c = setup.context
        val lines = mutableListOf("gates=$gates")
        if (mode == "oncreate-error") {
            val clazz = setup.loader.loadClass("com.example.appsandbox.testguest.runtime.Exp003OnCreateThrowingApplication")
            val app = Instrumentation().newApplication(setup.loader, clazz.name, c)
            lines += "callApplicationOnCreate=${runCatching { Instrumentation().callApplicationOnCreate(app); "UNEXPECTED_NO_EXCEPTION" }.getOrElse { "${it.javaClass.name}:${it.message}" }}"
        } else if (mode == "oncreate-read") {
            lines += "persisted=${setup.app.javaClass.getMethod("persistedResults").invoke(setup.app)}"
            lines += "onCreateCalled=${setup.app.javaClass.getField("onCreateCalled").getBoolean(null)}"
        } else {
            lines += "callApplicationOnCreate=${runCatching { Instrumentation().callApplicationOnCreate(setup.app); "RETURNED" }.getOrElse { "${it.javaClass.name}:${it.message}" }}"
            lines += "results=${setup.app.javaClass.getMethod("onCreateResults").invoke(setup.app)}"
            lines += "onCreateCalled=${setup.app.javaClass.getField("onCreateCalled").getBoolean(null)}"
        }
        lines += "host.files.absent=${!File(host.filesDir, "guest-oncreate.txt").exists()}"
        lines += "host.preferences.absent=${!File(host.dataDir, "shared_prefs/guest_prefs.xml").exists()}"
        lines += "host.database.absent=${!host.getDatabasePath("guest-oncreate.db").exists()}"
        lines += "guest.files=${c.getFileStreamPath("guest-oncreate.txt")} exists=${c.getFileStreamPath("guest-oncreate.txt").exists()}"
        lines += "guest.preferences=${c.preferenceFile("guest_prefs")} exists=${c.preferenceFile("guest_prefs").exists()}"
        lines += "guest.database=${c.getDatabasePath("guest-oncreate.db")} exists=${c.getDatabasePath("guest-oncreate.db").exists()}"
        lines += "services=${c.requestedServices()}"
        return lines.joinToString("\n")
    }
}
