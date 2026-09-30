package com.example.appsandbox.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.appsandbox.stub.StubActivities
import com.example.appsandbox.virtual.LaunchEnvelope
import com.example.appsandbox.virtual.VirtualInstance
import com.example.appsandbox.virtual.VirtualPackageSnapshotReader
import java.io.File
import java.util.UUID
import com.example.appsandbox.vpm.VirtualInstanceRecord
import com.example.appsandbox.vpm.VirtualPackageRegistry
import com.example.appsandbox.storage.InstanceStorageManager

class VirtualActivityLauncher(private val context: Context) {
    fun launch(packageName: String, instanceId: String, slot: Int): LaunchEnvelope {
        require(instanceId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "invalid instance id" }
        val snapshot = VirtualPackageSnapshotReader(context).readInstalled(packageName)
        val registryFlags = android.content.pm.PackageManager.GET_ACTIVITIES or android.content.pm.PackageManager.GET_SERVICES or
            android.content.pm.PackageManager.GET_RECEIVERS or android.content.pm.PackageManager.GET_PROVIDERS or
            android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(registryFlags.toLong()))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(packageName, registryFlags)
        }
        val registry = VirtualPackageRegistry(context)
        registry.registerPackage(snapshot, packageInfo)
        val dataRoot = InstanceStorageManager(context, instanceId).root
        val instance = VirtualInstance(packageName, instanceId, snapshot.versionCode, slot, dataRoot.path, VirtualInstance.State.LAUNCHING)
        val virtualUid = com.example.appsandbox.identity.RuntimeIdentity.create(context, packageName, instanceId, slot).virtualUidNumber
        registry.registerInstance(packageName, VirtualInstanceRecord(instanceId, virtualUid, slot, dataRoot.path))
        val original = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
            component = android.content.ComponentName(packageName, snapshot.launcherActivity.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val envelope = LaunchEnvelope(packageName, instanceId, requireNotNull(original.component), original,
            snapshot.launcherActivity, slot, UUID.randomUUID().toString(), dataRoot.path)
        val stub = envelope.putInto(StubActivities.intent(context, slot, snapshot.launcherActivity.launchMode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Log.i(TAG, "substitute original=${original.component?.flattenToShortString()} stub=${stub.component?.flattenToShortString()} " +
            "instance=${instance.instanceId} slot=$slot flags=0x${stub.flags.toString(16)} launchMode=${snapshot.launcherActivity.launchMode} " +
            "source=${snapshot.sourceDir} splits=${snapshot.splitSourceDirs} native=${snapshot.nativeLibraryDir}")
        context.startActivity(stub)
        return envelope
    }

    companion object { private const val TAG = "AppSandbox.M2" }
}
