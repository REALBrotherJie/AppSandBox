package com.example.appsandbox.experiments.exp002

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Process
import android.util.Log
import dalvik.system.DexClassLoader
import com.example.appsandbox.model.GuestPackageRecord
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.security.MessageDigest

/**
 * Debug-only EXP-002 verifier. Option A and LEGACY are deliberately not
 * executable because they mutate or reuse shared AssetManager state.
 */
object Exp002Runner {
    private const val TAG = "AppSandbox.Exp002"
    private const val STRING = "EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10"
    private const val RAW = "EXP002_RAW_5c9a8e21-6f34-4b7d-91c2-8e0f3a6d4b11"
    private const val ASSET = "EXP002_ASSET_b7e3d902-1a64-4c8f-95d0-2e6b4a1c8f33"
    private const val BUILD_SHA = "125c7c57d575f1d7d8bb84a9d83a6cd26679bae696d6ffad91e75226b2f109b5"
    private const val MISSING_ID = 0x7f06ffff
    private var runsInProcess = 0
    private var legacyExecuted = false
    private var optionAExecuted = false

    @JvmStatic
    fun run(activity: Activity, record: GuestPackageRecord): String {
        runsInProcess++
        val out = mutableListOf<String>()
        fun log(value: String) { Log.i(TAG, value); out += value }
        log("process.pid=${Process.myPid()}")
        log("process.runCount=$runsInProcess")
        log("legacyExecuted=$legacyExecuted")
        log("optionAExecuted=$optionAExecuted")
        log("legacyStatus=REJECTED_NOT_EXECUTED")
        log("optionAStatus=REJECTED_NOT_EXECUTED")

        val apk = File(record.apkPath)
        val ids = loadIds(activity, apk)
        val installed = installed(activity, record.packageName)
        val importedSha = sha256(apk)
        log("guestInstalled=$installed")
        log("historicalReferenceSha256=$BUILD_SHA")
        log("importedSha256=$importedSha")
        log("matchesHistoricalReference=${importedSha.equals(BUILD_SHA, true)}")

        val before = snapshot(activity, ids.stringId, record.packageName, ::log, "before")
        val optionB = runOptionB(activity, apk, record, ids, ::log)
        val after = snapshot(activity, ids.stringId, record.packageName, ::log, "after")
        val hostChanged = before.host != after.host
        val systemChanged = before.system != after.system
        log("HOST_CHANGED=$hostChanged")
        log("SYSTEM_CHANGED=$systemChanged")
        log("hostSystemLeakage=${if (!hostChanged && !systemChanged) "NONE_OBSERVED_API31" else "OBSERVED"}")
        log("optionBResult=$optionB")
        log("conclusion=${if (optionB && !installed && !hostChanged && !systemChanged) "CONFIRMED_API${android.os.Build.VERSION.SDK_INT}" else "PARTIALLY_CONFIRMED"}")
        return out.joinToString("\n")
    }

    private fun runOptionB(
        activity: Activity,
        apk: File,
        record: GuestPackageRecord,
        ids: Ids,
        log: (String) -> Unit
    ): Boolean {
        val archive = try {
            archiveInfo(activity, apk).also { log("OPTION_B.ARCHIVE_PARSE=PASS") }
        } catch (e: Throwable) {
            report(log, "ARCHIVE_PARSE", e); return false
        }
        val app = try {
            ApplicationInfo(archive.applicationInfo ?: error("ApplicationInfo missing")).also {
                it.sourceDir = apk.absolutePath
                it.publicSourceDir = apk.absolutePath
                log("OPTION_B.APPLICATION_INFO_PREPARE=PASS")
            }
        } catch (e: Throwable) {
            report(log, "APPLICATION_INFO_PREPARE", e); return false
        }
        val defaultResources = try {
            activity.packageManager.getResourcesForApplication(app).also {
                log("OPTION_B.GET_RESOURCES=PASS class=${it.javaClass.name}")
            }
        } catch (e: Throwable) {
            report(log, "GET_RESOURCES", e); return false
        }
        val defaultOk = verify(defaultResources, activity, record, ids, "OPTION_B.DEFAULT", log)

        val land = Configuration(activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        val landResources = try {
            if (Build.VERSION.SDK_INT < 31) error("API 31 required")
            activity.packageManager.getResourcesForApplication(app, land).also {
                log("OPTION_B.CONFIGURATION=PASS")
            }
        } catch (e: Throwable) {
            report(log, "CONFIGURATION", e); return false
        }
        val defaultValue = readString(defaultResources, ids.configId, "EXP002_DEFAULT", "OPTION_B.defaultConfig", log)
        val landValue = readString(landResources, ids.configId, "EXP002_LANDSCAPE", "OPTION_B.landConfig", log)
        log("OPTION_B.defaultResources===landResources=${defaultResources === landResources}")
        log("OPTION_B.defaultAssets===landAssets=${defaultResources.assets === landResources.assets}")
        val configurationOk = defaultValue == "EXP002_DEFAULT" && landValue == "EXP002_LANDSCAPE"

        val resourcesA = activity.packageManager.getResourcesForApplication(app)
        val resourcesB = activity.packageManager.getResourcesForApplication(app)
        log("OPTION_B.twoResources.resourcesA===resourcesB=${resourcesA === resourcesB}")
        log("OPTION_B.twoResources.assetsA===assetsB=${resourcesA.assets === resourcesB.assets}")
        log("OPTION_B.twoResources.A.marker=${readString(resourcesA, ids.stringId, STRING, "OPTION_B.twoResources.A", log)}")
        log("OPTION_B.twoResources.B.marker=${readString(resourcesB, ids.stringId, STRING, "OPTION_B.twoResources.B", log)}")
        log("OPTION_B.twoResources.A.layout=${layout(resourcesA, ids.layoutId, "OPTION_B.twoResources.A", log)}")
        log("OPTION_B.twoResources.B.layout=${layout(resourcesB, ids.layoutId, "OPTION_B.twoResources.B", log)}")
        return defaultOk && configurationOk && errorTests(activity, app, defaultResources, ids, log)
    }

    private fun verify(
        r: Resources,
        activity: Activity,
        record: GuestPackageRecord,
        ids: Ids,
        name: String,
        log: (String) -> Unit
    ): Boolean {
        val stringOk = readString(r, ids.stringId, STRING, "$name.string", log) == STRING
        val rawOk = readRaw(r, ids.rawId, RAW, "$name.raw", log)
        val assetOk = readAsset(r, "$name.asset", log)
        val colorOk = try {
            val actual = r.getColor(ids.colorId, null)
            log("$name.color.actual=$actual expected=${0xff12ab34.toInt()} exactMatch=${actual == 0xff12ab34.toInt()}")
            actual == 0xff12ab34.toInt()
        } catch (e: Throwable) { report(log, "$name.COLOR", e); false }
        val drawableOk = try {
            log("$name.drawable.class=${r.getDrawable(ids.drawableId, null).javaClass.name}")
            true
        } catch (e: Throwable) { report(log, "$name.DRAWABLE", e); false }
        val layoutOk = layout(r, ids.layoutId, name, log)
        val metadataOk = metadata(r, ids.stringId, "$name.metadata", log)
        val identifiersOk = identifierForms(r, record.packageName, name, log)
        val collisionOk = readString(r, ids.collisionId, "GUEST_COLLISION", "$name.collision", log) == "GUEST_COLLISION"
        val guestHostResource = seesHostResource(r, activity)
        val guestHostAsset = opens(r, "exp002_host_only_asset.txt")
        val hostGuestResource = seesGuestResource(activity, ids.stringId)
        val hostGuestAsset = opens(activity.resources, "exp002_asset.txt")
        log("$name.guestSeesHostResource=$guestHostResource")
        log("$name.guestSeesHostAsset=$guestHostAsset")
        log("$name.hostSeesGuestResource=$hostGuestResource")
        log("$name.hostSeesGuestAsset=$hostGuestAsset")
        return stringOk && rawOk && assetOk && colorOk && drawableOk && layoutOk &&
            metadataOk && identifiersOk && collisionOk && !guestHostResource && !guestHostAsset &&
            !hostGuestResource && !hostGuestAsset
    }

    private fun errorTests(
        activity: Activity,
        app: ApplicationInfo,
        r: Resources,
        ids: Ids,
        log: (String) -> Unit
    ): Boolean {
        val missing = try { r.getString(MISSING_ID); false } catch (e: Throwable) {
            report(log, "ERROR_TEST.MISSING", e); true
        }
        log("OPTION_B.missing=$missing")
        log("OPTION_B.missingIdExistenceVerified=EXTERNAL_AAPT2_EVIDENCE")
        val wrongType = try { r.getDrawable(ids.stringId, null); false } catch (e: Throwable) {
            report(log, "ERROR_TEST.WRONG_TYPE", e); true
        }
        log("OPTION_B.wrongType=$wrongType")
        val dir = File(activity.cacheDir, "experiments/exp002").apply { mkdirs() }
        val corrupt = File(dir, "corrupt.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val corruptOk = try {
            val corruptApp = ApplicationInfo(app).apply {
                sourceDir = corrupt.absolutePath
                publicSourceDir = corrupt.absolutePath
            }
            activity.packageManager.getResourcesForApplication(corruptApp)
            false
        } catch (e: Throwable) {
            report(log, "ERROR_TEST.CORRUPT_APK", e); true
        } finally {
            corrupt.delete(); dir.delete()
        }
        log("OPTION_B.corruptApk=$corruptOk")
        log("OPTION_B.corruptApkLocation=${dir.absolutePath}")
        log("OPTION_B.appSurvived=true")
        return missing && wrongType && corruptOk
    }

    private fun snapshot(
        activity: Activity,
        guestId: Int,
        packageName: String,
        log: (String) -> Unit,
        phase: String
    ): Snapshot {
        val hostResource = seesGuestResource(activity, guestId)
        val hostAsset = opens(activity.resources, "exp002_asset.txt")
        val systemResource = Resources.getSystem().getIdentifier("exp002_string", "string", packageName) != 0
        val systemAsset = opens(Resources.getSystem(), "exp002_asset.txt")
        log("$phase.hostResourcesSeesGuest=$hostResource")
        log("$phase.hostAssetsSeesGuest=$hostAsset")
        log("$phase.systemResourcesSeesGuest=$systemResource")
        log("$phase.systemAssetsSeesGuest=$systemAsset")
        val host = listOf(
            activity.resources.getIdentifier("exp002_host_only", "string", activity.packageName),
            runCatching {
                activity.resources.getString(
                    activity.resources.getIdentifier("exp002_collision", "string", activity.packageName)
                )
            }.getOrDefault("ERROR"),
            opens(activity.resources, "exp002_host_only_asset.txt")
        ).joinToString("|")
        return Snapshot(host, "$systemResource|$systemAsset")
    }

    private fun seesHostResource(r: Resources, activity: Activity) =
        activity.resources.getIdentifier("exp002_host_only", "string", activity.packageName).let {
            it != 0 && runCatching {
                r.getString(it) == "EXP002_HOST_ONLY_8b6f1e2d-4c9a-47d1-b8f0-5e3a7c9d2146"
            }.getOrDefault(false)
        }

    private fun seesGuestResource(activity: Activity, id: Int) =
        runCatching { activity.resources.getString(id) == STRING }.getOrDefault(false)

    private fun opens(r: Resources, name: String) =
        runCatching { r.assets.open(name).use { true } }.getOrDefault(false)

    private fun layout(r: Resources, id: Int, name: String, log: (String) -> Unit): Boolean {
        log("$name.layoutMetadata=${metadataValue(r, id)}")
        return try {
            val parser = r.getLayout(id)
            var root = ""
            var child = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (root.isEmpty()) root = parser.name else if (child.isEmpty()) child = parser.name
                }
            }
            parser.close()
            log("$name.layout=PASS root=$root child=$child")
            root == "LinearLayout" && child == "TextView"
        } catch (e: Throwable) {
            report(log, "$name.LAYOUT", e); false
        }
    }

    private fun identifierForms(r: Resources, packageName: String, name: String, log: (String) -> Unit): Boolean {
        val one = r.getIdentifier("exp002_string", "string", packageName)
        val two = r.getIdentifier("$packageName:string/exp002_string", null, null)
        val three = r.getIdentifier("string/exp002_string", null, packageName)
        log("$name.getIdentifier.form1=$one")
        log("$name.getIdentifier.form2=$two")
        log("$name.getIdentifier.form3=$three")
        return one != 0 && two != 0 && three != 0
    }

    private fun metadata(r: Resources, id: Int, key: String, log: (String) -> Unit): Boolean {
        log("$key=${metadataValue(r, id)}")
        return metadataValue(r, id) != "ERROR"
    }

    private fun metadataValue(r: Resources, id: Int) = runCatching {
        "${r.getResourcePackageName(id)}/${r.getResourceTypeName(id)}/${r.getResourceEntryName(id)}"
    }.getOrElse { "ERROR" }

    private fun readString(r: Resources, id: Int, expected: String, key: String, log: (String) -> Unit): String {
        val actual = runCatching { r.getString(id) }.getOrElse {
            report(log, key, it); return ""
        }
        log("$key.expected=$expected")
        log("$key.actual=$actual")
        log("$key.exactMatch=${actual == expected}")
        return actual
    }

    private fun readRaw(r: Resources, id: Int, expected: String, key: String, log: (String) -> Unit) =
        runCatching { r.openRawResource(id).bufferedReader().use { it.readText().trim() } }
            .onSuccess { log("$key.actual=$it expected=$expected exactMatch=${it == expected}") }
            .onFailure { report(log, key, it) }.getOrDefault("") == expected

    private fun readAsset(r: Resources, key: String, log: (String) -> Unit) =
        runCatching { r.assets.open("exp002_asset.txt").bufferedReader().use { it.readText().trim() } }
            .onSuccess { log("$key.actual=$it expected=$ASSET exactMatch=${it == ASSET}") }
            .onFailure { report(log, key, it) }.getOrDefault("") == ASSET

    private fun archiveInfo(activity: Activity, apk: File) =
        if (Build.VERSION.SDK_INT >= 33) activity.packageManager.getPackageArchiveInfo(
            apk.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0)
        ) ?: error("archive info unavailable")
        else @Suppress("DEPRECATION") activity.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("archive info unavailable")

    private fun installed(activity: Activity, packageName: String) =
        runCatching { activity.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    private fun loadIds(activity: Activity, apk: File): Ids {
        val loader = DexClassLoader(apk.absolutePath, activity.codeCacheDir.absolutePath, null, javaClass.classLoader)
        val type = loader.loadClass("com.example.appsandbox.testguest.runtime.Exp002ResourceIds")
        fun call(name: String) = type.getMethod(name).invoke(null) as Int
        return Ids(call("stringId"), call("rawId"), call("colorId"), call("drawableId"), call("layoutId"), call("configValueId"), call("collisionId"))
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

    private fun report(log: (String) -> Unit, stage: String, e: Throwable) {
        log("$stage.exceptionClass=${e.javaClass.name}")
        log("$stage.message=${e.message}")
        log("$stage.cause=${e.cause?.javaClass?.name}:${e.cause?.message}")
    }

    private data class Snapshot(val host: String, val system: String)
    private data class Ids(
        val stringId: Int,
        val rawId: Int,
        val colorId: Int,
        val drawableId: Int,
        val layoutId: Int,
        val configId: Int,
        val collisionId: Int
    )
}
