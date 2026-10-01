package com.example.appsandbox.binder

import android.content.Context
import android.util.Log
import com.example.appsandbox.activity.GuestActivityStartBridge
import com.example.appsandbox.activity.VirtualActivityManager
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.identity.SystemIdentityBridge
import com.example.appsandbox.vpm.GuestPackageManagerBridge
import com.example.appsandbox.vpm.VirtualPackageManagerService

class VirtualBinderManager(
    context: Context,
    private val identity: RuntimeIdentity,
    activityThread: Any,
    vpm: VirtualPackageManagerService,
    activityManager: VirtualActivityManager
) {
    private val identityBridge = SystemIdentityBridge(identity)
    private val content = ContentProviderIdentityAdapter(identity, identityBridge)
    private val adapters = BinderAdapterRegistry(listOf(
        GuestPackageManagerBridge(identity, vpm, activityThread),
        ActivityManagerAdapter(context, identity, identityBridge, content, vpm),
        GuestActivityStartBridge(context, identity, activityManager),
        AppOpsAdapter(context, identity, identityBridge)
        , NotificationIdentityAdapter(identity)
    ))

    fun install(): List<AdapterInstallResult> {
        val results = adapters.installAll()
        Log.i("AppSandbox.M6", "VBINDER_CORE api=${android.os.Build.VERSION.SDK_INT} instance=${identity.instanceId} " +
            "virtualUid=${identity.virtualUidNumber} adapters=${results.joinToString { "${it.service}:${if (it.installed) "PASS" else "FAIL"}" }}")
        return results
    }

    fun identityBridge(): SystemIdentityBridge = identityBridge
}
