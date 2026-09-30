package com.example.appsandbox.virtual

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class VirtualPackageSnapshot(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val nativeLibraryDir: String?,
    val applicationInfo: ApplicationInfo,
    val launcherActivity: ActivityInfo,
    val activities: List<ActivityInfo>,
    val servicesCount: Int,
    val receiversCount: Int,
    val providersCount: Int,
    val signingSummary: String
)

class VirtualPackageSnapshotReader(private val context: Context) {
    fun readInstalled(packageName: String): VirtualPackageSnapshot {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_SIGNING_CERTIFICATES
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(packageName, flags)
        }
        val launcher = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName), 0
        )?.activityInfo ?: error("package $packageName has no launcher activity")
        return fromPackageInfo(info, launcher)
    }

    internal fun fromPackageInfo(info: PackageInfo, launcher: ActivityInfo): VirtualPackageSnapshot {
        val app = ApplicationInfo(requireNotNull(info.applicationInfo))
        val activities = info.activities.orEmpty().map(::ActivityInfo)
        return VirtualPackageSnapshot(
            packageName = info.packageName,
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
            versionName = info.versionName,
            sourceDir = requireNotNull(app.sourceDir),
            splitSourceDirs = app.splitSourceDirs.orEmpty().toList(),
            nativeLibraryDir = app.nativeLibraryDir,
            applicationInfo = app,
            launcherActivity = ActivityInfo(launcher),
            activities = activities,
            servicesCount = info.services.orEmpty().size,
            receiversCount = info.receivers.orEmpty().size,
            providersCount = info.providers.orEmpty().size,
            signingSummary = info.signingInfo?.apkContentsSigners?.joinToString { it.toCharsString().take(24) } ?: "unavailable"
        )
    }
}

data class VirtualInstance(
    val packageName: String,
    val instanceId: String,
    val packageVersionCode: Long,
    val assignedProcessSlot: Int,
    val dataRoot: String,
    val state: State = State.CREATED
) {
    enum class State { CREATED, LAUNCHING, RUNNING, FAILED }
    val virtualUid: String get() = "$packageName:$instanceId"
}
