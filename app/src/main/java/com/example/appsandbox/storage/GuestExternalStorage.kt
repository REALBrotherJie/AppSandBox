package com.example.appsandbox.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.appsandbox.hidden.HiddenApiAccess
import com.example.appsandbox.hiddenbridge.MappedUserEnvironment
import com.example.appsandbox.runtime.NativeRuntimeBridge
import java.io.File

/**
 * App-specific external storage of one instance. A Guest's `<volume>/Android/{data,obb,media}/<guest>`
 * lives under the Host's own `<volume>/Android/{data,obb,media}/<host>/virtual/<instance>`, which the
 * Host UID can create and access without storage permissions on every API level.
 */
class GuestExternalStoragePolicy(
    private val guestPackage: String,
    private val hostPackage: String,
    private val instanceId: String
) {
    enum class Kind(val segment: String) { DATA("data"), OBB("obb"), MEDIA("media") }

    fun logicalRoot(volume: String, kind: Kind): String = "${volume.trimEnd('/')}/Android/${kind.segment}/$guestPackage"

    fun physicalRoot(volume: String, kind: Kind): String = instanceRoot(volume, kind, hostPackage, instanceId)

    /** Maps a path under a Guest app-specific external root; any other path is returned unchanged. */
    fun map(path: String): String {
        for (kind in Kind.values()) {
            val marker = "/Android/${kind.segment}/$guestPackage"
            var index = path.indexOf(marker)
            while (index >= 0) {
                val end = index + marker.length
                if (end == path.length || path[end] == '/') {
                    return physicalRoot(path.substring(0, index), kind) + path.substring(end)
                }
                index = path.indexOf(marker, index + 1)
            }
        }
        return path
    }

    companion object {
        fun instanceRoot(volume: String, kind: Kind, hostPackage: String, instanceId: String): String =
            "${volume.trimEnd('/')}/Android/${kind.segment}/$hostPackage/virtual/$instanceId"
    }
}

object GuestExternalStorage {
    private const val TAG = "AppSandbox.M5"
    private const val PER_USER_RANGE = 100000
    @Volatile private var installed: GuestExternalStoragePolicy? = null

    /** Installs the mapping in this stub process before any Guest context asks for external dirs. */
    @Synchronized
    fun install(context: Context, guestPackage: String, instanceId: String) {
        if (installed != null) return
        val policy = GuestExternalStoragePolicy(guestPackage, context.packageName, instanceId)
        // The Host's own app-specific roots must exist first (vold creates them for the owning UID);
        // the per-instance children below them are then plain directories the Host owns.
        val volumes = hostVolumes(context)
        for (volume in volumes) {
            for (kind in GuestExternalStoragePolicy.Kind.values()) {
                val physical = File(policy.physicalRoot(volume, kind))
                // ContextImpl creates external cache dirs only through StorageManager.mkdirs (API 30+).
                val children = if (kind == GuestExternalStoragePolicy.Kind.DATA) listOf("files", "cache") else emptyList()
                (listOf(physical) + children.map { File(physical, it) }).forEach { dir ->
                    if (!dir.isDirectory && !dir.mkdirs()) Log.w(TAG, "VEXTERNAL mkdir failed instance=$instanceId path=$dir")
                }
            }
        }
        // The exemption must precede the first link of MappedUserEnvironment, or ART refuses its overrides.
        HiddenApiAccess.probe()
        EnvironmentSwap.install(guestPackage, policy)
        // Paths an App assembles itself (Environment.getExternalStorageDirectory() + "/Android/data/<pkg>")
        // still reach the same instance through the native IO boundary.
        for (volume in volumes) {
            val aliases = buildList {
                add(volume)
                if (volume == volumes.first()) addAll(listOf("/sdcard", "/storage/self/primary", "/mnt/sdcard"))
            }
            for (kind in GuestExternalStoragePolicy.Kind.values()) {
                aliases.forEach { NativeRuntimeBridge.addPathMapping(policy.logicalRoot(it, kind), policy.physicalRoot(volume, kind)) }
            }
        }
        installed = policy
        Log.i(TAG, "VEXTERNAL installed package=$guestPackage instance=$instanceId volumes=$volumes " +
            "files=${volumes.firstOrNull()?.let { policy.physicalRoot(it, GuestExternalStoragePolicy.Kind.DATA) }}")
    }

    /** Kept in its own class so verifying [GuestExternalStorage] never links the hidden subclass early. */
    private object EnvironmentSwap {
        fun install(guestPackage: String, policy: GuestExternalStoragePolicy) {
            val field = Environment::class.java.getDeclaredField("sCurrentUser").apply { isAccessible = true }
            val userId = android.os.Process.myUid() / PER_USER_RANGE
            field.set(null, MappedUserEnvironment(userId) { packageName, dirs ->
                if (packageName != guestPackage) dirs else Array(dirs.size) { File(policy.map(dirs[it].path)) }
            })
        }
    }

    fun delete(context: Context, instanceId: String): Boolean {
        var removed = false
        for (volume in hostVolumes(context)) {
            for (kind in GuestExternalStoragePolicy.Kind.values()) {
                val root = File(GuestExternalStoragePolicy.instanceRoot(volume, kind, context.packageName, instanceId))
                if (root.exists()) removed = root.deleteRecursively() || removed
            }
        }
        return removed
    }

    /** Storage volume roots as seen by the Host, derived from its own app-specific data dirs. */
    private fun hostVolumes(context: Context): List<String> {
        val marker = "/Android/data/${context.packageName}"
        context.obbDirs
        @Suppress("DEPRECATION") context.externalMediaDirs
        return context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
            dir.path.substringBefore(marker, "").takeIf { it.isNotEmpty() }
        }
    }
}
