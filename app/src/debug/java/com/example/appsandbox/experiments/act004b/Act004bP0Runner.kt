package com.example.appsandbox.experiments.act004b

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import com.example.appsandbox.experiments.act003.Act003StubActivity
import java.io.File
import java.lang.reflect.Modifier
import java.util.UUID

object Act004bP0Runner {
    private data class Surface(val name: String, val className: String, val classification: String)

    private val surfaces = listOf(
        Surface("ActivityThread", "android.app.ActivityThread", "INTERNAL,VERSION-SENSITIVE,OEM-SENSITIVE"),
        Surface("ActivityClientRecord", "android.app.ActivityThread\$ActivityClientRecord", "HIDDEN,VERSION-SENSITIVE"),
        Surface("ClientTransaction", "android.app.servertransaction.ClientTransaction", "HIDDEN,VERSION-SENSITIVE"),
        Surface("LaunchActivityItem", "android.app.servertransaction.LaunchActivityItem", "HIDDEN,VERSION-SENSITIVE"),
        Surface("TransactionExecutor", "android.app.servertransaction.TransactionExecutor", "INTERNAL,VERSION-SENSITIVE"),
        Surface("ClientTransactionHandler", "android.app.ClientTransactionHandler", "INTERNAL,VERSION-SENSITIVE"),
        Surface("LoadedApk", "android.app.LoadedApk", "INTERNAL,VERSION-SENSITIVE"),
        Surface("ContextImpl", "android.app.ContextImpl", "INTERNAL,VERSION-SENSITIVE"),
        Surface("Instrumentation", "android.app.Instrumentation", "PUBLIC"),
        Surface("ActivityInfo", "android.content.pm.ActivityInfo", "PUBLIC"),
        Surface("Intent", "android.content.Intent", "PUBLIC"),
        Surface("Window", "android.view.Window", "PUBLIC,INTERNAL_STATE"),
        Surface("IBinder", "android.os.IBinder", "PUBLIC_TOKEN_TYPE")
    )

    fun run(host: Activity): String {
        val lines = mutableListOf<String>()
        fun line(value: String) { lines += value }
        line("p0.phase=HOST_LAUNCH_RECEIVED")
        line("api=${Build.VERSION.SDK_INT}")
        line("host.component=${host.componentName.flattenToShortString()}")
        line("guestActivityConstructed=false")
        line("guestLifecycleExecuted=false")
        line("transactionMutation=NO")
        line("internalInvocation=NO")
        line("hiddenApiBypass=NO")

        var failed = false
        surfaces.forEach { surface ->
            try {
                val clazz = Class.forName(surface.className, false, host.classLoader)
                val methods = clazz.declaredMethods
                    .filter { !it.isSynthetic }
                    .map { it.name }
                    .distinct()
                    .sorted()
                val fields = clazz.declaredFields
                    .filter { !it.isSynthetic }
                    .map { it.name }
                    .distinct()
                    .sorted()
                val nested = clazz.declaredClasses.map { it.name }.sorted()
                line("surface.name=${surface.name}")
                line("surface.className=${clazz.name}")
                line("surface.classFound=true")
                line("surface.reflectionEnumeration=SUCCESS")
                line("surface.methodCount=${methods.size}")
                line("surface.fieldCount=${fields.size}")
                line("surface.methodNames=${methods.take(24).joinToString(",")}")
                line("surface.fieldNames=${fields.take(24).joinToString(",")}")
                line("surface.nestedNames=${nested.take(12).joinToString(",")}")
                line("surface.accessOutcome=DECLARATION_METADATA_ONLY")
                line("surface.classification=${surface.classification}")
                line("surface.runtimeInstanceObserved=NO")
                line("surface.mutationPerformed=NO")
                line("surface.invocationPerformed=NO")
            } catch (error: Throwable) {
                failed = true
                line("surface.name=${surface.name}")
                line("surface.className=${surface.className}")
                line("surface.classFound=false")
                line("surface.reflectionEnumeration=FAILED")
                line("surface.accessOutcome=${error.javaClass.name}:${error.message}")
                line("surface.runtimeInstanceObserved=NO")
                line("surface.mutationPerformed=NO")
                line("surface.invocationPerformed=NO")
            }
        }

        line("p0.phase=STATIC_SURFACE_ONLY")
        line("internalRuntimeInstanceObserved=false")
        line("internalObservationOutcome=${if (failed) "FAILED_CLOSED_OR_PARTIAL" else "STATIC_SURFACE_ONLY"}")
        line("hostStub.startActivity=CALLED")
        host.startActivity(Intent(host, Act003StubActivity::class.java).apply {
            action = Act003StubActivity.ACTION
            putExtra(Act003StubActivity.EXTRA_LAUNCH_ID, UUID.randomUUID().toString())
            putExtra(Act003StubActivity.EXTRA_GUEST_PACKAGE, "")
            putExtra(Act003StubActivity.EXTRA_GUEST_COMPONENT, "")
            putExtra(Act003StubActivity.EXTRA_REVISION_ID, "")
        })
        line("hostFallbackPreserved=true")
        line("hostSurvived=true")
        return lines.joinToString("\n")
    }
}
