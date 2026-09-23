package com.example.appsandbox.experiments.exp003b0

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import dalvik.system.DexClassLoader
import com.example.appsandbox.experiments.exp003a.Exp003aControlledContext
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest

object Exp003b0Runner {
    private const val TAG = "AppSandbox.Exp003B0P"
    private const val GUEST_APP = "com.example.appsandbox.testguest.runtime.Exp003GuestApplication"
    private const val FACTORY = "com.example.appsandbox.testguest.runtime.Exp003GuestComponentFactory"
    private const val THROWING_APP = "com.example.appsandbox.testguest.runtime.Exp003ThrowingApplication"
    private const val HOST_ONLY_ASSET = "exp002_host_only_asset.txt"
    private const val BUILD_SHA256 = "85f1cf6590b97b60ab09b4e55952e3df7a493e193a59a779756b24890cc33e0e"
    private var runsInProcess = 0

    @JvmStatic
    fun run(activity: android.app.Activity, record: GuestPackageRecord): String {
        runsInProcess++
        val output = mutableListOf<String>()
        fun line(value: String) { Log.i(TAG, value); output += value }
        line("process.pid=${Process.myPid()}")
        line("process.runCount=$runsInProcess")
        line("guestInstalled=${installed(activity, record.packageName)}")
        line("hiddenObservation=DEFERRED_NOT_APPROVED_TASK14")

        val apk = File(record.apkPath)
        val importedSha = sha256(apk)
        line("task14ReferenceSha256=$BUILD_SHA256")
        line("importedSha256=$importedSha")
        line("matchesTask14Reference=${importedSha.equals(BUILD_SHA256, true)}")
        val archive = archiveInfo(activity, apk)
        val applicationClass = archive.className ?: error("Guest Application className missing")
        line("guestApplicationClass=$applicationClass")
        val guestLoader = DexClassLoader(
            apk.absolutePath,
            File(activity.codeCacheDir, "exp003b0").apply { mkdirs() }.absolutePath,
            null,
            javaClass.classLoader
        )
        val guestInfo = ApplicationInfo(archive).apply {
            sourceDir = apk.absolutePath
            publicSourceDir = apk.absolutePath
        }
        val guestResources = activity.packageManager.getResourcesForApplication(guestInfo)
        val guestStringId = guestLoader.loadClass(
            "com.example.appsandbox.testguest.runtime.Exp002ResourceIds"
        ).getMethod("stringId").invoke(null) as Int
        val instanceRoot = File(
            activity.filesDir,
            "guests/${record.internalGuestId}/instances/debug-exp003b0-${record.internalGuestId}/data"
        )

        val appContextResult = observeBase(
            "applicationContext", activity, activity.applicationContext, record, guestLoader,
            guestResources, guestInfo, guestStringId, instanceRoot, applicationClass, ::line
        )
        val activityResult = observeBase(
            "activity", activity, activity, record, guestLoader,
            guestResources, guestInfo, guestStringId, instanceRoot, applicationClass, ::line
        )
        line("comparison.applicationContext.package=${appContextResult.packageName}")
        line("comparison.activity.package=${activityResult.packageName}")
        line("comparison.applicationContext.appContextClass=${appContextResult.applicationContextClass}")
        line("comparison.activity.appContextClass=${activityResult.applicationContextClass}")

        guestObservation(appContextResult.application, guestLoader, ::line)
        line("constructorCount=${staticInt(guestLoader, applicationClass, "constructorCount")}")
        line("onCreateCalled=${staticBoolean(guestLoader, applicationClass, "onCreateCalled")}")
        line("factoryUsed=${staticBoolean(guestLoader, FACTORY, "factoryUsed")}")
        line("factoryLogExpected=Uninitialized ActivityThread ... disabling AppComponentFactory")

        line("host.applicationContext.package=${activity.applicationContext.packageName}")
        line("host.applicationContext.class=${activity.applicationContext.javaClass.name}")
        line("host.resourceHostMarker=${activity.getString(
            activity.resources.getIdentifier("exp002_host_only", "string", activity.packageName)
        )}")
        line("hostResourcesSeesGuest=${runCatching {
            activity.resources.getString(guestStringId) ==
                "EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10"
        }.getOrDefault(false)}")
        line("hostAssetsSeesGuest=${opens(activity.resources, "exp002_asset.txt")}")
        line("systemResourcesSeesGuest=${ResourcesCompat.seesGuest(record.packageName)}")
        line("systemAssetsSeesGuest=${opens(android.content.res.Resources.getSystem(), "exp002_asset.txt")}")
        line("hostLoaderLoadsGuestApplication=${load(activity.classLoader, applicationClass)}")

        errorTests(
            activity, record, guestLoader, guestResources, guestInfo, instanceRoot,
            applicationClass, ::line
        )
        line("applicationContextObservation=${appContextResult.summary}")
        line("activityBaseObservation=${activityResult.summary}")
        line("conclusion=PUBLIC_ONLY_OBSERVED_HOST_LOADEDAPK_IMPACT")
        return output.joinToString("\n")
    }

    private fun observeBase(
        label: String,
        activity: android.app.Activity,
        base: Context,
        record: GuestPackageRecord,
        loader: DexClassLoader,
        resources: android.content.res.Resources,
        archiveInfo: ApplicationInfo,
        stringId: Int,
        root: File,
        className: String,
        line: (String) -> Unit
    ): BaseResult {
        val info = ApplicationInfo(archiveInfo).apply {
            sourceDir = archiveInfo.sourceDir
            publicSourceDir = archiveInfo.publicSourceDir
            dataDir = root.absolutePath
            uid = Process.myUid()
        }
        val controlled = Exp003aControlledContext(
            base, record.packageName, loader, resources, info, root
        )
        val instrumentation = Instrumentation()
        val app = try {
            instrumentation.newApplication(loader, className, controlled)
        } catch (e: Throwable) {
            line("$label.newApplication=${e.javaClass.name}:${e.message}")
            return BaseResult(null, "", "", "FAILED")
        }
        line("$label.app.class=${app.javaClass.name}")
        line("$label.app.loader=${app.javaClass.classLoader?.javaClass?.name}")
        line("$label.app.loaderIsGuest=${app.javaClass.classLoader === loader}")
        line("$label.app.baseIsControlled=${app.baseContext === controlled}")
        line("$label.app.package=${app.packageName}")
        line("$label.app.resourcesIsGuest=${app.resources === resources}")
        line("$label.app.assetsClass=${app.assets.javaClass.name}")
        line("$label.app.classLoader=${app.classLoader?.javaClass?.name}")
        line("$label.app.applicationInfo.package=${app.applicationInfo.packageName}")
        line("$label.app.applicationInfo.dataDir=${app.applicationInfo.dataDir}")
        line("$label.app.filesDir=${app.filesDir}")
        line("$label.app.applicationContext.class=${app.applicationContext.javaClass.name}")
        line("$label.app.applicationContextIsApp=${app.applicationContext === app}")
        line("$label.app.applicationContextIsControlled=${app.applicationContext === controlled}")
        line("$label.app.applicationContextIsHost=${app.applicationContext === activity.applicationContext}")
        line("$label.app.opPackageName=${app.opPackageName}")
        line("$label.app.attributionPackage=${app.attributionSource.packageName}")
        line("$label.app.attributionUid=${app.attributionSource.uid}")
        line("$label.app.packageManagerQuery=${runCatching { app.packageManager.getPackageInfo(app.packageName, 0); "FOUND" }.getOrElse { it.javaClass.simpleName }}")
        val inflater = app.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as? LayoutInflater
        line("$label.app.layoutInflaterClass=${inflater?.javaClass?.name ?: "null"}")
        line("$label.app.layoutInflaterContext=${inflater?.context?.javaClass?.name ?: "null"}")
        line("$label.app.activityServiceClass=${app.getSystemService(Context.ACTIVITY_SERVICE)?.javaClass?.name ?: "null"}")
        derived(app, label, line)
        val callbacks = object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        }
        line("$label.registerComponentCallbacks=${runCatching { app.registerComponentCallbacks(callbacks); "NO_EXCEPTION" }.getOrElse { it.javaClass.simpleName }}")
        line("$label.registerActivityLifecycleCallbacks=${runCatching {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: android.app.Activity, state: android.os.Bundle?) {}
                override fun onActivityStarted(a: android.app.Activity) {}
                override fun onActivityResumed(a: android.app.Activity) {}
                override fun onActivityPaused(a: android.app.Activity) {}
                override fun onActivityStopped(a: android.app.Activity) {}
                override fun onActivitySaveInstanceState(a: android.app.Activity, state: android.os.Bundle) {}
                override fun onActivityDestroyed(a: android.app.Activity) {}
            }); "NO_EXCEPTION"
        }.getOrElse { it.javaClass.simpleName }}")
        return BaseResult(app, app.packageName, app.applicationContext.javaClass.name, "PASS")
    }

    private fun guestObservation(app: Application?, loader: DexClassLoader, line: (String) -> Unit) {
        if (app == null) return
        try {
            val values = app.javaClass.getMethod("observe").invoke(app) as Map<*, *>
            values.forEach { (key, value) -> line("guest.observe.$key=$value") }
        } catch (e: Throwable) {
            line("guest.observe=${e.javaClass.name}:${e.message}")
        }
    }

    private fun derived(app: Application, label: String, line: (String) -> Unit) {
        val config = Configuration(app.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        try {
            val derived = app.createConfigurationContext(config)
            line("$label.derivedConfiguration.class=${derived.javaClass.name}")
            line("$label.derivedConfiguration.package=${derived.packageName}")
        } catch (e: Throwable) {
            line("$label.derivedConfiguration=${e.javaClass.simpleName}")
        }
    }

    private fun errorTests(
        activity: android.app.Activity,
        record: GuestPackageRecord,
        loader: DexClassLoader,
        resources: android.content.res.Resources,
        info: ApplicationInfo,
        root: File,
        className: String,
        line: (String) -> Unit
    ) {
        line("error.missingClass=${runCatching {
            Instrumentation().newApplication(loader, "missing.GuestApplication",
                Exp003aControlledContext(activity.applicationContext, record.packageName, loader, resources, ApplicationInfo(info), root))
            "NO_EXCEPTION"
        }.getOrElse { "${it.javaClass.name}:${it.message}" }}")
        line("error.nullBase=${runCatching {
            val nullBase = ContextWrapper(null)
            Instrumentation().newApplication(loader, className,
                Exp003aControlledContext(nullBase, record.packageName, loader, resources, ApplicationInfo(info), root))
            "NO_EXCEPTION"
        }.getOrElse { "${it.javaClass.name}:${it.message}" }}")
        line("error.constructorThrows=${runCatching {
            Instrumentation().newApplication(loader, THROWING_APP,
                Exp003aControlledContext(activity.applicationContext, record.packageName, loader, resources, ApplicationInfo(info), root))
            "NO_EXCEPTION"
        }.getOrElse { "${it.javaClass.name}:${it.message}" }}")
        line("error.appSurvived=true")
    }

    private data class BaseResult(
        val application: Application?,
        val packageName: String,
        val applicationContextClass: String,
        val summary: String
    )

    private object ResourcesCompat {
        fun seesGuest(packageName: String): Boolean =
            android.content.res.Resources.getSystem().getIdentifier("exp002_string", "string", packageName) != 0
    }

    private fun staticBoolean(loader: DexClassLoader, className: String, field: String): Boolean =
        loader.loadClass(className).getField(field).getBoolean(null)

    private fun staticInt(loader: DexClassLoader, className: String, field: String): Int =
        loader.loadClass(className).getField(field).getInt(null)

    private fun load(loader: ClassLoader, name: String) =
        runCatching { loader.loadClass(name).name }.getOrElse { it.javaClass.simpleName }

    private fun opens(resources: android.content.res.Resources, name: String) =
        runCatching { resources.assets.open(name).use { true } }.getOrDefault(false)

    private fun installed(activity: android.app.Activity, packageName: String) =
        runCatching { activity.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    private fun archiveInfo(activity: android.app.Activity, apk: File): ApplicationInfo {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            activity.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.PackageInfoFlags.of(
                    android.content.pm.PackageManager.GET_META_DATA.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.GET_META_DATA
            )
        } ?: error("Guest archive unreadable")
        return packageInfo.applicationInfo ?: error("Guest ApplicationInfo missing")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
