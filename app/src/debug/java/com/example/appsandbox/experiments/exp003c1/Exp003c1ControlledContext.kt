package com.example.appsandbox.experiments.exp003c1

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.LayoutInflater
import java.io.File

class Exp003c1ControlledContext private constructor(
    base: Context,
    private val loader: ClassLoader,
    private val guestResources: Resources,
    archive: ApplicationInfo,
    private val root: File,
    val instanceId: String,
    private val state: State,
    private val dp: Boolean
) : ContextWrapper(base) {
    private class State {
        var application: Application? = null
        val services = linkedSetOf<String>()
        val preferences = mutableMapOf<String, ExperimentPreferences>()
    }
    constructor(base: Context, loader: ClassLoader, resources: Resources, info: ApplicationInfo, root: File, instanceId: String) :
        this(base.applicationContext, loader, resources, info, root, instanceId, State(), false)
    private val info = ApplicationInfo(archive).apply { dataDir = root.path; uid = android.os.Process.myUid() }
    private var themeId = info.theme.takeIf { it != 0 } ?: android.R.style.Theme_DeviceDefault_Light
    private val guestTheme by lazy { resources.newTheme().apply { applyStyle(themeId, true) } }
    private val inflater by lazy { LayoutInflater.from(baseContext).cloneInContext(this) }
    fun bindApplication(app: Application) {
        require(app.baseContext === this)
        check(state.application == null || state.application === app)
        state.application = app
    }
    fun requestedServices(): Set<String> = state.services.toSet()
    fun selectedTheme() = themeId
    private fun directory(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
    private fun name(name: String): String { require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name); return name }
    override fun getPackageName() = info.packageName
    override fun getClassLoader() = loader
    override fun getResources() = guestResources
    override fun getAssets() = guestResources.assets
    override fun getApplicationInfo() = info
    override fun getApplicationContext(): Context = state.application ?: this
    override fun getPackageCodePath() = info.sourceDir
    override fun getPackageResourcePath() = info.publicSourceDir
    override fun getDataDir() = root.apply { check(isDirectory || mkdirs()) }
    override fun getFilesDir() = directory("files")
    override fun getCacheDir() = directory("cache")
    override fun getCodeCacheDir() = directory("code_cache")
    override fun getNoBackupFilesDir() = directory("no_backup")
    override fun getDir(name: String, mode: Int) = directory("app_${name(name)}")
    override fun getFileStreamPath(name: String) = File(filesDir, name(name))
    override fun openFileInput(name: String) = getFileStreamPath(name).inputStream()
    override fun openFileOutput(name: String, mode: Int): java.io.FileOutputStream {
        require(mode == MODE_PRIVATE || mode == MODE_APPEND)
        return java.io.FileOutputStream(getFileStreamPath(name), mode == MODE_APPEND)
    }
    override fun deleteFile(name: String) = getFileStreamPath(name).delete()
    override fun fileList() = filesDir.list() ?: emptyArray()
    override fun getSystemService(name: String): Any? {
        state.services += name
        return if (name == LAYOUT_INFLATER_SERVICE) inflater else super.getSystemService(name)
    }
    override fun getTheme(): Resources.Theme = guestTheme
    override fun setTheme(resid: Int) { themeId = resid; guestTheme.applyStyle(resid, true) }
    private fun derive(base: Context, resources: Resources = guestResources, root: File = this.root, dp: Boolean = this.dp) =
        Exp003c1ControlledContext(base, loader, resources, info, root, instanceId, state, dp).also { it.setTheme(themeId) }
    override fun createConfigurationContext(config: Configuration): Context =
        derive(baseContext, packageManager.getResourcesForApplication(info, config))
    override fun createDeviceProtectedStorageContext(): Context {
        val base = baseContext.createDeviceProtectedStorageContext()
        return derive(base, root = File(base.filesDir, "task15-instances/${name(instanceId)}"), dp = true)
    }
    override fun isDeviceProtectedStorage() = dp
    override fun createAttributionContext(tag: String?): Context = derive(baseContext.createAttributionContext(tag))
    fun preferenceFile(name: String) = File(directory("shared_prefs"), "${name(name)}.xml")
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require(mode == MODE_PRIVATE)
        val file = preferenceFile(name)
        return synchronized(state.preferences) { state.preferences.getOrPut(file.canonicalPath) { ExperimentPreferences(file) } }
    }
    override fun deleteSharedPreferences(name: String): Boolean = synchronized(state.preferences) {
        val file = preferenceFile(name)
        state.preferences.remove(file.canonicalPath)
        android.util.AtomicFile(file).delete()
        !file.exists()
    }
    override fun getDatabasePath(name: String) = File(directory("databases"), name(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        openOrCreateDatabase(name, mode, factory, null)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
        require(mode == MODE_PRIVATE || mode == MODE_ENABLE_WRITE_AHEAD_LOGGING)
        val db = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        if (mode and MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) db.enableWriteAheadLogging()
        return db
    }
    override fun deleteDatabase(name: String) = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    override fun databaseList() = directory("databases").list() ?: emptyArray()
    private fun external(base: File?): File? = base?.let { File(it, "sandbox/${name(instanceId)}").apply { check(isDirectory || mkdirs()) } }
    override fun getExternalFilesDir(type: String?): File? = external(baseContext.getExternalFilesDir(null))?.let {
        if (type == null) it else File(it, name(type)).apply { check(isDirectory || mkdirs()) }
    }
    override fun getExternalFilesDirs(type: String?): Array<File> = listOfNotNull(getExternalFilesDir(type)).toTypedArray()
    override fun getExternalCacheDir() = external(baseContext.externalCacheDir)
    override fun getExternalCacheDirs(): Array<File> = listOfNotNull(externalCacheDir).toTypedArray()
    override fun getObbDir() = external(baseContext.obbDir)
    override fun getObbDirs(): Array<File> = listOfNotNull(obbDir).toTypedArray()
}
