package com.example.appsandbox.packageinfo

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.example.appsandbox.contract.GuestViewContract
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.packageinfo.validation.GuestContractRejection
import com.example.appsandbox.packageinfo.validation.GuestContractValidation
import java.io.File

class GuestPackageReader(private val context: Context) {
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
                    android.content.pm.PackageManager.GET_META_DATA.toLong() or
                        android.content.pm.PackageManager.GET_DISABLED_COMPONENTS.toLong() or
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
                    android.content.pm.PackageManager.GET_META_DATA or
                    android.content.pm.PackageManager.GET_DISABLED_COMPONENTS or
                    android.content.pm.PackageManager.GET_ACTIVITIES or
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
        val components = bindIntentFilters(
            normalizeComponents(info, guestId),
            readIntentFilters(apkPath, info.packageName, guestId)
        )
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
            componentSummary = ComponentSummary.fromComponents(components),
            schemaVersion = GuestPackageRecord.CURRENT_SCHEMA_VERSION,
            contractVersion = contract.version,
            components = components
        )
        Log.i(tag, "Parsed ${record.packageName} from $apkPath")
        return record
    }

    companion object {
        const val CONTRACT_VERSION = "com.example.appsandbox.guest.CONTRACT_VERSION"
        const val VIEW_LAYOUT = "com.example.appsandbox.guest.VIEW_LAYOUT"
        const val ACTION_SPEC = "com.example.appsandbox.guest.ACTION_SPEC"

        fun normalizeComponents(info: PackageInfo, revisionId: String): List<GuestComponent> {
            val packageName = info.packageName.takeIf { it.isNotBlank() }
                ?: error("APK has no package name")
            val result = buildList {
                info.activities.orEmpty().forEach {
                    add(normalize(it, GuestComponentType.ACTIVITY, revisionId, packageName, it.permission))
                }
                info.services.orEmpty().forEach {
                    add(normalize(it, GuestComponentType.SERVICE, revisionId, packageName, it.permission))
                }
                info.receivers.orEmpty().forEach {
                    add(normalize(it, GuestComponentType.RECEIVER, revisionId, packageName, it.permission))
                }
                info.providers.orEmpty().forEach {
                    val permissions = listOfNotNull(it.readPermission, it.writePermission)
                    add(normalize(it, GuestComponentType.PROVIDER, revisionId, packageName, *permissions.toTypedArray()))
                }
            }
            if (result.map { it.className to it.type }.toSet().size != result.size) {
                error("Duplicate Guest component declaration")
            }
            return result.sortedWith(compareBy({ it.type.code }, { it.className }))
        }

        private fun normalize(
            info: ComponentInfo,
            type: GuestComponentType,
            revisionId: String,
            packageName: String,
            vararg permissions: String?
        ): GuestComponent {
            val className = GuestComponentNames.normalize(packageName, info.name)
            val normalizedPermissions = permissions.filterNotNull()
                .map(GuestComponentNames::normalizePermission)
                .distinct()
                .sorted()
            return GuestComponent(
                revisionId = revisionId,
                packageName = packageName,
                className = className,
                type = type,
                enabled = info.enabled,
                exported = info.exported,
                declaredPermissions = normalizedPermissions
            )
        }
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
        GuestContractValidation.requireSupportedVersion(version)
        val layoutName = GuestContractValidation.requireMetadata(
            metadata.getString(VIEW_LAYOUT),
            GuestContractRejection.MISSING_LAYOUT
        )
        val layoutId = resources.getIdentifier(layoutName, "layout", packageName)
        GuestContractValidation.requireResource(layoutId, GuestContractRejection.MISSING_LAYOUT_RESOURCE)
        if (version == 1) return GuestViewContract(1, layoutName, null, emptyList())
        val specName = GuestContractValidation.requireMetadata(
            metadata.getString(ACTION_SPEC),
            GuestContractRejection.MISSING_ACTION_SPEC
        )
        val specId = resources.getIdentifier(specName, "raw", packageName)
        GuestContractValidation.requireResource(specId, GuestContractRejection.MISSING_ACTION_RESOURCE)
        val text = resources.openRawResource(specId).bufferedReader().use { it.readText() }
        val (stateView, actions) = GuestContractValidation.parseActionSpec(text)
        GuestContractValidation.validateLayout(resources, layoutId, stateView, actions)
        return GuestViewContract(2, layoutName, stateView, actions)
    }

    private fun readIntentFilters(
        apkPath: String,
        packageName: String,
        revisionId: String
    ): List<com.example.appsandbox.model.resolver.GuestIntentFilter> {
        return GuestIntentFilterManifestParser.parse(
            GuestBinaryXmlManifest.read(apkPath),
            packageName,
            revisionId
        )
    }

    private fun bindIntentFilters(
        components: List<GuestComponent>,
        filters: List<com.example.appsandbox.model.resolver.GuestIntentFilter>
    ): List<GuestComponent> {
        val componentKeys = components.map { it.type to it.className }.toSet()
        if (filters.any { (it.componentType to it.componentClassName) !in componentKeys }) {
            error("Unsupported Guest: intent-filter component is not declared")
        }
        return components.map { component ->
            component.copy(
                intentFilters = filters.filter {
                    it.componentType == component.type &&
                        it.componentClassName == component.className
                }
            )
        }
    }
}
