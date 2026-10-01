package com.example.appsandbox.runtime

import android.app.Instrumentation
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import java.io.File
import android.util.Log
import android.os.Build
import com.example.appsandbox.identity.SystemIdentityBridge
import com.example.appsandbox.activity.VirtualActivityLifecycle
import com.example.appsandbox.activity.VirtualActivityManager

class GuestRuntimeClassLoader(private val host: Context) {
    @Volatile private var loader: ClassLoader? = null
    @Volatile private var packageName: String? = null

    fun prepare(sourceDir: String, splitSourceDirs: List<String>, nativeLibraryDir: String?, packageName: String, dataRoot: String): ClassLoader {
        val current = loader
        if (current != null && this.packageName == packageName) return current
        val optimized = File(dataRoot, "dex").apply { mkdirs() }
        val dexPath = (listOf(sourceDir) + splitSourceDirs).joinToString(File.pathSeparator)
        return GuestDomainClassLoader(dexPath, optimized.path, nativeLibraryDir, host.classLoader).also {
            loader = it
            this.packageName = packageName
            Log.i("AppSandbox.M3", "CLASSLOAD_SETUP package=$packageName base=$sourceDir splits=$splitSourceDirs dexPath=$dexPath loader=$it")
        }
    }

    fun installInstrumentation(activityThread: Any, classLoader: ClassLoader, identityBridge: SystemIdentityBridge, activityManager: VirtualActivityManager) {
        val field = generateSequence(activityThread.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mInstrumentation") }.getOrNull() }.first()
            .apply { isAccessible = true }
        val previous = field.get(activityThread) as? Instrumentation
        if (previous !is GuestInstrumentation) field.set(activityThread, GuestInstrumentation(classLoader, previous, identityBridge, activityManager))
    }

    fun installLoadedApk(activityThread: Any, classLoader: ClassLoader, sourceDir: String, appInfo: android.content.pm.ApplicationInfo): Resources {
        val assets = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val addPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        require((addPath.invoke(assets, sourceDir) as Int) != 0) { "guest asset path rejected: $sourceDir" }
        val hostResources = host.resources
        val resources = Resources(assets, hostResources.displayMetrics, hostResources.configuration)
        val loadedApk = runCatching {
            val compatibility = Class.forName("android.content.res.CompatibilityInfo")
                .getField("DEFAULT_COMPATIBILITY_INFO").get(null)
            val method = activityThread.javaClass.declaredMethods.first {
                it.name == "getPackageInfoNoCheck" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == android.content.pm.ApplicationInfo::class.java &&
                    it.parameterTypes[1].isAssignableFrom(compatibility.javaClass)
            }
            method.apply { isAccessible = true }.invoke(activityThread, appInfo, compatibility)
        }.onFailure { Log.e("AppSandbox.M2", "guest LoadedApk creation failed", it) }.getOrNull()
            ?: return resources
        val type = loadedApk.javaClass
        var changed = 0
        runCatching { type.getDeclaredField("mClassLoader").apply { isAccessible = true }.set(loadedApk, classLoader) }
                .onFailure { Log.e("AppSandbox.M2", "LoadedApk mClassLoader replacement failed", it) }
        runCatching { type.getDeclaredField("mResources").apply { isAccessible = true }.set(loadedApk, resources) }
                .onSuccess { changed++ }.onFailure { Log.e("AppSandbox.M2", "LoadedApk mResources replacement failed", it) }
        runCatching { type.getDeclaredField("mApplicationInfo").apply { isAccessible = true }.set(loadedApk, appInfo) }
                .onFailure { Log.e("AppSandbox.M2", "LoadedApk mApplicationInfo replacement failed", it) }
        Log.i("AppSandbox.M2", "LoadedApk resources-installed changed=$changed guestPackage=${appInfo.packageName} resPackage=${runCatching { resources.getResourcePackageName(com.example.appsandbox.R.string.app_name) }.getOrDefault("unknown")}")
        return resources
    }

    private class GuestInstrumentation(
        private val guestLoader: ClassLoader,
        private val delegate: Instrumentation?,
        private val identityBridge: SystemIdentityBridge,
        private val activityManager: VirtualActivityManager
    ) : Instrumentation() {
        override fun newApplication(cl: ClassLoader?, className: String?, context: android.content.Context?): android.app.Application {
            return delegate?.newApplication(guestLoader, className, context)
                ?: super.newApplication(guestLoader, className, context)
        }

        override fun newActivity(cl: ClassLoader?, className: String?, intent: android.content.Intent?): android.app.Activity {
            return delegate?.newActivity(guestLoader, className, intent)
                ?: super.newActivity(guestLoader, className, intent)
        }

        override fun callActivityOnCreate(activity: android.app.Activity, state: android.os.Bundle?) {
            val base = activity.baseContext
            val attribution = if (Build.VERSION.SDK_INT >= 31) base.attributionSource else null
            Log.i("AppSandbox.M2.1", "CONTEXT_IDENTITY class=${activity.javaClass.name} package=${base.packageName} " +
                "opPackage=${base.opPackageName} attribution=${attribution?.packageName}/${attribution?.uid} " +
                "applicationInfo=${base.applicationInfo.packageName}/${base.applicationInfo.uid} processUid=${android.os.Process.myUid()} " +
                "logical=${identityBridge.logicalPackageName()}/${identityBridge.logicalUid()} physical=${identityBridge.physicalPackageName()}/${identityBridge.physicalUid()}")
            activityManager.created(activity)
            delegate?.callActivityOnCreate(activity, state) ?: super.callActivityOnCreate(activity, state)
        }

        override fun callActivityOnStart(activity: android.app.Activity) {
            delegate?.callActivityOnStart(activity) ?: super.callActivityOnStart(activity)
            activityManager.event(activity, VirtualActivityLifecycle.START)
        }

        override fun callActivityOnResume(activity: android.app.Activity) {
            delegate?.callActivityOnResume(activity) ?: super.callActivityOnResume(activity)
            activityManager.event(activity, VirtualActivityLifecycle.RESUME)
        }

        override fun callActivityOnPause(activity: android.app.Activity) {
            activityManager.event(activity, VirtualActivityLifecycle.PAUSE)
            delegate?.callActivityOnPause(activity) ?: super.callActivityOnPause(activity)
        }

        override fun callActivityOnStop(activity: android.app.Activity) {
            activityManager.event(activity, VirtualActivityLifecycle.STOP)
            delegate?.callActivityOnStop(activity) ?: super.callActivityOnStop(activity)
        }

        override fun callActivityOnDestroy(activity: android.app.Activity) {
            activityManager.event(activity, VirtualActivityLifecycle.DESTROY)
            delegate?.callActivityOnDestroy(activity) ?: super.callActivityOnDestroy(activity)
        }

        override fun callActivityOnNewIntent(activity: android.app.Activity, intent: android.content.Intent) {
            val restored = com.example.appsandbox.virtual.LaunchEnvelope.from(intent)?.originalIntent ?: intent
            restored.setExtrasClassLoader(guestLoader)
            activityManager.newIntent(activity, restored)
            delegate?.callActivityOnNewIntent(activity, restored) ?: super.callActivityOnNewIntent(activity, restored)
        }
    }
}
