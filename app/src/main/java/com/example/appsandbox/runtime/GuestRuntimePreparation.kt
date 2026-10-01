package com.example.appsandbox.runtime

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.content.res.Resources
import java.io.File
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.platform.ProviderPlatformBridge

class GuestRuntimePreparation(
    private val host: Context,
    private val identity: RuntimeIdentity,
    private val loader: ClassLoader,
    private val resources: Resources,
    private val applicationInfo: ApplicationInfo,
    private val dataRoot: File,
    private val activityThread: Any,
    private val instrumentation: Instrumentation
) {
    data class Result(val application: Application, val context: Context, val installedProviders: List<String>)

    fun prepare(providers: Array<ProviderInfo>): Result {
        val guestContext = GuestContext(host, loader, resources, applicationInfo, dataRoot)
        val appClass = applicationInfo.className?.takeIf { it.isNotBlank() } ?: Application::class.java.name
        val application = instrumentation.newApplication(loader, appClass, guestContext)
        val installed = providers.filter { it.processName.isNullOrBlank() || it.processName == applicationInfo.processName || it.processName == identity.guestPackageName }.map { info ->
            val overlay = ProviderInfo(info).apply {
                packageName = identity.guestPackageName
                applicationInfo = ApplicationInfo(applicationInfo)
                this.applicationInfo.packageName = identity.guestPackageName
                this.applicationInfo.dataDir = dataRoot.path
                this.applicationInfo.sourceDir = applicationInfo.sourceDir
                this.name = info.name
            }
            ProviderPlatformBridge.installLocalProvider(activityThread, guestContext, overlay)
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
        override fun getSharedPreferences(name: String, mode: Int) = createDeviceProtectedStorageContext().getSharedPreferences(File(root, "shared_prefs/$name.xml").path, mode)
    }
}
