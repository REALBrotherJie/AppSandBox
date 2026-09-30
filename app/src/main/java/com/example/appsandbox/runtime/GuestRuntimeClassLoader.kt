package com.example.appsandbox.runtime

import android.app.Instrumentation
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.ref.WeakReference
import android.util.Log
import java.lang.reflect.Proxy

class GuestRuntimeClassLoader(private val host: Context) {
    @Volatile private var loader: ClassLoader? = null
    @Volatile private var packageName: String? = null

    fun prepare(sourceDir: String, packageName: String, dataRoot: String): ClassLoader {
        val current = loader
        if (current != null && this.packageName == packageName) return current
        val optimized = File(dataRoot, "dex").apply { mkdirs() }
        return DexClassLoader(sourceDir, optimized.path, null, host.classLoader).also {
            loader = it
            this.packageName = packageName
        }
    }

    fun installInstrumentation(activityThread: Any, classLoader: ClassLoader) {
        val field = generateSequence(activityThread.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mInstrumentation") }.getOrNull() }.first()
            .apply { isAccessible = true }
        val previous = field.get(activityThread) as? Instrumentation
        if (previous !is GuestInstrumentation) field.set(activityThread, GuestInstrumentation(classLoader, previous))
    }

    fun installLoadedApk(activityThread: Any, classLoader: ClassLoader, sourceDir: String, appInfo: android.content.pm.ApplicationInfo): Resources {
        val assets = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val addPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        require((addPath.invoke(assets, sourceDir) as Int) != 0) { "guest asset path rejected: $sourceDir" }
        val hostResources = host.resources
        val resources = Resources(assets, hostResources.displayMetrics, hostResources.configuration)
        val packages = generateSequence(activityThread.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mPackages") }.getOrNull() }.first().apply { isAccessible = true }
        runCatching {
            val compatibility = Class.forName("android.content.res.CompatibilityInfo")
                .getField("DEFAULT_COMPATIBILITY_INFO").get(null)
            activityThread.javaClass.getDeclaredMethod("getPackageInfoNoCheck", android.content.pm.ApplicationInfo::class.java, compatibility.javaClass)
                .apply { isAccessible = true }.invoke(activityThread, appInfo, compatibility)
        }.onFailure { Log.e("AppSandbox.M2", "guest LoadedApk creation failed", it) }
        val values = packages.get(activityThread) as? Map<*, *> ?: return resources
        var changed = 0
        values.values.forEach { reference ->
            val loaded = when (reference) {
                is WeakReference<*> -> reference.get()
                else -> reference
            } ?: return@forEach
            val type = loaded.javaClass
            runCatching { type.getDeclaredField("mClassLoader").apply { isAccessible = true }.set(loaded, classLoader) }
                .onFailure { Log.e("AppSandbox.M2", "LoadedApk mClassLoader replacement failed", it) }
            runCatching { type.getDeclaredField("mResources").apply { isAccessible = true }.set(loaded, resources) }
                .onSuccess { changed++ }.onFailure { Log.e("AppSandbox.M2", "LoadedApk mResources replacement failed", it) }
            runCatching { type.getDeclaredField("mApplicationInfo").apply { isAccessible = true }.set(loaded, appInfo) }
                .onFailure { Log.e("AppSandbox.M2", "LoadedApk mApplicationInfo replacement failed", it) }
        }
        Log.i("AppSandbox.M2", "LoadedApk resources-installed entries=${values.size} changed=$changed guestPackage=${appInfo.packageName} resPackage=${runCatching { resources.getResourcePackageName(com.example.appsandbox.R.string.app_name) }.getOrDefault("unknown")}")
        return resources
    }

    fun installSystemCallerBridge(guestPackage: String) {
        val amClass = Class.forName("android.app.ActivityManager")
        val singleton = amClass.getDeclaredField("IActivityManagerSingleton").apply { isAccessible = true }.get(null)
        val singletonClass = Class.forName("android.util.Singleton")
        val instanceField = singletonClass.getDeclaredField("mInstance").apply { isAccessible = true }
        val original = instanceField.get(singleton) ?: singletonClass.getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
        if (original == null || Proxy.isProxyClass(original.javaClass)) return
        val iface = Class.forName("android.app.IActivityManager")
        val hostPackage = host.packageName
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
            val rewritten = args?.map { value -> if (value is String && value == guestPackage) hostPackage else value }?.toTypedArray()
            method.invoke(original, *(rewritten ?: emptyArray()))
        }
        instanceField.set(singleton, proxy)
        Log.i("AppSandbox.M2", "binder-bridge installed interface=${iface.name} guest=$guestPackage host=$hostPackage")
    }

    private class GuestInstrumentation(
        private val guestLoader: ClassLoader,
        private val delegate: Instrumentation?
    ) : Instrumentation() {
        override fun newApplication(cl: ClassLoader?, className: String?, context: android.content.Context?): android.app.Application {
            return super.newApplication(guestLoader, className, context)
        }

        override fun newActivity(cl: ClassLoader?, className: String?, intent: android.content.Intent?): android.app.Activity {
            return super.newActivity(guestLoader, className, intent)
        }
    }
}
