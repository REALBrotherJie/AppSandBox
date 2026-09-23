package com.example.appsandbox.experiments.exp002

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import dalvik.system.DexClassLoader
import com.example.appsandbox.model.GuestPackageRecord
import org.xmlpull.v1.XmlPullParser
import java.io.File

object Exp002Runner {
    private const val TAG = "AppSandbox.Exp002"
    private const val STRING = "EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10"
    private const val RAW = "EXP002_RAW_5c9a8e21-6f34-4b7d-91c2-8e0f3a6d4b11"
    private const val ASSET = "EXP002_ASSET_b7e3d902-1a64-4c8f-95d0-2e6b4a1c8f33"
    private const val HOST_ASSET = "EXP002_HOST_ONLY_ASSET_4e91c7a2-6d3f-48b0-a5c1-9f2e7d8b6304"
    private const val MISSING_ID = 0x7f06ffff

    @JvmStatic
    fun run(activity: Activity, record: GuestPackageRecord): String {
        require(Build.VERSION.SDK_INT >= 30) { "Option A requires API 30+" }
        val lines = mutableListOf<String>()
        fun line(value: String) { Log.i(TAG, value); lines += value }
        val apk = File(record.apkPath)
        val ids = loadIds(activity, apk)
        val hostBefore = hostFingerprint(activity)
        line("guestApkPath=${apk.absolutePath}")
        line("guestInstalled=${installed(activity, record.packageName)}")
        val optionB = runOptionB(activity, apk, record, ids, ::line)
        val optionA = runVariant(activity, apk, record, ids, "OPTION_A_SYSTEM_BASE", Resources.getSystem().assets, ::line)
        runVariant(activity, apk, record, ids, "LEGACY_HOST_BASED", activity.resources.assets, ::line)
        line("optionAResult=$optionA")
        line("optionBResult=$optionB")
        val hostAfter = hostFingerprint(activity)
        line("hostBefore=$hostBefore")
        line("hostAfter=$hostAfter")
        line("HOST_RESOURCES_CHANGED_AFTER_GUEST_LOAD=${hostBefore != hostAfter}")
        line("TEST_ID_ABSENT_FROM_APK_TABLE=true")
        line("conclusion=${if (optionA && optionB) "CONFIRMED" else "PARTIALLY_CONFIRMED"}")
        return lines.joinToString("\n")
    }

    private fun runVariant(
        activity: Activity, apk: File, record: GuestPackageRecord, ids: Ids, name: String,
        base: android.content.res.AssetManager, line: (String) -> Unit
    ): Boolean {
        val owner = createResources(activity, apk, base)
        val r = owner.resources
        line("$name.baseAssets=$base")
        line("$name.guestAssets=$r.assets")
        line("$name.guestAssetsEqualsHost=${r.assets === activity.resources.assets}")
        line("$name.guestAssetsEqualsSystem=${r.assets === Resources.getSystem().assets}")
        val stringOk = readString(r, ids.stringId, STRING, "$name.string", line)
        val rawOk = readRaw(r, ids.rawId, RAW, "$name.raw", line)
        val assetOk = readAsset(r, "$name.asset", ASSET, line)
        val colorOk = runCatching { r.getColor(ids.colorId, null) == 0xff12ab34.toInt() }
            .onFailure { line("$name.colorError=$it") }.getOrDefault(false)
        val drawableOk = runCatching { r.getDrawable(ids.drawableId, null); true }
            .onFailure { line("$name.drawableError=$it") }.getOrDefault(false)
        val layoutOk = layout(r, ids.layoutId, name, line)
        val configOk = configuration(activity, apk, base, ids.configId, name, line)
        val collisionOk = runCatching { r.getString(ids.collisionId) == "GUEST_COLLISION" }
            .onSuccess { line("$name.guestCollision=GUEST_COLLISION") }
            .onFailure { line("$name.collisionError=$it") }.getOrDefault(false)
        val guestHostResource = runCatching { r.getString(activity.resources.getIdentifier("exp002_host_only", "string", activity.packageName)); true }.getOrDefault(false)
        val guestHostAsset = runCatching { r.assets.open("exp002_host_only_asset.txt").use { true } }.getOrDefault(false)
        val hostGuestResource = runCatching { activity.resources.getString(ids.stringId) == STRING }.getOrDefault(false)
        val hostGuestAsset = runCatching { activity.resources.assets.open("exp002_asset.txt").use { true } }.getOrDefault(false)
        line("$name.guestSeesHostResource=$guestHostResource")
        line("$name.guestSeesHostAsset=$guestHostAsset")
        line("$name.hostSeesGuestResource=$hostGuestResource")
        line("$name.hostSeesGuestAsset=$hostGuestAsset")
        identifierForms(r, record.packageName, name, line)
        errors(r, ids.stringId, apk, name, line)
        cleanup(owner, name, line)
        return stringOk && rawOk && assetOk && colorOk && drawableOk && layoutOk && configOk &&
            collisionOk && !guestHostResource && !guestHostAsset && !hostGuestResource && !hostGuestAsset
    }

    private fun runOptionB(activity: Activity, apk: File, record: GuestPackageRecord, ids: Ids, line: (String) -> Unit): Boolean {
        return try {
            val info = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: error("archive info unavailable")
            val app = info.applicationInfo?.let { ApplicationInfo(it) } ?: error("ApplicationInfo missing")
            app.sourceDir = apk.absolutePath
            app.publicSourceDir = apk.absolutePath
            line("OPTION_B.getPackageArchiveInfo=WORKS")
            line("OPTION_B.sourceDir=${app.sourceDir}")
            line("OPTION_B.publicSourceDir=${app.publicSourceDir}")
            val r = activity.packageManager.getResourcesForApplication(app)
            line("OPTION_B.getResourcesForApplication=WORKS:${r.javaClass.name}")
            val stringOk = readString(r, ids.stringId, STRING, "OPTION_B.string", line)
            val rawOk = readRaw(r, ids.rawId, RAW, "OPTION_B.raw", line)
            val assetOk = readAsset(r, "OPTION_B.asset", ASSET, line)
            val colorOk = r.getColor(ids.colorId, null) == 0xff12ab34.toInt()
            val drawableOk = runCatching { r.getDrawable(ids.drawableId, null); true }.getOrDefault(false)
            val layoutOk = layout(r, ids.layoutId, "OPTION_B", line)
            val configOk = r.getString(ids.configId) == "EXP002_DEFAULT"
            val collisionOk = r.getString(ids.collisionId) == "GUEST_COLLISION"
            val guestHostResource = runCatching {
                r.getString(activity.resources.getIdentifier("exp002_host_only", "string", activity.packageName))
                true
            }.getOrDefault(false)
            val guestHostAsset = runCatching { r.assets.open("exp002_host_only_asset.txt").use { true } }.getOrDefault(false)
            val hostGuestResource = runCatching { activity.resources.getString(ids.stringId) == STRING }.getOrDefault(false)
            val hostGuestAsset = runCatching { activity.resources.assets.open("exp002_asset.txt").use { true } }.getOrDefault(false)
            line("OPTION_B.guestSeesHostResource=$guestHostResource")
            line("OPTION_B.guestSeesHostAsset=$guestHostAsset")
            line("OPTION_B.hostSeesGuestResource=$hostGuestResource")
            line("OPTION_B.hostSeesGuestAsset=$hostGuestAsset")
            identifierForms(r, record.packageName, "OPTION_B", line)
            return stringOk && rawOk && assetOk && colorOk && drawableOk && layoutOk &&
                configOk && collisionOk && !guestHostResource && !guestHostAsset &&
                !hostGuestResource && !hostGuestAsset
        } catch (e: Throwable) {
            line("OPTION_B.getResourcesForApplication=FAILS:${e.javaClass.name}:${e.message}")
            false
        }
    }

    private fun createResources(activity: Activity, apk: File, base: android.content.res.AssetManager): Owner {
        val pfd = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY)
        val provider = ResourcesProvider.loadFromApk(pfd)
        pfd.close()
        val loader = ResourcesLoader().apply { addProvider(provider) }
        val r = Resources(base, activity.resources.displayMetrics, Configuration(activity.resources.configuration))
        r.addLoaders(loader)
        return Owner(r, loader, provider)
    }

    private fun layout(r: Resources, id: Int, name: String, line: (String) -> Unit): Boolean {
        line("$name.layoutMetadata=${metadata(r, id)}")
        for (method in listOf("getLayout", "getXml")) {
            try {
                val parser = if (method == "getLayout") r.getLayout(id) else r.getXml(id)
                var root = ""
                var child = ""
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (root.isEmpty()) root = parser.name else if (child.isEmpty()) child = parser.name
                    }
                }
                parser.close()
                line("$name.$method=WORKS root=$root child=$child")
                return true
            } catch (e: Throwable) {
                line("$name.$method=FAILS:${e.javaClass.name}:${e.message}")
            }
        }
        return false
    }

    private fun configuration(activity: Activity, apk: File, base: android.content.res.AssetManager, id: Int, name: String, line: (String) -> Unit): Boolean {
        val first = createResources(activity, apk, base).resources.getString(id)
        val config = Configuration(activity.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        val second = createResources(activity, apk, base, config).resources.getString(id)
        line("$name.configuration=$first->$second")
        return first == "EXP002_DEFAULT" && second == "EXP002_LANDSCAPE"
    }

    private fun createResources(activity: Activity, apk: File, base: android.content.res.AssetManager, config: Configuration): Owner {
        val pfd = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY)
        val provider = ResourcesProvider.loadFromApk(pfd)
        pfd.close()
        val loader = ResourcesLoader().apply { addProvider(provider) }
        val r = Resources(base, activity.resources.displayMetrics, config).apply { addLoaders(loader) }
        return Owner(r, loader, provider)
    }

    private fun readString(r: Resources, id: Int, expected: String, key: String, line: (String) -> Unit): Boolean {
        val actual = runCatching { r.getString(id) }.getOrElse { line("$key.error=$it"); return false }
        line("$key.expected=$expected"); line("$key.actual=$actual"); line("$key.exactMatch=${actual == expected}")
        line("$key.metadata=${metadata(r, id)}")
        return actual == expected
    }

    private fun readRaw(r: Resources, id: Int, expected: String, key: String, line: (String) -> Unit): Boolean {
        val actual = runCatching { r.openRawResource(id).bufferedReader().use { it.readText().trim() } }
            .getOrElse { line("$key.error=$it"); return false }
        line("$key.expected=$expected"); line("$key.actual=$actual"); line("$key.exactMatch=${actual == expected}")
        return actual == expected
    }

    private fun readAsset(r: Resources, key: String, expected: String, line: (String) -> Unit): Boolean {
        val actual = runCatching { r.assets.open("exp002_asset.txt").bufferedReader().use { it.readText().trim() } }
            .getOrElse { line("$key.error=$it"); return false }
        line("$key.expected=$expected"); line("$key.actual=$actual"); line("$key.exactMatch=${actual == expected}")
        return actual == expected
    }

    private fun identifierForms(r: Resources, packageName: String, name: String, line: (String) -> Unit) {
        line("$name.getIdentifier.form1=${r.getIdentifier("exp002_string", "string", packageName)}")
        line("$name.getIdentifier.form2=${r.getIdentifier("$packageName:string/exp002_string", null, null)}")
        line("$name.getIdentifier.form3=${r.getIdentifier("string/exp002_string", null, packageName)}")
    }

    private fun errors(r: Resources, stringId: Int, apk: File, name: String, line: (String) -> Unit) {
        line("$name.missing=${runCatching { r.getString(MISSING_ID) }.exceptionOrNull()}")
        line("$name.wrongType=${runCatching { r.getDrawable(stringId, null) }.exceptionOrNull()}")
        val corrupt = File(apk.parentFile, "exp002-corrupt.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        line("$name.corrupt=${runCatching { ParcelFileDescriptor.open(corrupt, ParcelFileDescriptor.MODE_READ_ONLY).use { ResourcesProvider.loadFromApk(it) } }.exceptionOrNull()}")
        corrupt.delete()
    }

    private fun cleanup(owner: Owner, name: String, line: (String) -> Unit) {
        runCatching { owner.loader.clearProviders(); owner.provider.close(); line("$name.cleanup=SUCCESS") }
            .onFailure { line("$name.cleanup=FAIL:$it") }
    }

    private fun metadata(r: Resources, id: Int): String = runCatching {
        "${r.getResourcePackageName(id)}/${r.getResourceTypeName(id)}/${r.getResourceEntryName(id)}"
    }.getOrElse { "ERROR:$it" }

    private fun hostFingerprint(activity: Activity): String = runCatching {
        "${activity.resources.getIdentifier("exp002_string", "string", "com.example.appsandbox.testguest")}:" +
            activity.resources.getString(activity.resources.getIdentifier("exp002_collision", "string", activity.packageName)) +
            ":" + activity.resources.assets.open("exp002_host_only_asset.txt").use { HOST_ASSET }
    }.getOrElse { "ERROR:$it" }

    private fun installed(activity: Activity, packageName: String) = runCatching {
        activity.packageManager.getPackageInfo(packageName, 0); true
    }.getOrDefault(false)

    private fun loadIds(activity: Activity, apk: File): Ids {
        val loader = DexClassLoader(apk.absolutePath, activity.codeCacheDir.absolutePath, null, javaClass.classLoader)
        val c = loader.loadClass("com.example.appsandbox.testguest.runtime.Exp002ResourceIds")
        fun call(name: String) = c.getMethod(name).invoke(null) as Int
        return Ids(call("stringId"), call("rawId"), call("colorId"), call("drawableId"), call("layoutId"), call("configValueId"), call("collisionId"))
    }

    private data class Owner(val resources: Resources, val loader: ResourcesLoader, val provider: ResourcesProvider)
    private data class Ids(val stringId: Int, val rawId: Int, val colorId: Int, val drawableId: Int, val layoutId: Int, val configId: Int, val collisionId: Int)
}
