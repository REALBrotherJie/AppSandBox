package com.example.appsandbox.experiments

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.view.LayoutInflater
import java.io.File

class GuestWorkspaceContext(base: Context, private val guestLoader: ClassLoader, private val guestResources: Resources, archive: ApplicationInfo, private val root: File) : ContextWrapper(base.applicationContext) {
    private val info = ApplicationInfo(archive).apply { dataDir = root.path; sourceDir = archive.sourceDir; publicSourceDir = archive.publicSourceDir }
    private val inflater by lazy { LayoutInflater.from(baseContext).cloneInContext(this) }
    private fun dir(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
    override fun getPackageName() = info.packageName
    override fun getClassLoader() = guestLoader
    override fun getResources() = guestResources
    override fun getAssets() = guestResources.assets
    override fun getApplicationInfo() = info
    override fun getDataDir() = root
    override fun getFilesDir() = dir("files")
    override fun getCacheDir() = dir("cache")
    override fun getCodeCacheDir() = dir("code_cache")
    override fun getNoBackupFilesDir() = dir("no_backup")
    override fun getSystemService(name: String): Any? = if (name == LAYOUT_INFLATER_SERVICE) inflater else super.getSystemService(name)
}
