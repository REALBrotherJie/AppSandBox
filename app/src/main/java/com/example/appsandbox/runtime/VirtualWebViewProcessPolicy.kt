package com.example.appsandbox.runtime

import android.os.Build
import android.util.Log
import android.webkit.WebView
import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Process-global WebView ownership gate. It must run before Guest Application creation. */
object VirtualWebViewProcessPolicy {
    @Volatile private var configuredSuffix: String? = null

    @Synchronized
    fun configure(key: VirtualProcessKey): String {
        val suffix = stableSuffix(key)
        configuredSuffix?.let {
            check(it == suffix) { "WebView-initialized process cannot rebind from $it to $suffix" }
            return it
        }
        if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix(suffix)
        configuredSuffix = suffix
        Log.i(TAG, "WEBVIEW_PREINIT_READY suffix=$suffix instance=${key.instanceId} logicalProcess=${key.logicalProcessName}")
        return suffix
    }

    internal fun stableSuffix(key: VirtualProcessKey): String {
        val raw = "${key.packageRevision}|${key.packageName}|${key.instanceId}|${key.logicalProcessName}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    fun deleteDataDirectories(context: Context, suffixes: Collection<String>): Boolean {
        val dataDir = context.dataDir.canonicalFile
        return suffixes.distinct().all { suffix ->
            require(suffix.matches(Regex("[0-9a-f]{24}"))) { "invalid WebView suffix" }
            val target = File(dataDir, "app_webview_$suffix").canonicalFile
            require(target.parentFile == dataDir) { "WebView directory escaped host dataDir" }
            !target.exists() || target.deleteRecursively()
        }
    }

    private const val TAG = "AppSandbox.M10"
}
