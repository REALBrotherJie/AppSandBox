package com.example.appsandbox.experiments.act003

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object Act003Runner {
    private const val GUEST_CLASS = "com.example.appsandbox.testguest.runtime.GuestMainActivity"

    fun run(host: Activity, record: GuestPackageRecord, invalidLaunchId: Boolean): String {
        val lines = mutableListOf<String>()
        fun line(value: String) { lines += value }
        val apk = File(record.apkPath)
        val verification = GuestArtifactVerifier.verify(record)
        line("record.revisionId=${record.revisionId}")
        line("record.sha256=${record.sha256}")
        line("record.fileSize=${record.fileSize}")
        line("artifact.canRead=${apk.canRead()}")
        line("artifact.canWrite=${apk.canWrite()}")
        line("verification=${verification.state}")
        check(verification.state == ArtifactState.VALID) { "${verification.state}:${verification.message}" }
        check(record.sha256 == sha256(apk)) { "record SHA mismatch" }
        line("guest.systemInstalled.before=${isInstalled(host, record.packageName)}")
        line("host.package=${host.packageName}")
        line("host.component=${host.componentName.flattenToShortString()}")
        line("host.taskId=${host.taskId}")
        line("guestActivityInstantiated=false")

        var directError: Throwable? = null
        val directLaunch = try {
            host.startActivity(Intent().setComponent(ComponentName(record.packageName, GUEST_CLASS)))
            "UNEXPECTED_SUCCESS"
        } catch (error: Throwable) {
            directError = error
            if (error is ActivityNotFoundException) "REJECTED" else "REJECTED:${error.javaClass.name}"
        }
        line("directGuestLaunch=$directLaunch")
        line("directGuestExceptionClass=${directError?.javaClass?.name ?: "none"}")
        check(directLaunch != "UNEXPECTED_SUCCESS") { "Uninstalled Guest unexpectedly launched" }

        val launchId = if (invalidLaunchId) null else UUID.randomUUID().toString()
        val intent = Intent(host, Act003StubActivity::class.java).apply {
            action = Act003StubActivity.ACTION
            if (launchId != null) putExtra(Act003StubActivity.EXTRA_LAUNCH_ID, launchId)
            putExtra(Act003StubActivity.EXTRA_GUEST_PACKAGE, record.packageName)
            putExtra(Act003StubActivity.EXTRA_GUEST_COMPONENT, GUEST_CLASS)
            putExtra(Act003StubActivity.EXTRA_REVISION_ID, record.revisionId)
        }
        line("stub.launchId=${launchId ?: "MISSING"}")
        line("stub.invalidLaunchId=$invalidLaunchId")
        line("stub.component=${ComponentName(host, Act003StubActivity::class.java).flattenToShortString()}")
        host.startActivity(intent)
        line("stub.startActivity=CALLED")
        line("guest.systemInstalled.after=${isInstalled(host, record.packageName)}")
        line("hostSurvived=true")
        return lines.joinToString("\n")
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean = try {
        activity.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
