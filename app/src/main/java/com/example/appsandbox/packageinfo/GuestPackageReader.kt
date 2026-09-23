package com.example.appsandbox.packageinfo

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File

class GuestPackageReader(private val context: Context) {
    fun validate(apkPath: String): PackageInfo {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apkPath,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkPath, 0)
        } ?: error("Selected file is not a readable APK")
        require(!info.packageName.isNullOrBlank()) { "Selected APK has no package name" }
        Log.i(tag, "Validated APK package=${info.packageName} path=$apkPath")
        return info
    }

    private val tag = "AppSandbox.Package"
    fun readApplicationInfo(apkPath: String): android.content.pm.ApplicationInfo {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apkPath,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkPath, 0)
        } ?: error("The selected file is not a readable APK")
        return info.applicationInfo?.also {
            it.sourceDir = apkPath
            it.publicSourceDir = apkPath
        } ?: error("APK has no application information")
    }

    fun read(apkPath: String, guestId: String): GuestPackageRecord {
        val packageManager = context.packageManager
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apkPath,
                android.content.pm.PackageManager.PackageInfoFlags.of(
                    android.content.pm.PackageManager.GET_ACTIVITIES.toLong() or
                        android.content.pm.PackageManager.GET_SERVICES.toLong() or
                        android.content.pm.PackageManager.GET_RECEIVERS.toLong() or
                        android.content.pm.PackageManager.GET_PROVIDERS.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                apkPath,
                android.content.pm.PackageManager.GET_ACTIVITIES or
                    android.content.pm.PackageManager.GET_SERVICES or
                    android.content.pm.PackageManager.GET_RECEIVERS or
                    android.content.pm.PackageManager.GET_PROVIDERS
            )
        } ?: error("The selected file is not a readable APK")

        val appInfo = info.applicationInfo ?: error("APK has no application information")
        appInfo.sourceDir = apkPath
        appInfo.publicSourceDir = apkPath
        val label = packageManager.getApplicationLabel(appInfo).toString()
        val record = GuestPackageRecord(
            internalGuestId = guestId,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            },
            apkPath = File(apkPath).absolutePath,
            appLabel = label,
            importedAt = System.currentTimeMillis(),
            componentSummary = ComponentSummary(
                info.activities?.size ?: 0,
                info.services?.size ?: 0,
                info.receivers?.size ?: 0,
                info.providers?.size ?: 0
            )
        )
        Log.i(tag, "Parsed ${record.packageName} from $apkPath")
        return record
    }
}
