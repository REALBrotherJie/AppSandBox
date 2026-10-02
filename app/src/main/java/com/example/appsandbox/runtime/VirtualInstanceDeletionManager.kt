package com.example.appsandbox.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.example.appsandbox.storage.InstanceStorageManager
import com.example.appsandbox.stub.StubProcessPool
import com.example.appsandbox.stub.StubServices
import com.example.appsandbox.vpm.VirtualPackageRegistry
import com.example.appsandbox.location.VirtualLocationCoordinatorClient
import com.example.appsandbox.m12.M12RuntimeRegistries

data class VirtualInstanceDeleteResult(val slot: Int, val running: Boolean, val storageRemoved: Boolean)

class VirtualInstanceDeletionManager(
    private val context: Context,
    private val registry: VirtualPackageRegistry,
    private val pool: StubProcessPool
) {
    fun deleteVirtualInstance(packageName: String, instanceId: String): VirtualInstanceDeleteResult {
        require(PACKAGE_PATTERN.matches(packageName)) { "invalid packageName" }
        require(INSTANCE_PATTERN.matches(instanceId)) { "invalid instanceId" }
        val before = registry.find(packageName)
        val registered = before?.instances?.get(instanceId)
            ?: return VirtualInstanceDeleteResult(-1, false, false).also {
                Log.i(TAG, "VINSTANCE_DELETE package=$packageName instance=$instanceId virtualUid=-1 slot=-1 " +
                    "process=none running=false cpRoot=null dpRoot=null activityCleanup=none taskCleanup=none " +
                    "processCleanup=none registryCleanup=already-absent storageCleanup=none result=idempotent")
            }
        val slot = registered.processSlot
        val webViewSuffixes = VirtualProcessCoordinatorClient.terminateInstance(context, instanceId)
        val conflicts = registry.readAll().flatMap { it.instances.values }
            .filter { it.instanceId != instanceId && pool.query(it.instanceId) == slot }
        check(conflicts.isEmpty()) { "stub slot p$slot belongs to another instance" }
        val processName = "${context.packageName}:p$slot"
        val process = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .runningAppProcesses.orEmpty().firstOrNull { it.processName == processName }
        val running = process != null
        if (process != null) {
            Process.killProcess(process.pid)
            var attempts = 0
            while (java.io.File("/proc/${process.pid}").exists() && attempts++ < 20) {
                Thread.sleep(50)
            }
        }
        pool.release(instanceId)
        context.stopService(StubServices.intent(context, slot))
        var storageRemoved = false
        registry.deleteInstance(packageName, instanceId) {
            storageRemoved = InstanceStorageManager.delete(context, instanceId, it.dataRoot)
        }
        val webViewStorageRemoved = VirtualWebViewProcessPolicy.deleteDataDirectories(context, webViewSuffixes)
        val locationStateRemoved = VirtualLocationCoordinatorClient.delete(context, instanceId)
        M12RuntimeRegistries.removeInstance(packageName, instanceId)
        val after = registry.find(packageName)
        val recordGone = after?.instances?.containsKey(instanceId) != true
        Log.i(TAG, "VINSTANCE_DELETE package=$packageName instance=$instanceId virtualUid=${registered.virtualUid} " +
            "slot=$slot process=$processName running=$running cpRoot=${registered.dataRoot} " +
            "dpRoot=${registered.dataRoot}/device activityCleanup=${if (running) "process-terminated" else "no-record"} " +
            "taskCleanup=${if (running) "process-terminated" else "no-record"} " +
            "processCleanup=${if (running) "killed" else "not-running"} " +
            "registryCleanup=${if (recordGone) "removed" else "present"} storageCleanup=$storageRemoved " +
            "webViewSuffixes=$webViewSuffixes webViewStorageCleanup=$webViewStorageRemoved locationStateCleanup=$locationStateRemoved result=success")
        return VirtualInstanceDeleteResult(slot, running, storageRemoved)
    }

    companion object {
        private const val TAG = "AppSandbox.M5"
        private val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        private val INSTANCE_PATTERN = Regex("[A-Za-z0-9._-]{1,80}")
    }
}
