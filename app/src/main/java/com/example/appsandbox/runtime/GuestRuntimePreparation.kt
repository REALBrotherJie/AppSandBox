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
import com.example.appsandbox.storage.InstancePathPolicy

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

    fun prepare(providers: Array<ProviderInfo>, logicalProcessName: String = identity.guestPackageName): Result {
        val guestContext = GuestContext(host, loader, resources, applicationInfo, dataRoot)
        val localProviders = providers.filter {
            !it.authority.isNullOrBlank() &&
                VirtualProcessKey.canonicalProcessName(identity.guestPackageName, it.processName) == logicalProcessName
        }.map(::overlayProvider)
        localProviders.forEach { overlay ->
            overlay.authority.split(';').forEach { authority ->
                val key = VirtualProviderManager.Key(identity.instanceId, authority)
                VirtualProviderManager.GLOBAL.registerSelfProvider(
                    key, identity.guestPackageName, identity.virtualUid
                ) {
                    installProvider(guestContext, overlay, key)
                }
            }
        }
        val appClass = applicationInfo.className?.takeIf { it.isNotBlank() } ?: Application::class.java.name
        val application = instrumentation.newApplication(loader, appClass, guestContext)
        guestContext.bindApplication(application)
        bindApplication(application)
        val installed = localProviders.map { overlay ->
            val authority = overlay.authority.substringBefore(';')
            requireNotNull(VirtualProviderManager.GLOBAL.findSelfProvider(
                identity.instanceId, identity.guestPackageName, identity.virtualUid, authority
            ))
            overlay.name
        }
        instrumentation.callApplicationOnCreate(application)
        return Result(application, guestContext, installed)
    }

    private fun overlayProvider(info: ProviderInfo) = ProviderInfo(info).apply {
        packageName = identity.guestPackageName
        applicationInfo = ApplicationInfo(applicationInfo)
        this.applicationInfo.packageName = identity.guestPackageName
        this.applicationInfo.dataDir = dataRoot.path
        this.applicationInfo.sourceDir = applicationInfo.sourceDir
        name = info.name
    }

    private fun installProvider(
        guestContext: GuestContext,
        overlay: ProviderInfo,
        requestedKey: VirtualProviderManager.Key
    ): VirtualProviderManager.Record {
        val holder = ProviderPlatformBridge.installLocalProvider(activityThread, guestContext, overlay)
        android.util.Log.i("AppSandbox.M8", "VPROVIDER event=GUEST_CONTEXT instance=${identity.instanceId} provider=${overlay.name} " +
            "applicationContext=${System.identityHashCode(guestContext.applicationContext)} package=${guestContext.packageName} " +
            "data=${guestContext.dataDir} databaseRoot=${File(dataRoot, "databases")} holder=${System.identityHashCode(holder)}")
        val records = overlay.authority.split(';').associateWith { authority ->
            VirtualProviderManager.Record(
                VirtualProviderManager.Key(identity.instanceId, authority), identity.guestPackageName,
                identity.virtualUid, ProviderInfo(overlay), holder
            )
        }
        records.values.filterNot { it.key == requestedKey }.forEach(VirtualProviderManager.GLOBAL::install)
        return requireNotNull(records[requestedKey.authority])
    }

    private class GuestContext(
        base: Context,
        private val guestLoader: ClassLoader,
        private val guestResources: Resources,
        private val guestInfo: ApplicationInfo,
        private val root: File
    ) : ContextWrapper(base) {
        @Volatile private var guestApplication: Application? = null
        private val pathPolicy = InstancePathPolicy(guestInfo.packageName, root)

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
        override fun getCodeCacheDir() = instanceDirectory("code_cache")
        override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
        override fun getDatabasePath(name: String) = pathPolicy.databasePath(name)
        override fun getDir(name: String, mode: Int): File {
            require(name.isNotEmpty() && !name.contains(File.separatorChar)) { "Directory name is invalid" }
            return instanceDirectory("app_$name")
        }
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

        private fun instanceDirectory(name: String): File {
            val directory = File(root, name).canonicalFile
            check(directory.parentFile == root.canonicalFile) { "Guest directory escapes instance root" }
            check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
                "Guest directory unavailable: $directory"
            }
            return directory
        }

        companion object {
            private val preferences = ConcurrentHashMap<String, SharedPreferences>()
        }
    }
}
