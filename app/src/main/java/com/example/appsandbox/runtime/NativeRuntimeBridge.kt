package com.example.appsandbox.runtime

import android.app.Application
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.storage.InstanceStorageManager
import java.io.File

/** Process-local native boundary installed before any Guest class can load a DSO. */
object NativeRuntimeBridge {
    init {
        System.loadLibrary("appsandbox_runtime")
        Log.i(TAG, "NATIVE_BRIDGE_PRELOADED process=${Application.getProcessName()}")
    }

    @Synchronized
    fun preload() {
        // Touching the object loads libappsandbox_runtime before Guest code exists.
    }

    @Synchronized
    fun bind(identity: RuntimeIdentity, storage: InstanceStorageManager) {
        val process = physicalProcessName()
        val expected = "${identity.hostPackageName}:p${identity.processSlot}"
        check(process == expected) { "native binding process mismatch expected=$expected actual=$process" }
        check(nativeBindAndInstall(identity.guestPackageName, identity.instanceId, identity.processSlot,
            storage.root.path, storage.deviceProtected.path)) {
            "native process binding or Bionic hook installation failed for ${identity.instanceId}/p${identity.processSlot}"
        }
        check(nativeIsReady()) { "native bridge did not reach EARLY_NATIVE_IO_READY" }
        Log.i(TAG, "PROCESS_BINDING_GATE PASS package=${identity.guestPackageName} instance=${identity.instanceId} " +
            "slot=${identity.processSlot} process=$process rewrites=${nativeGuestRewriteCount()}")
    }

    external fun nativeBindAndInstall(
        packageName: String,
        instanceId: String,
        processSlot: Int,
        credentialRoot: String,
        deviceRoot: String
    ): Boolean
    external fun nativeIsReady(): Boolean
    external fun nativeGuestRewriteCount(): Long

    private fun physicalProcessName(): String = runCatching {
        File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
    }.getOrDefault(Application.getProcessName())

    private const val TAG = "AppSandbox.M9.Native"
}
