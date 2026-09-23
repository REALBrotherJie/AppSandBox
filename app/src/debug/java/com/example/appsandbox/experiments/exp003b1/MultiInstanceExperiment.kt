package com.example.appsandbox.experiments.exp003b1

import android.app.Activity
import com.example.appsandbox.experiments.exp003c1.C1Experiment
import com.example.appsandbox.model.GuestPackageRecord

object MultiInstanceExperiment {
    fun run(host: Activity, record: GuestPackageRecord, shared: Boolean): String {
        val form = if (shared) "M-A" else "M-B"
        val first = C1Experiment.create(host, record, "$form-instance-1")
        val initialCount = first.app.javaClass.getField("constructorCount").getInt(null)
        val second = C1Experiment.create(host, record, "$form-instance-2", if (shared) first.loader else null)
        val lines = mutableListOf("form=$form", "firstCountBeforeSecond=$initialCount",
            "applicationSame=${first.app === second.app}", "classSame=${first.app.javaClass === second.app.javaClass}",
            "loaderSame=${first.loader === second.loader}", "resourcesSame=${first.context.resources === second.context.resources}",
            "assetsSame=${first.context.assets === second.context.assets}")
        listOf(first, second).forEachIndexed { index, setup ->
            val c = setup.context
            lines += "instance${index + 1} appIdentity=${System.identityHashCode(setup.app)} resourcesIdentity=${System.identityHashCode(c.resources)}"
            lines += "instance${index + 1} constructorCount=${setup.app.javaClass.getField("constructorCount").getInt(null)} applicationContextIsOwn=${c.applicationContext === setup.app} onCreateCalled=${setup.app.javaClass.getField("onCreateCalled").getBoolean(null)}"
            lines += "instance${index + 1} data=${c.dataDir} files=${c.filesDir} cache=${c.cacheDir} codeCache=${c.codeCacheDir} preferences=${c.preferenceFile("multi")} database=${c.getDatabasePath("multi.db")}"
            check(c.getSharedPreferences("multi", 0).edit().clear().commit())
            c.deleteDatabase("multi.db")
        }
        val marker = java.util.UUID.randomUUID().toString()
        check(first.context.getSharedPreferences("multi", 0).edit().putString("marker", marker).commit())
        C1Experiment.Database(first.context, "multi.db").use { it.write(marker) }
        val secondPrefs = second.context.getSharedPreferences("multi", 0).getString("marker", null)
        val secondDb = C1Experiment.Database(second.context, "multi.db").use { it.read() }
        lines += "instance1.preferencesReadback=${first.context.getSharedPreferences("multi", 0).getString("marker", null) == marker}"
        lines += "instance1.databaseReadback=${C1Experiment.Database(first.context, "multi.db").use { it.read() } == marker}"
        lines += "instance2.preferencesSeesMarker=${secondPrefs == marker} instance2.databaseSeesMarker=${secondDb == marker}"
        lines += "host.preferencesAbsent=${!java.io.File(host.dataDir, "shared_prefs/multi.xml").exists()} host.databaseAbsent=${!host.getDatabasePath("multi.db").exists()}"
        lines += "scope=JAVA_STATIC_AND_CONTEXT_STORAGE_ONLY; CALLBACK_DISPATCH_NOT_TESTED; NO_ONCREATE"
        return lines.joinToString("\n")
    }
}
