package com.example.appsandbox.experiments.act004a

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.example.appsandbox.experiments.act003.Act003StubActivity
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import dalvik.system.DexClassLoader
import java.io.File
import java.util.UUID

object Act004aNegativeRunner {
    private const val GUEST_CLASS = "com.example.appsandbox.testguest.runtime.GuestMainActivity"

    fun run(host: Activity, record: GuestPackageRecord, invalidMapping: Boolean): String {
        val lines = mutableListOf<String>()
        fun line(value: String) { lines += value }
        fun observe(label: String, block: () -> Any?): String = try {
            "$label=VALUE:${block()}"
        } catch (error: Throwable) {
            "$label=EXCEPTION:${error.javaClass.name}:${error.message}"
        }

        val apk = File(record.apkPath)
        val verification = GuestArtifactVerifier.verify(record)
        val actualSha = GuestArtifactVerifier.sha256(apk)
        line("record.guestPackage=${record.packageName}")
        line("record.guestComponent=$GUEST_CLASS")
        line("record.revisionId=${record.revisionId}")
        line("record.sha256=${record.sha256}")
        line("record.actualSha256=$actualSha")
        line("record.fileSize=${record.fileSize}")
        line("artifact.path=${apk.absolutePath}")
        line("artifact.canWrite=${apk.canWrite()}")
        line("verification=${verification.state}")
        check(verification.state == ArtifactState.VALID) { "${verification.state}:${verification.message}" }
        check(record.sha256.equals(actualSha, true)) { "record SHA mismatch" }

        val launchId = if (invalidMapping) "" else UUID.randomUUID().toString()
        val instanceId = UUID.randomUUID().toString()
        line("launchId=${launchId.ifBlank { "MISSING" }}")
        line("instanceId=$instanceId")
        line("originalAction=${Act003StubActivity.ACTION}")
        line("mapping.phase=RECEIVED")

        if (launchId.isBlank()) {
            line("mappingResult=REJECTED")
            line("mapping.phase=REJECTED_BEFORE_CLASS_LOAD")
            line("guestObjectCreated=false")
            line("guest.attached=false")
            line("guest.lifecycleExecuted=false")
            line("hostSurvived=true")
            return lines.joinToString("\n")
        }

        line("mapping.phase=MAPPED")
        val loader = DexClassLoader(apk.path, host.codeCacheDir.path, null, host.classLoader)
        val guestClass = loader.loadClass(GUEST_CLASS).asSubclass(Activity::class.java)
        line("mapping.phase=CLASS_LOADED")
        line("guest.class=${guestClass.name}")
        line("guest.classLoader=${guestClass.classLoader}")
        line("guest.classLoaderIsDex=${guestClass.classLoader === loader}")
        line("guest.classLoaderDiffersFromHost=${guestClass.classLoader !== host.classLoader}")

        val constructorObject = guestClass.getDeclaredConstructor().newInstance()
        val instrumentationObject = Instrumentation().newActivity(loader, GUEST_CLASS, Intent("task24.object"))
        line("mapping.phase=OBJECT_CREATED")
        line("guestObjectCreated=true")
        line("guest.constructor.class=${constructorObject.javaClass.name}")
        line("guest.instrumentation.class=${instrumentationObject.javaClass.name}")
        line("guest.objectsDistinct=${constructorObject !== instrumentationObject}")
        line("identity.hostClass=${host.javaClass.name}")
        line("identity.hostDiffersFromGuest=${host.javaClass != constructorObject.javaClass}")
        line(observe("guest.getApplication") { constructorObject.application })
        line(observe("guest.getWindow") { constructorObject.window })
        line(observe("guest.getIntent") { constructorObject.intent })
        line(observe("guest.getPackageName") { constructorObject.packageName })
        line("mapping.phase=UNATTACHED")
        line("guest.attached=false")
        line("guest.lifecycleExecuted=false")
        line("guest.tokenWindowTask=NOT_CLAIMED")
        line("guest.inHostContentHierarchy=false")

        val installedBefore = isInstalled(host, record.packageName)
        var directError: Throwable? = null
        val directLaunch = try {
            host.startActivity(Intent().setComponent(ComponentName(record.packageName, GUEST_CLASS)))
            "UNEXPECTED_SUCCESS"
        } catch (error: Throwable) {
            directError = error
            if (error is ActivityNotFoundException) "REJECTED" else "REJECTED:${error.javaClass.name}"
        }
        line("guest.systemInstalled.before=$installedBefore")
        line("directGuestLaunch=$directLaunch")
        line("directGuestExceptionClass=${directError?.javaClass?.name ?: "none"}")
        check(!installedBefore && directLaunch != "UNEXPECTED_SUCCESS")

        host.startActivity(Intent(host, Act003StubActivity::class.java).apply {
            action = Act003StubActivity.ACTION
            putExtra(Act003StubActivity.EXTRA_EXPERIMENT, Act003StubActivity.EXPERIMENT_ACT004A)
            putExtra(Act003StubActivity.EXTRA_LAUNCH_ID, launchId)
            putExtra(Act003StubActivity.EXTRA_INSTANCE_ID, instanceId)
            putExtra(Act003StubActivity.EXTRA_GUEST_PACKAGE, record.packageName)
            putExtra(Act003StubActivity.EXTRA_GUEST_COMPONENT, GUEST_CLASS)
            putExtra(Act003StubActivity.EXTRA_REVISION_ID, record.revisionId)
            putExtra(Act003StubActivity.EXTRA_ORIGINAL_ACTION, Act003StubActivity.ACTION)
        })
        line("hostStub.startActivity=CALLED")
        line("hostStub.component=${ComponentName(host, Act003StubActivity::class.java).flattenToShortString()}")
        line("guest.systemInstalled.after=${isInstalled(host, record.packageName)}")
        line("hostSurvived=true")
        return lines.joinToString("\n")
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean = try {
        activity.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
