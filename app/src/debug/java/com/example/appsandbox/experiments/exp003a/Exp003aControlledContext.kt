package com.example.appsandbox.experiments.exp003a

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.annotation.RequiresApi
import android.os.Build
import dalvik.system.DexClassLoader
import java.io.File

/**
 * EXP-003A only. This is a semantic Context facade, not an installed-package
 * Context and not a replacement for framework LoadedApk state.
 */
class Exp003aControlledContext(
    base: Context,
    private val guestPackage: String,
    private val guestLoader: DexClassLoader,
    private val guestResources: Resources,
    private val guestInfo: ApplicationInfo,
    private val instanceRoot: File
) : ContextWrapper(base) {
    private val files = File(instanceRoot, "files").apply { mkdirs() }
    private val cache = File(instanceRoot, "cache").apply { mkdirs() }
    private val codeCache = File(instanceRoot, "code_cache").apply { mkdirs() }
    private val noBackup = File(instanceRoot, "no_backup").apply { mkdirs() }
    private val dirs = File(instanceRoot, "dirs").apply { mkdirs() }

    init {
        require(guestInfo.sourceDir.isNotBlank() && File(guestInfo.sourceDir).isFile) {
            "Guest APK path is invalid: ${guestInfo.sourceDir}"
        }
        guestInfo.dataDir = instanceRoot.absolutePath
    }

    override fun getPackageName() = guestPackage
    override fun getClassLoader() = guestLoader
    override fun getResources() = guestResources
    override fun getAssets(): AssetManager = guestResources.assets
    override fun getApplicationInfo() = guestInfo
    override fun getDataDir() = instanceRoot
    override fun getFilesDir() = files
    override fun getCacheDir() = cache
    override fun getCodeCacheDir() = codeCache
    override fun getNoBackupFilesDir() = noBackup
    override fun getPackageCodePath() = guestInfo.sourceDir
    override fun getPackageResourcePath() = guestInfo.publicSourceDir
    override fun getApplicationContext(): Context = this

    override fun getFileStreamPath(name: String) = File(files, name)

    override fun openFileInput(name: String) =
        getFileStreamPath(name).inputStream()

    override fun openFileOutput(name: String, mode: Int) =
        FileOutput.open(getFileStreamPath(name), mode)

    override fun deleteFile(name: String) = getFileStreamPath(name).delete()

    override fun fileList() = files.list()?.sorted()?.toTypedArray() ?: emptyArray()

    override fun getDir(name: String, mode: Int): File =
        File(dirs, name).apply { mkdirs() }

    override fun getOpPackageName(): String = super.getOpPackageName()

    @RequiresApi(31)
    override fun getAttributionSource(): android.content.AttributionSource =
        super.getAttributionSource()

    private object FileOutput {
        fun open(file: File, mode: Int): java.io.FileOutputStream {
            file.parentFile?.mkdirs()
            return java.io.FileOutputStream(file, mode and MODE_APPEND != 0)
        }
    }
}
