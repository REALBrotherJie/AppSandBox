package com.example.appsandbox.experiments.act001

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.example.appsandbox.experiments.exp003c1.Exp003c1ControlledContext
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

object Act001Runner {
    private const val MARKER = "EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10"
    private const val GUEST_LAYOUT = "exp003_themed_layout"
    private const val GUEST_ACTIVITY = "com.example.appsandbox.testguest/.runtime.GuestMainActivity"

    fun run(host: Activity, record: GuestPackageRecord): String {
        val lines = mutableListOf<String>()
        fun line(value: String) { lines += value }
        val apk = File(record.apkPath)
        val verification = GuestArtifactVerifier.verify(record)
        line("record.revisionId=${record.revisionId}")
        line("record.sha256=${record.sha256}")
        line("record.fileSize=${record.fileSize}")
        line("verification=${verification.state}")
        check(verification.state == ArtifactState.VALID) { "${verification.state}:${verification.message}" }
        check(record.sha256 == sha256(apk)) { "record SHA mismatch" }

        val loader = DexClassLoader(apk.path, host.codeCacheDir.path, null, host.classLoader)
        @Suppress("DEPRECATION")
        val archive = ApplicationInfo(host.packageManager.getPackageArchiveInfo(apk.path, 0)!!.applicationInfo!!).apply {
            sourceDir = apk.path
            publicSourceDir = apk.path
        }
        val resources = host.packageManager.getResourcesForApplication(archive)
        val guestContext = Exp003c1ControlledContext(
            host.applicationContext, loader, resources, archive,
            File(host.filesDir, "task18-act001"), "act001"
        )
        val layoutId = resources.getIdentifier(GUEST_LAYOUT, "layout", record.packageName)
        check(layoutId != 0) { "Guest layout not found: $GUEST_LAYOUT" }
        val guestView = LayoutInflater.from(guestContext).inflate(layoutId, null)
        val child = (guestView as? ViewGroup)?.getChildAt(0) as? TextView
            ?: error("Guest layout has no first TextView child")
        host.setContentView(guestView)
        val marker = child.text.toString()
        line("guest.logical.package=${record.packageName}")
        line("guest.logical.component=$GUEST_ACTIVITY")
        line("guest.layout=$GUEST_LAYOUT")
        line("guest.marker=$marker")
        line("guest.markerPresent=${marker == MARKER}")
        line("guest.themeId=${guestContext.selectedTheme()}")
        line("guest.view.class=${guestView.javaClass.name}")
        line("guest.child.class=${child.javaClass.name}")
        line("guest.view.context=${guestView.context.javaClass.name}")
        line("guest.view.contextIsControlled=${guestView.context === guestContext}")
        line("guest.child.contextIsControlled=${child.context === guestContext}")
        line("guest.view.contextPackage=${guestView.context.packageName}")
        line("guest.view.resourcesPackage=${guestView.resources.getResourcePackageName(layoutId)}")

        line("host.package=${host.packageName}")
        line("host.component=${host.componentName.flattenToShortString()}")
        line("host.taskId=${host.taskId}")
        line("host.window.class=${host.window.javaClass.name}")
        line("host.window.decor.class=${host.window.decorView.javaClass.name}")
        line("guest.systemInstalled=${isInstalled(host, record.packageName)}")
        line("guest.activityObjectInstantiated=false")
        line("activityTokenValue=NOT_READ_PUBLIC_ONLY")
        line("guest.taskIdentity=NOT_CLAIMED")

        var directException: Throwable? = null
        val direct = try {
            host.startActivity(Intent().setComponent(ComponentName(record.packageName, GUEST_ACTIVITY.substringAfter('/'))))
            "UNEXPECTED_SUCCESS"
        } catch (error: Throwable) {
            directException = error
            if (error is ActivityNotFoundException) "REJECTED" else "REJECTED:${error.javaClass.name}"
        }
        line("directGuestLaunch=$direct")
        line("directGuestExceptionClass=${directException?.javaClass?.name ?: "none"}")
        line("directGuestExceptionMessage=${directException?.message ?: "none"}")
        line("hostSurvived=true")
        check(marker == MARKER) { "Guest marker missing" }
        check(guestView.context === guestContext && child.context === guestContext) { "Guest context evidence missing" }
        check(direct != "UNEXPECTED_SUCCESS") { "Uninstalled Guest unexpectedly launched" }
        line("conclusion=ACT-001_CONFIRMED_L0")
        return lines.joinToString("\n")
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean = runCatching {
        activity.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
