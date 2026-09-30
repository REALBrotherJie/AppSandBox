package com.example.appsandbox.runtime

import android.app.Instrumentation
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import java.io.File
import android.util.Log
import android.os.Build
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationTargetException
import com.example.appsandbox.identity.SystemIdentityBridge

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

    fun installInstrumentation(activityThread: Any, classLoader: ClassLoader, identityBridge: SystemIdentityBridge) {
        val field = generateSequence(activityThread.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mInstrumentation") }.getOrNull() }.first()
            .apply { isAccessible = true }
        val previous = field.get(activityThread) as? Instrumentation
        if (previous !is GuestInstrumentation) field.set(activityThread, GuestInstrumentation(classLoader, previous, identityBridge))
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

    fun installSystemCallerBridge(identityBridge: SystemIdentityBridge) {
        val amClass = Class.forName("android.app.ActivityManager")
        val singleton = amClass.getDeclaredField("IActivityManagerSingleton").apply { isAccessible = true }.get(null)
        val singletonClass = Class.forName("android.util.Singleton")
        val instanceField = singletonClass.getDeclaredField("mInstance").apply { isAccessible = true }
        val original = instanceField.get(singleton) ?: singletonClass.getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
        if (original == null || Proxy.isProxyClass(original.javaClass)) return
        val iface = Class.forName("android.app.IActivityManager")
        val hostPackage = identityBridge.physicalPackageName()
        val guestPackage = identityBridge.logicalPackageName()
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
            val rewritten = args?.map { value -> if (value is String && value == guestPackage) hostPackage else value }?.toTypedArray()
            try {
                val result = method.invoke(original, *(rewritten ?: emptyArray()))
                if (method.name == "getContentProvider" && result != null) wrapProviderHolder(result, identityBridge)
                result
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        }
        instanceField.set(singleton, proxy)
        Log.i("AppSandbox.M2", "binder-bridge installed interface=${iface.name} guest=$guestPackage host=$hostPackage")
    }

    private fun wrapProviderHolder(holder: Any, identityBridge: SystemIdentityBridge) {
        val providerField = generateSequence(holder.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("provider") }.getOrNull() }.firstOrNull() ?: return
        providerField.isAccessible = true
        val provider = providerField.get(holder) ?: return
        providerField.set(holder, identityBridge.wrapContentProvider(provider))
    }

    private class GuestInstrumentation(
        private val guestLoader: ClassLoader,
        private val delegate: Instrumentation?,
        private val identityBridge: SystemIdentityBridge
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
            delegate?.callActivityOnCreate(activity, state) ?: super.callActivityOnCreate(activity, state)
        }
    }
}
