package com.example.appsandbox.runtime

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.content.res.Resources
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.platform.ProviderPlatformBridge
import com.example.appsandbox.provider.VirtualProviderManager

class GuestRuntimePreparation(
    private val host: Context,
    private val identity: RuntimeIdentity,
    private val loader: ClassLoader,
    private val resources: Resources,
    private val applicationInfo: ApplicationInfo,
    private val dataRoot: File,
    private val activityThread: Any,
    private val instrumentation: Instrumentation,
    private val bindApplication: (Application) -> Unit
) {
    data class Result(val application: Application, val context: Context, val installedProviders: List<String>)

    fun prepare(providers: Array<ProviderInfo>): Result {
        val guestContext = GuestContext(host, loader, resources, applicationInfo, dataRoot)
        val appClass = applicationInfo.className?.takeIf { it.isNotBlank() } ?: Application::class.java.name
        val application = instrumentation.newApplication(loader, appClass, guestContext)
        guestContext.bindApplication(application)
        bindApplication(application)
        val installed = providers.filter { it.processName.isNullOrBlank() || it.processName == applicationInfo.processName || it.processName == identity.guestPackageName }.map { info ->
            val overlay = ProviderInfo(info).apply {
                packageName = identity.guestPackageName
                applicationInfo = ApplicationInfo(applicationInfo)
                this.applicationInfo.packageName = identity.guestPackageName
                this.applicationInfo.dataDir = dataRoot.path
                this.applicationInfo.sourceDir = applicationInfo.sourceDir
                this.name = info.name
            }
            val holder = ProviderPlatformBridge.installLocalProvider(activityThread, application, overlay)
            android.util.Log.i("AppSandbox.M8", "VPROVIDER event=GUEST_CONTEXT instance=${identity.instanceId} provider=${overlay.name} " +
                "application=${System.identityHashCode(application)} applicationContext=${System.identityHashCode(application.applicationContext)} " +
                "package=${application.packageName} data=${application.dataDir} databaseRoot=${File(dataRoot, "databases")} holder=${System.identityHashCode(holder)}")
            overlay.authority.split(';').forEach { authority ->
                VirtualProviderManager.GLOBAL.install(VirtualProviderManager.Record(
                    VirtualProviderManager.Key(identity.instanceId, authority), identity.guestPackageName,
                    identity.virtualUid, ProviderInfo(overlay), holder
                ))
            }
            info.name
        }
        instrumentation.callApplicationOnCreate(application)
        return Result(application, guestContext, installed)
    }

    private class GuestContext(
        base: Context,
        private val guestLoader: ClassLoader,
        private val guestResources: Resources,
        private val guestInfo: ApplicationInfo,
        private val root: File
    ) : ContextWrapper(base) {
        @Volatile private var guestApplication: Application? = null

        fun bindApplication(application: Application) {
            check(guestApplication == null || guestApplication === application) { "Guest Application already bound" }
            guestApplication = application
        }

        override fun getApplicationContext(): Context = guestApplication ?: this
        override fun getPackageName() = guestInfo.packageName
        override fun getOpPackageName() = guestInfo.packageName
        override fun getClassLoader() = guestLoader
        override fun getResources() = guestResources
        override fun getAssets() = guestResources.assets
        override fun getApplicationInfo() = guestInfo
        override fun getDataDir() = root
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(File(root, "databases").apply { mkdirs() }, name)
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            require(!name.contains(File.separatorChar)) { "SharedPreferences name contains a path separator" }
            val file = File(File(root, "shared_prefs").apply { mkdirs() }, "$name.xml")
            return preferences.computeIfAbsent(file.canonicalPath) {
                val type = Class.forName("android.app.SharedPreferencesImpl")
                val constructor = type.getDeclaredConstructor(File::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
                constructor.newInstance(file, mode) as SharedPreferences
            }
        }

        override fun createDeviceProtectedStorageContext(): Context {
            val deviceRoot = guestInfo.deviceProtectedDataDir?.let(::File) ?: File(root, "device_protected")
            return GuestContext(baseContext.createDeviceProtectedStorageContext(), guestLoader, guestResources, guestInfo, deviceRoot).also {
                guestApplication?.let(it::bindApplication)
            }
        }

        companion object {
            private val preferences = ConcurrentHashMap<String, SharedPreferences>()
        }
    }
}
