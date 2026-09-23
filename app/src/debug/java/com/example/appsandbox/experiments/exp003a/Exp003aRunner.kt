package com.example.appsandbox.experiments.exp003a

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Looper
import android.os.Process
import android.util.Log
import dalvik.system.DexClassLoader
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.security.MessageDigest

object Exp003aRunner {
    private const val TAG = "AppSandbox.Exp003A"
    private const val PROBE = "com.example.appsandbox.testguest.runtime.GuestProbe"
    private const val STRING = "EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10"
    private const val ASSET = "EXP002_ASSET_b7e3d902-1a64-4c8f-95d0-2e6b4a1c8f33"
    private const val FILE = "exp003a_context_file.txt"
    private const val FILE_MARKER = "EXP003A_FILE_6fd2c7a1-5b84-4e90-a316-8d2f7c9b4015"
    private var runsInProcess = 0

    @JvmStatic
    fun run(activity: Activity, record: GuestPackageRecord): String {
        runsInProcess++
        val result = mutableListOf<String>()
        fun line(value: String) { Log.i(TAG, value); result += value }
        line("process.pid=${Process.myPid()}")
        line("process.runCount=$runsInProcess")
        line("guestId=${record.internalGuestId}")
        line("instanceId=debug-exp003a-${record.internalGuestId}")
        line("guestPackage=${record.packageName}")
        line("guestApkPath=${record.apkPath}")
        line("guestInstalled=${installed(activity, record.packageName)}")

        val guestLoader = createLoader(activity, File(record.apkPath))
        val guestInfo = ApplicationInfo(archiveInfo(activity, File(record.apkPath)))
        guestInfo.sourceDir = record.apkPath
        guestInfo.publicSourceDir = record.apkPath
        val guestResources = activity.packageManager.getResourcesForApplication(guestInfo)
        val root = File(activity.filesDir, "guests/${record.internalGuestId}/instances/debug-exp003a-${record.internalGuestId}/data")
        val controlledInfo = ApplicationInfo(guestInfo).apply {
            dataDir = root.absolutePath
            uid = Process.myUid()
        }
        val context = Exp003aControlledContext(
            activity, record.packageName, guestLoader, guestResources, controlledInfo, root
        )
        line("controlledContext.class=${context.javaClass.name}")
        line("baseContext.class=${context.baseContext.javaClass.name}")
        line("baseContext.package=${context.baseContext.packageName}")
        line("BASE_CONTEXT=HOST")

        val host = activity
        line("hostPackage=${host.packageName}")
        line("guestPackage=${context.packageName}")
        line("guestLogicalPackageDiffers=${host.packageName != context.packageName}")
        line("guestOpPackageName=${context.opPackageName}")
        line("hostOpPackageName=${host.opPackageName}")
        line("process.uid=${Process.myUid()}")
        line("process.pid=${Process.myPid()}")
        val guestAttribution = context.attributionSource
        val hostAttribution = host.attributionSource
        line("guestAttribution.package=${guestAttribution.packageName}")
        line("guestAttribution.uid=${guestAttribution.uid}")
        line("hostAttribution.package=${hostAttribution.packageName}")
        line("hostAttribution.uid=${hostAttribution.uid}")

        val hostLoader = host.classLoader
        line("hostLoader=${hostLoader.javaClass.name}")
        line("guestLoader=${guestLoader.javaClass.name}")
        line("contextLoader=${context.classLoader.javaClass.name}")
        line("contextLoaderIsGuest=${context.classLoader === guestLoader}")
        line("guestLoaderDiffersHost=${guestLoader !== hostLoader}")
        line("guestProbeViaContext=${load(context.classLoader, PROBE)}")
        line("guestProbeViaHost=${load(hostLoader, PROBE)}")

        line("guestResources=${context.resources.javaClass.name}")
        line("guestAssets=${context.assets.javaClass.name}")
        val string = context.getString(guestStringId(guestLoader))
        val asset = context.assets.open("exp002_asset.txt").bufferedReader().use { it.readText().trim() }
        line("string.expected=$STRING")
        line("string.actual=$string")
        line("string.exactMatch=${string == STRING}")
        line("asset.expected=$ASSET")
        line("asset.actual=$asset")
        line("asset.exactMatch=${asset == ASSET}")
        val hostOnlyId = host.resources.getIdentifier("exp002_host_only", "string", host.packageName)
        val guestHostResource = runCatching { context.getString(hostOnlyId); true }.getOrDefault(false)
        val guestHostAsset = runCatching { context.assets.open("exp002_host_only_asset.txt").use { true } }.getOrDefault(false)
        line("guestSeesHostResource=$guestHostResource")
        line("guestSeesHostAsset=$guestHostAsset")

        storage(context, host, ::line)
        applicationInfo(context, guestInfo, root, record.apkPath, ::line)
        line("hostPackageCodePath=${host.packageCodePath}")
        line("hostPackageResourcePath=${host.packageResourcePath}")
        line("guestPackageCodePath=${context.packageCodePath}")
        line("guestPackageResourcePath=${context.packageResourcePath}")
        line("packagePathsDiffer=${context.packageCodePath != host.packageCodePath && context.packageResourcePath != host.packageResourcePath}")
        line("applicationContext.class=${context.applicationContext.javaClass.name}")
        line("applicationContext===controlled=${context.applicationContext === context}")
        line("applicationContext===hostApplication=${context.applicationContext === host.applicationContext}")
        line("mainLooperSame=${context.mainLooper === Looper.getMainLooper()}")
        line("packageManager.class=${context.packageManager.javaClass.name}")
        line("packageManagerGuestSelf=${runCatching { context.packageManager.getPackageInfo(record.packageName, 0); "FOUND" }.getOrElse { it.javaClass.simpleName }}")
        line("contentResolver.sameHost=${context.contentResolver === host.contentResolver}")
        line("systemService.layoutInflater.sameHost=${context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) === host.getSystemService(Context.LAYOUT_INFLATER_SERVICE)}")
        derivedContexts(context, record.packageName, ::line)
        line("invalidApk=${invalidApk(activity, context, ::line)}")
        line("missingInstance=${missingInstance(activity, record, ::line)}")
        line("hostResourcesSeesGuest=${seesGuest(host.resources, guestStringId(guestLoader))}")
        line("hostAssetsSeesGuest=${opens(host.resources, "exp002_asset.txt")}")
        line("systemResourcesSeesGuest=${Resources.getSystem().getIdentifier("exp002_string", "string", record.packageName) != 0}")
        line("systemAssetsSeesGuest=${opens(Resources.getSystem(), "exp002_asset.txt")}")
        val required = result.filter { it.contains("exactMatch=false") || it.contains("=true") && it.startsWith("guestSees") }.isEmpty()
        line("conclusion=${if (required && context.packageName == record.packageName) "CONFIRMED" else "PARTIALLY_CONFIRMED"}")
        return result.joinToString("\n")
    }

    private fun storage(c: Exp003aControlledContext, host: Context, line: (String) -> Unit) {
        val rows = listOf(
            "dataDir" to (c.dataDir to host.dataDir),
            "filesDir" to (c.filesDir to host.filesDir),
            "cacheDir" to (c.cacheDir to host.cacheDir),
            "codeCacheDir" to (c.codeCacheDir to host.codeCacheDir),
            "noBackupFilesDir" to (c.noBackupFilesDir to host.noBackupFilesDir)
        )
        rows.forEach { (name, paths) -> line("storage.$name.guest=${paths.first}"); line("storage.$name.host=${paths.second}"); line("storage.$name.diff=${paths.first != paths.second}") }
        val guestPath = c.getFileStreamPath(FILE)
        val hostPath = host.getFileStreamPath(FILE)
        c.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(FILE_MARKER.toByteArray()) }
        val read = c.openFileInput(FILE).bufferedReader().use { it.readText() }
        line("file.getFileStreamPath=$guestPath")
        line("file.openFileOutput.path=$guestPath")
        line("file.openFileInput.result=$read")
        line("file.exactMatch=${read == FILE_MARKER}")
        line("file.hostPath=$hostPath")
        line("file.samePath=${guestPath == hostPath}")
        line("file.hostDefaultExists=${hostPath.exists()}")
        line("fileList.contains=${c.fileList().contains(FILE)}")
        val dir = c.getDir("exp003a_custom", Context.MODE_PRIVATE)
        line("getDir=$dir")
        line("getDir.inInstance=${dir.path.startsWith(c.dataDir.path)}")
        line("deleteFile=${c.deleteFile(FILE)}")
        line("fileListAfterDelete=${c.fileList().contains(FILE)}")
    }

    private fun applicationInfo(c: Exp003aControlledContext, archive: ApplicationInfo, root: File, apk: String, line: (String) -> Unit) {
        line("applicationInfo.packageName=${c.applicationInfo.packageName}")
        line("applicationInfo.sourceDir=${c.applicationInfo.sourceDir}")
        line("applicationInfo.publicSourceDir=${c.applicationInfo.publicSourceDir}")
        line("applicationInfo.dataDir=${c.applicationInfo.dataDir}")
        line("applicationInfo.uid=${c.applicationInfo.uid}")
        line("archiveApplicationInfo.uid=${archive.uid}")
        line("process.uid=${Process.myUid()}")
        line("applicationInfo.apkDerived=packageName,className,targetSdkVersion,theme")
        line("applicationInfo.guestLogical=sourceDir,publicSourceDir,dataDir")
        line("applicationInfo.androidReality=uid,Process.myUid")
        line("applicationInfo.sourceMatches=${c.applicationInfo.sourceDir == apk}")
        line("applicationInfo.dataMatches=${c.applicationInfo.dataDir == root.absolutePath}")
    }

    private fun derivedContexts(c: Exp003aControlledContext, packageName: String, line: (String) -> Unit) {
        val landscape = Configuration(c.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        observe(line, "createConfigurationContext") { c.createConfigurationContext(landscape) }
        observe(line, "createDeviceProtectedStorageContext") { c.createDeviceProtectedStorageContext() }
        if (Build.VERSION.SDK_INT >= 30) observe(line, "createAttributionContext") { c.createAttributionContext("exp003a") }
        observe(line, "createPackageContext") { c.createPackageContext(packageName, 0) }
    }

    private fun observe(line: (String) -> Unit, name: String, block: () -> Context) {
        try {
            val c = block()
            line("derived.$name.class=${c.javaClass.name}")
            line("derived.$name.package=${c.packageName}")
            line("derived.$name.guestSemantics=${c.packageName != "com.example.appsandbox"}")
            line("derived.$name.classification=${if (c.packageName == "com.example.appsandbox.testguest") "SUPPORTED" else "HOST ESCAPE"}")
        } catch (e: Throwable) {
            line("derived.$name.classification=THROWS:${e.javaClass.simpleName}")
        }
    }

    private fun invalidApk(activity: Activity, c: Exp003aControlledContext, line: (String) -> Unit): String =
        try {
            val invalidInfo = ApplicationInfo(c.applicationInfo).apply {
                sourceDir = File(activity.cacheDir, "exp003a-invalid.apk").absolutePath
                publicSourceDir = sourceDir
            }
            Exp003aControlledContext(activity, c.packageName, c.classLoader as DexClassLoader, c.resources,
                invalidInfo, File(activity.cacheDir, "exp003a-invalid-data"))
            "INVALID_CREATED"
        } catch (e: Throwable) { "${e.javaClass.simpleName}:${e.message}" }

    private fun missingInstance(activity: Activity, record: GuestPackageRecord, line: (String) -> Unit): String {
        val missing = File(activity.cacheDir, "exp003a-missing-${System.nanoTime()}")
        return try {
            val info = ApplicationInfo(archiveInfo(activity, File(record.apkPath))).apply {
                sourceDir = record.apkPath
                publicSourceDir = record.apkPath
            }
            val c = Exp003aControlledContext(activity, record.packageName,
                createLoader(activity, File(record.apkPath)),
                activity.packageManager.getResourcesForApplication(info),
                info, missing)
            "CREATED:${c.dataDir.exists()}"
        } catch (e: Throwable) { "${e.javaClass.simpleName}:${e.message}" }
    }

    private fun guestStringId(loader: DexClassLoader): Int {
        val c = loader.loadClass("com.example.appsandbox.testguest.runtime.Exp002ResourceIds")
        return c.getMethod("stringId").invoke(null) as Int
    }

    private fun load(loader: ClassLoader, name: String) = try { loader.loadClass(name).name } catch (e: Throwable) { e.javaClass.simpleName }
    private fun seesGuest(r: Resources, id: Int) = runCatching { r.getString(id) == STRING }.getOrDefault(false)
    private fun opens(r: Resources, name: String) = runCatching { r.assets.open(name).use { true } }.getOrDefault(false)
    private fun installed(a: Activity, p: String) = runCatching { a.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
    private fun createLoader(a: Activity, apk: File) = DexClassLoader(apk.absolutePath, File(a.codeCacheDir, "exp003a").apply { mkdirs() }.absolutePath, null, Exp003aRunner::class.java.classLoader)
    private fun archiveInfo(a: Activity, apk: File) =
        if (Build.VERSION.SDK_INT >= 33) a.packageManager.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0))!!.applicationInfo!!
        else @Suppress("DEPRECATION") a.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)!!.applicationInfo!!
}
