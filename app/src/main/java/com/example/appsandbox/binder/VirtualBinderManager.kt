package com.example.appsandbox.binder

import android.content.Context
import android.util.Log
import com.example.appsandbox.activity.GuestActivityStartBridge
import com.example.appsandbox.activity.VirtualActivityManager
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.identity.SystemIdentityBridge
import com.example.appsandbox.vpm.GuestPackageManagerBridge
import com.example.appsandbox.vpm.VirtualPackageManagerService
import com.example.appsandbox.location.GuestProcessLocationBindings

class VirtualBinderManager(
    context: Context,
    private val identity: RuntimeIdentity,
    activityThread: Any,
    vpm: VirtualPackageManagerService,
    activityManager: VirtualActivityManager
) {
    private val identityBridge = SystemIdentityBridge(identity)
    private val content = ContentProviderIdentityAdapter(identity, identityBridge)
    private val contentService = ContentServiceAdapter(context, identity, identityBridge, vpm)
    private val adapters = BinderAdapterRegistry(listOf(
        GuestPackageManagerBridge(identity, vpm, activityThread),
        ActivityManagerAdapter(context, identity, identityBridge, content, vpm),
        GuestActivityStartBridge(context, identity, activityManager),
        AppOpsAdapter(context, identity, identityBridge)
        , contentService
        , NotificationIdentityAdapter(identity)
        , PhysicalPackageServiceAdapter(identity, "media_session", "android.media.session.ISessionManager")
        , PhysicalPackageServiceAdapter(identity, "audio", "android.media.IAudioService")
        , PhysicalPackageServiceAdapter(identity, "uri_grants", "android.app.IUriGrantsManager", optional = true)
        , PhysicalPackageServiceAdapter(identity, "mount", "android.os.storage.IStorageManager", optional = true)
        , PhysicalPackageServiceAdapter(identity, "telephony.registry", "com.android.internal.telephony.ITelephonyRegistry", optional = true)
        , PhysicalPackageServiceAdapter(identity, "clipboard", "android.content.IClipboard", optional = true)
        , PhysicalPackageServiceAdapter(identity, "netstats", "android.net.INetworkStatsService", optional = true)
        , PhysicalPackageServiceAdapter(identity, "appwidget", "com.android.internal.appwidget.IAppWidgetService", optional = true)
        , PhysicalPackageServiceAdapter(identity, "role", "android.app.role.IRoleManager", optional = true)
        , LocationServiceAdapter(context, identity, requireNotNull(GuestProcessLocationBindings.current()) { "Guest process location binding missing" })
    ))

    fun install(): List<AdapterInstallResult> {
        val results = adapters.installAll()
        Log.i("AppSandbox.M6", "VBINDER_CORE api=${android.os.Build.VERSION.SDK_INT} instance=${identity.instanceId} " +
            "virtualUid=${identity.virtualUidNumber} adapters=${results.joinToString { "${it.service}:${if (it.installed) "PASS" else "FAIL"}" }}")
        return results
    }

    fun identityBridge(): SystemIdentityBridge = identityBridge
    fun refreshContentService(): AdapterInstallResult = contentService.install()
}
