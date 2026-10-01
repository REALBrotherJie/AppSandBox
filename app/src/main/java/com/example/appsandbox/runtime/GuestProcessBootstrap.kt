package com.example.appsandbox.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import android.util.Log
import com.example.appsandbox.virtual.LaunchEnvelope
import java.io.File
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.vpm.VirtualPackageManagerService
import com.example.appsandbox.activity.VirtualActivityManager
import com.example.appsandbox.binder.VirtualBinderManager
import com.example.appsandbox.stub.StubActivities
import com.example.appsandbox.storage.InstanceStorageManager

class GuestProcessBootstrap(private val context: Context) {
    private val guestLoader = GuestRuntimeClassLoader(context)
    private val activityManager = VirtualActivityManager()
    data class PreparedLaunch(val intent: Intent, val activityInfo: ActivityInfo)

    fun prepare(envelope: LaunchEnvelope): PreparedLaunch {
        require(envelope.processSlot in 0..8) { "invalid process slot ${envelope.processSlot}" }
        require(envelope.target.packageName == envelope.packageName) { "target package mismatch" }
        val storage = InstanceStorageManager(context, envelope.instanceId)
        val root = storage.root
        val allowed = File(context.filesDir, "virtual/instances").canonicalFile
        require(root.path.startsWith(allowed.path + File.separator) && root.path == File(envelope.dataRoot).canonicalPath) { "instance dataRoot mismatch" }
        val files = storage.files

        val info = ActivityInfo(envelope.activityInfo)
        val identity = RuntimeIdentity.create(context, envelope.packageName, envelope.instanceId, envelope.processSlot)
        val stub = StubActivities.intent(context, envelope.processSlot, envelope.activityInfo.launchMode)
        activityManager.requested(envelope, requireNotNull(stub.component), null, -1)
        val app = ApplicationInfo(requireNotNull(info.applicationInfo))
        // System services still see the host UID in M2. Keep the ContextImpl caller package
        // aligned with that UID while retaining the guest source/class metadata for loading.
        app.packageName = envelope.packageName
        app.uid = context.applicationInfo.uid
        app.dataDir = root.path
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            app.deviceProtectedDataDir = storage.deviceProtected.path
        }
        app.processName = context.packageName + ":p${envelope.processSlot}"
        info.applicationInfo = app
        info.processName = app.processName
        val loader = guestLoader.prepare(app.sourceDir, app.splitSourceDirs.orEmpty().toList(), app.nativeLibraryDir, envelope.packageName, root.path)
        val thread = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentActivityThread")
            .apply { isAccessible = true }.invoke(null) ?: error("ActivityThread unavailable")
        val packageFlags = android.content.pm.PackageManager.GET_ACTIVITIES or android.content.pm.PackageManager.GET_SERVICES or
            android.content.pm.PackageManager.GET_RECEIVERS or android.content.pm.PackageManager.GET_PROVIDERS or
            android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(envelope.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(packageFlags.toLong()))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(envelope.packageName, packageFlags)
        }
        val binderManager = VirtualBinderManager(context, identity, thread,
            VirtualPackageManagerService(packageInfo, identity, root.path), activityManager)
        val binderResults = binderManager.install()
        require(binderResults.all { it.installed }) {
            "Binder Core install failed: ${binderResults.filterNot { it.installed }.joinToString { "${it.service}:${it.failureReason}" }}"
        }
        val resources = guestLoader.installLoadedApk(thread, loader, app.sourceDir, app)
        guestLoader.installInstrumentation(thread, loader, binderManager.identityBridge(), activityManager)
        val guestInstrumentation = thread.javaClass.let { _ ->
            val field = generateSequence(thread.javaClass) { it.superclass }.mapNotNull { runCatching { it.getDeclaredField("mInstrumentation") }.getOrNull() }.first().apply { isAccessible = true }
            field.get(thread) as android.app.Instrumentation
        }
        val preparation = GuestRuntimePreparation(context, identity, loader, resources, app, root, thread, guestInstrumentation)
        val providers = packageInfo.providers?.map { android.content.pm.ProviderInfo(it) }?.toMutableList() ?: mutableListOf()
        if (providers.isEmpty()) {
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.queryContentProviders(null, 0, android.content.pm.PackageManager.GET_META_DATA)
                    .orEmpty().filter { it.packageName == envelope.packageName }.forEach { providers += android.content.pm.ProviderInfo(it) }
            }.onFailure { Log.w(TAG, "provider metadata fallback failed package=${envelope.packageName}", it) }
        }
        Log.i(TAG, "guest-provider-metadata package=${envelope.packageName} providers=${providers.map { it.name + ":" + it.authority }}")
        val runtime = preparation.prepare(providers.toTypedArray())
        val contentRefresh = binderManager.refreshContentService()
        require(contentRefresh.installed) { "ContentService refresh failed: ${contentRefresh.failureReason}" }
        Log.i(TAG, "bootstrap guest-runtime application=${runtime.application.javaClass.name} context=${runtime.context.javaClass.name} providers=${runtime.installedProviders}")
        info.applicationInfo.className = app.className
        val restored = Intent(envelope.originalIntent).apply {
            component = envelope.target
            setExtrasClassLoader(loader)
        }
        Log.i(TAG, "bootstrap api=${android.os.Build.VERSION.SDK_INT} pid=${Process.myPid()} process=${app.processName} " +
            "package=${envelope.packageName} instance=${envelope.instanceId} slot=${envelope.processSlot} run=${envelope.runId} " +
            "source=${app.sourceDir} splits=${app.splitSourceDirs?.contentToString()} native=${app.nativeLibraryDir} data=${app.dataDir} files=$files " +
            "application=${app.className} activity=${info.name} guestLoader=$loader resources=$resources")
        return PreparedLaunch(restored, info)
    }

    companion object { private const val TAG = "AppSandbox.M2" }
}
