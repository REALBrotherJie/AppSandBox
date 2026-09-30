package com.example.appsandbox.packageinfo

import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import java.io.File

class GuestPackageReader(private val context: Context) {
    fun queryLaunchableInstalledApps(): List<GuestPackageRecord> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return matches.mapNotNull { it.activityInfo?.packageName }.distinct().mapNotNull { packageName ->
            runCatching { readInstalled(packageName) }.getOrNull()
        }.sortedBy { it.appLabel.lowercase() }
    }

    fun readInstalled(packageName: String): GuestPackageRecord {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_DISABLED_COMPONENTS or
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(packageName, flags)
        }
        return toRecord(info, packageName)
    }

    fun validate(apkPath: String): PackageInfo = archiveInfo(apkPath, PackageManager.GET_META_DATA)

    fun readApplicationInfo(apkPath: String) = archiveInfo(apkPath, PackageManager.GET_META_DATA).applicationInfo!!.also {
        it.sourceDir = apkPath
        it.publicSourceDir = apkPath
    }

    fun read(apkPath: String, guestId: String): GuestPackageRecord {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_DISABLED_COMPONENTS or
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = archiveInfo(apkPath, flags)
        info.applicationInfo?.also { it.sourceDir = apkPath; it.publicSourceDir = apkPath }
        return toRecord(info, guestId)
    }

    private fun archiveInfo(path: String, flags: Int): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") context.packageManager.getPackageArchiveInfo(path, flags)
    } ?: error("APK is not readable")

    private fun toRecord(info: PackageInfo, revisionId: String): GuestPackageRecord {
        val app = requireNotNull(info.applicationInfo) { "package has no application info" }
        val components = normalizeComponents(info, revisionId)
        return GuestPackageRecord(
            internalGuestId = revisionId,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
            apkPath = File(app.sourceDir).absolutePath,
            appLabel = context.packageManager.getApplicationLabel(app).toString(),
            importedAt = System.currentTimeMillis(),
            componentSummary = ComponentSummary.fromComponents(components),
            schemaVersion = GuestPackageRecord.CURRENT_SCHEMA_VERSION,
            contractVersion = 0,
            components = components
        )
    }

    companion object {
        fun normalizeComponents(info: PackageInfo, revisionId: String): List<GuestComponent> {
            val packageName = info.packageName.takeIf { it.isNotBlank() } ?: error("package has no name")
            val components = buildList {
                info.activities.orEmpty().forEach { add(normalize(it, GuestComponentType.ACTIVITY, revisionId, packageName, it.permission)) }
                info.services.orEmpty().forEach { add(normalize(it, GuestComponentType.SERVICE, revisionId, packageName, it.permission)) }
                info.receivers.orEmpty().forEach { add(normalize(it, GuestComponentType.RECEIVER, revisionId, packageName, it.permission)) }
                info.providers.orEmpty().forEach { add(normalize(it, GuestComponentType.PROVIDER, revisionId, packageName, it.readPermission, it.writePermission)) }
            }
            check(components.map { it.type to it.className }.toSet().size == components.size) { "Duplicate Guest component declaration" }
            return components.sortedWith(compareBy({ it.type.code }, { it.className }))
        }

        private fun normalize(info: ComponentInfo, type: GuestComponentType, revisionId: String, packageName: String, vararg permissions: String?) =
            GuestComponent(
                revisionId = revisionId,
                packageName = packageName,
                className = GuestComponentNames.normalize(packageName, info.name),
                type = type,
                enabled = info.enabled,
                exported = info.exported,
                declaredPermissions = permissions.filterNotNull().map(GuestComponentNames::normalizePermission).distinct().sorted()
            )
    }
}
