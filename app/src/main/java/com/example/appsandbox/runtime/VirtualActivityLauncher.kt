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

class VirtualActivityLauncher(private val context: Context) {
    fun launch(packageName: String, instanceId: String, slot: Int): LaunchEnvelope {
        require(instanceId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "invalid instance id" }
        val snapshot = VirtualPackageSnapshotReader(context).readInstalled(packageName)
        require(snapshot.launcherActivity.launchMode == android.content.pm.ActivityInfo.LAUNCH_MULTIPLE) {
            "M2 only supports standard launcher activities"
        }
        val dataRoot = File(context.filesDir, "virtual/instances/$instanceId").canonicalFile.apply { mkdirs() }
        val instance = VirtualInstance(packageName, instanceId, snapshot.versionCode, slot, dataRoot.path, VirtualInstance.State.LAUNCHING)
        val original = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
            component = android.content.ComponentName(packageName, snapshot.launcherActivity.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val envelope = LaunchEnvelope(packageName, instanceId, requireNotNull(original.component), original,
            snapshot.launcherActivity, slot, UUID.randomUUID().toString(), dataRoot.path)
        val stub = envelope.putInto(StubActivities.standardIntent(context, slot).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Log.i(TAG, "substitute original=${original.component?.flattenToShortString()} stub=${stub.component?.flattenToShortString()} " +
            "instance=${instance.instanceId} slot=$slot flags=0x${stub.flags.toString(16)} launchMode=standard " +
            "source=${snapshot.sourceDir} splits=${snapshot.splitSourceDirs} native=${snapshot.nativeLibraryDir}")
        context.startActivity(stub)
        return envelope
    }

    companion object { private const val TAG = "AppSandbox.M2" }
}
