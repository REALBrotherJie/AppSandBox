package com.example.appsandbox.vpm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.*
import com.example.appsandbox.identity.RuntimeIdentity
import android.os.Parcel

class VirtualPackageManagerService(
    private val source: PackageInfo,
    private val identity: RuntimeIdentity,
    private val dataRoot: String
) {
    fun getPackageInfo(packageName: String): PackageInfo? = source.takeIf { it.packageName == packageName }?.let(::sanitize)
    fun getApplicationInfo(packageName: String): ApplicationInfo? = getPackageInfo(packageName)?.applicationInfo
    fun getActivityInfo(component: ComponentName): ActivityInfo? = getPackageInfo(component.packageName)?.activities?.firstOrNull { it.name == component.className }
    fun getServiceInfo(component: ComponentName): ServiceInfo? = getPackageInfo(component.packageName)?.services?.firstOrNull { it.name == component.className }
    fun getReceiverInfo(component: ComponentName): ActivityInfo? = getPackageInfo(component.packageName)?.receivers?.firstOrNull { it.name == component.className }
    fun getProviderInfo(component: ComponentName): ProviderInfo? = getPackageInfo(component.packageName)?.providers?.firstOrNull { it.name == component.className }
    fun getProviderInfo(authority: String): ProviderInfo? = getPackageInfo(identity.guestPackageName)?.providers
        ?.firstOrNull { authority in it.authority.orEmpty().split(';') }
    fun ownsProviderAuthority(authority: String?): Boolean = authority != null &&
        source.providers.orEmpty().any { provider -> provider.authority?.split(';')?.contains(authority) == true }
    fun getPackagesForUid(uid: Int): Array<String>? = if (uid == identity.virtualUidNumber) arrayOf(identity.guestPackageName) else null

    fun checkPermission(permission: String, packageName: String): Int {
        if (packageName != identity.guestPackageName) return PackageManager.PERMISSION_DENIED
        val index = source.requestedPermissions?.indexOf(permission) ?: -1
        if (index < 0) return PackageManager.PERMISSION_DENIED
        val flags = source.requestedPermissionsFlags?.getOrNull(index) ?: 0
        return if (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    fun sanitizeResolveInfo(info: ResolveInfo?): ResolveInfo? = info?.let { original ->
        ResolveInfo(original).apply {
            activityInfo = original.activityInfo?.let(::sanitize)
            serviceInfo = original.serviceInfo?.let(::sanitize)
            providerInfo = original.providerInfo?.let(::sanitize)
        }
    }

    private fun sanitize(info: PackageInfo) = clone(info).apply {
        packageName = identity.guestPackageName
        applicationInfo = info.applicationInfo?.let(::sanitize)
        activities = info.activities?.map { sanitize(it) }?.toTypedArray()
        services = info.services?.map { sanitize(it) }?.toTypedArray()
        receivers = info.receivers?.map { sanitize(it) }?.toTypedArray()
        providers = info.providers?.map { sanitize(it) }?.toTypedArray()
    }

    private fun sanitize(info: ApplicationInfo) = ApplicationInfo(info).apply {
        packageName = identity.guestPackageName
        uid = identity.virtualUidNumber
        dataDir = dataRoot
        if (android.os.Build.VERSION.SDK_INT >= 24) deviceProtectedDataDir = java.io.File(dataRoot, "device").path
    }
    private fun sanitize(info: ActivityInfo) = ActivityInfo(info).apply { packageName = identity.guestPackageName; applicationInfo = sanitize(info.applicationInfo) }
    private fun sanitize(info: ServiceInfo) = ServiceInfo(info).apply { packageName = identity.guestPackageName; applicationInfo = sanitize(info.applicationInfo) }
    private fun sanitize(info: ProviderInfo) = ProviderInfo(info).apply { packageName = identity.guestPackageName; applicationInfo = sanitize(info.applicationInfo) }

    private fun clone(info: PackageInfo): PackageInfo {
        val parcel = Parcel.obtain()
        return try {
            info.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            PackageInfo.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
