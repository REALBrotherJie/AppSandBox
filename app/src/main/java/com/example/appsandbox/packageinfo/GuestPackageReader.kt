package com.example.appsandbox.packageinfo

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.example.appsandbox.contract.GuestActionSpecParser
import com.example.appsandbox.contract.GuestContractVersions
import com.example.appsandbox.contract.GuestContractResources
import com.example.appsandbox.contract.GuestViewContract
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File

class GuestPackageReader(private val context: Context) {
    companion object {
        const val CONTRACT_VERSION = "com.example.appsandbox.guest.CONTRACT_VERSION"
        const val VIEW_LAYOUT = "com.example.appsandbox.guest.VIEW_LAYOUT"
        const val ACTION_SPEC = "com.example.appsandbox.guest.ACTION_SPEC"
    }
    fun validate(apkPath: String): PackageInfo {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apkPath,
                android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkPath, android.content.pm.PackageManager.GET_META_DATA)
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
                    android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_META_DATA.toLong())
                )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkPath, android.content.pm.PackageManager.GET_META_DATA)
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
                    android.content.pm.PackageManager.GET_META_DATA.toLong() or android.content.pm.PackageManager.GET_ACTIVITIES.toLong() or
                        android.content.pm.PackageManager.GET_SERVICES.toLong() or
                        android.content.pm.PackageManager.GET_RECEIVERS.toLong() or
                        android.content.pm.PackageManager.GET_PROVIDERS.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                apkPath,
                    android.content.pm.PackageManager.GET_META_DATA or android.content.pm.PackageManager.GET_ACTIVITIES or
                    android.content.pm.PackageManager.GET_SERVICES or
                    android.content.pm.PackageManager.GET_RECEIVERS or
                    android.content.pm.PackageManager.GET_PROVIDERS
            )
        } ?: error("The selected file is not a readable APK")

        val appInfo = info.applicationInfo ?: error("APK has no application information")
        appInfo.sourceDir = apkPath
        appInfo.publicSourceDir = apkPath
        val guestResources = packageManager.getResourcesForApplication(appInfo)
        val contract = readContract(appInfo, guestResources, info.packageName)
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
            ),
            contractVersion = contract.version
        )
        Log.i(tag, "Parsed ${record.packageName} from $apkPath")
        return record
    }

    fun readContract(apkPath: String): GuestViewContract {
        val info = readApplicationInfo(apkPath)
        return readContract(info, context.packageManager.getResourcesForApplication(info), info.packageName)
    }

    private fun readContract(
        appInfo: android.content.pm.ApplicationInfo,
        resources: android.content.res.Resources,
        packageName: String
    ): GuestViewContract {
        val metadata = appInfo.metaData ?: error("Unsupported Guest: missing View contract")
        val version = metadata.getInt(CONTRACT_VERSION, 0)
        GuestContractVersions.requireSupported(version)
        val layoutName = metadata.getString(VIEW_LAYOUT)
        require(!layoutName.isNullOrBlank()) { "Unsupported Guest: missing View layout" }
        GuestContractResources.requirePresent(resources.getIdentifier(layoutName, "layout", packageName), "layout")
        if (version == 1) return GuestViewContract(1, layoutName, null, emptyList())
        val specName = metadata.getString(ACTION_SPEC)
        require(!specName.isNullOrBlank()) { "Unsupported Guest: missing action specification" }
        val specId = GuestContractResources.requirePresent(resources.getIdentifier(specName, "raw", packageName), "action specification")
        val text = resources.openRawResource(specId).bufferedReader().use { it.readText() }
        val (stateView, actions) = GuestActionSpecParser.parse(text)
        return GuestViewContract(2, layoutName, stateView, actions)
    }
}
