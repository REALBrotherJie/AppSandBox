package com.example.appsandbox.experiments.act002

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

object Act002Runner {
    private const val GUEST_CLASS = "com.example.appsandbox.testguest.runtime.GuestMainActivity"
    private const val MISSING_CLASS = "com.example.appsandbox.testguest.runtime.MissingActivity"
    private const val NON_ACTIVITY_CLASS = "com.example.appsandbox.testguest.runtime.GuestProbe"

    fun run(host: Activity, record: GuestPackageRecord): String {
        val lines = mutableListOf<String>()
        fun line(value: String) { lines += value }
        fun observe(label: String, block: () -> Any?): String = try {
            "$label=VALUE:${block()}"
        } catch (error: Throwable) {
            "$label=EXCEPTION:${error.javaClass.name}:${error.message}"
        }

        val apk = File(record.apkPath)
        val verification = GuestArtifactVerifier.verify(record)
        line("record.revisionId=${record.revisionId}")
        line("record.sha256=${record.sha256}")
        line("record.fileSize=${record.fileSize}")
        line("artifact.path=${apk.absolutePath}")
        line("artifact.canRead=${apk.canRead()}")
        line("artifact.canWrite=${apk.canWrite()}")
        line("verification=${verification.state}")
        check(verification.state == ArtifactState.VALID) { "${verification.state}:${verification.message}" }
        check(record.sha256 == sha256(apk)) { "record SHA mismatch" }

        val preInstalled = isInstalled(host, record.packageName)
        val preTaskId = host.taskId
        val preComponent = host.componentName.flattenToShortString()
        line("system.pre.guestInstalled=$preInstalled")
        line("system.pre.hostPackage=${host.packageName}")
        line("system.pre.hostComponent=$preComponent")
        line("system.pre.hostTaskId=$preTaskId")

        val loader = DexClassLoader(apk.path, host.codeCacheDir.path, null, host.classLoader)
        val guestClass = loader.loadClass(GUEST_CLASS)
        line("classLoad=SUCCESS")
        line("guestClass=${guestClass.name}")
        line("guestClassLoader=${guestClass.classLoader}")
        line("guestClassLoaderIsGuest=${guestClass.classLoader === loader}")
        line("hostClassLoader=${host.classLoader}")
        line("guestLoaderDiffersFromHost=${guestClass.classLoader !== host.classLoader}")
        line("assignableToActivity=${Activity::class.java.isAssignableFrom(guestClass)}")
        check(Activity::class.java.isAssignableFrom(guestClass)) { "Guest class is not Activity" }

        @Suppress("UNCHECKED_CAST")
        val activityClass = guestClass as Class<out Activity>
        val directObject = activityClass.getDeclaredConstructor().newInstance()
        line("constructor=SUCCESS")
        line("constructor.objectClass=${directObject.javaClass.name}")
        line("constructor.objectClassLoader=${directObject.javaClass.classLoader}")

        val instrumentationObject = Instrumentation().newActivity(loader, GUEST_CLASS, Intent("task21.instrumentation"))
        line("instrumentation.newActivity=SUCCESS")
        line("instrumentation.objectClass=${instrumentationObject.javaClass.name}")
        line("objects.distinct=${directObject !== instrumentationObject}")
        line("constructionCount=${guestClass.getMethod("constructionCount").invoke(null)}")

        listOf("constructor" to directObject, "instrumentation" to instrumentationObject).forEach { (label, activity) ->
            line(observe("$label.getApplication") { activity.application })
            line(observe("$label.getWindow") { activity.window })
            line(observe("$label.getIntent") { activity.intent })
            line(observe("$label.isFinishing") { activity.isFinishing })
            line(observe("$label.isDestroyed") { activity.isDestroyed })
            line(observe("$label.getPackageName") { activity.packageName })
        }

        val missing = runCatching { loader.loadClass(MISSING_CLASS) }
            .fold({ "UNEXPECTED_SUCCESS" }, { "${it.javaClass.name}:${it.message}" })
        line("negative.missingClass=$missing")
        check(missing.startsWith(ClassNotFoundException::class.java.name)) { "Missing class control failed" }
        val nonActivity = loader.loadClass(NON_ACTIVITY_CLASS)
        val cast = runCatching { nonActivity.asSubclass(Activity::class.java) }
            .fold({ "UNEXPECTED_SUCCESS" }, { "${it.javaClass.name}:${it.message}" })
        line("negative.nonActivityCast=$cast")
        check(cast.startsWith(ClassCastException::class.java.name)) { "Non-Activity control failed" }

        var launchError: Throwable? = null
        val directLaunch = try {
            host.startActivity(Intent().setComponent(ComponentName(record.packageName, GUEST_CLASS)))
            "UNEXPECTED_SUCCESS"
        } catch (error: Throwable) {
            launchError = error
            if (error is ActivityNotFoundException) "REJECTED" else "REJECTED:${error.javaClass.name}"
        }
        line("directGuestLaunch=$directLaunch")
        line("directGuestExceptionClass=${launchError?.javaClass?.name ?: "none"}")
        line("directGuestExceptionMessage=${launchError?.message ?: "none"}")

        val postInstalled = isInstalled(host, record.packageName)
        line("system.post.guestInstalled=$postInstalled")
        line("system.post.hostPackage=${host.packageName}")
        line("system.post.hostComponent=${host.componentName.flattenToShortString()}")
        line("system.post.hostTaskId=${host.taskId}")
        line("system.hostComponentUnchanged=${host.componentName.flattenToShortString() == preComponent}")
        line("system.hostTaskIdUnchanged=${host.taskId == preTaskId}")
        line("guest.attached=false")
        line("guest.lifecycleExecuted=false")
        line("guest.tokenWindowTask=NOT_CLAIMED")
        line("hostSurvived=true")
        check(!preInstalled && !postInstalled) { "Guest installation state changed" }
        check(directLaunch != "UNEXPECTED_SUCCESS") { "Uninstalled Guest unexpectedly launched" }
        check(host.taskId == preTaskId) { "Host task changed" }
        line("conclusion=ACT-002_CONFIRMED_JAVA_OBJECT_ONLY")
        return lines.joinToString("\n")
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean = try {
        activity.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
