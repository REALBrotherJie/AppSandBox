package com.example.appsandbox.experiments.exp003c1

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.*
import android.util.TypedValue
import android.view.LayoutInflater
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.experiments.exp003a.Exp003aControlledContext
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object C1Experiment {
    const val MARKER = "EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10"
    data class Setup(val context: Exp003c1ControlledContext, val app: Application, val loader: DexClassLoader)
    fun create(host: Context, record: GuestPackageRecord, id: String, loader: DexClassLoader? = null, before: (Exp003c1ControlledContext) -> Unit = {}): Setup {
        val apk = File(record.apkPath)
        val guestLoader = loader ?: DexClassLoader(apk.path, host.codeCacheDir.path, null, host.classLoader)
        @Suppress("DEPRECATION")
        val info = ApplicationInfo(host.packageManager.getPackageArchiveInfo(apk.path, 0)!!.applicationInfo!!).apply {
            sourceDir = apk.path; publicSourceDir = apk.path
        }
        val context = Exp003c1ControlledContext(host.applicationContext, guestLoader,
            host.packageManager.getResourcesForApplication(info), info,
            File(host.filesDir, "task15-instances/$id"), id)
        before(context)
        val app = Instrumentation().newApplication(guestLoader, requireNotNull(info.className), context)
        context.bindApplication(app)
        return Setup(context, app, guestLoader)
    }
    class Database(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE marker (value TEXT)") }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("No upgrade in experiment") }
        fun write(value: String) { writableDatabase.execSQL("DELETE FROM marker"); writableDatabase.execSQL("INSERT INTO marker VALUES (?)", arrayOf(value)) }
        fun read(): String? = readableDatabase.rawQuery("SELECT value FROM marker", null).use { if (it.moveToFirst()) it.getString(0) else null }
    }
    fun run(host: Activity, record: GuestPackageRecord, readOnly: Boolean = false): String {
        val lines = mutableListOf<String>()
        var passed = true
        fun line(s: String) { lines += s }
        fun test(name: String, block: () -> Boolean) {
            try { val ok = block(); if (!ok) passed = false; line("$name=${if (ok) "PASS" else "FAIL"}") }
            catch (e: Throwable) { passed = false; line("$name=FAIL ${e.javaClass.name}:${e.message}") }
        }
        val hostTheme = TypedValue().also { host.theme.resolveAttribute(android.R.attr.windowBackground, it, true) }.toString()
        val setup = create(host, record, "c1") { test("unbound.applicationContext") { it.applicationContext === it } }
        val c = setup.context
        if (!readOnly) {
            val c0 = Exp003aControlledContext(host.applicationContext, c.packageName, setup.loader, c.resources, ApplicationInfo(c.applicationInfo), c.dataDir)
            test("C0.preferences.write") { c0.getSharedPreferences("exp003c1", 0).edit().putString("marker", "C0_HOST_LEAK").commit() }
            line("C0.preferences.path=${File(host.dataDir, "shared_prefs/exp003c1.xml")}")
            test("C0.preferences.hostLeak") { host.getSharedPreferences("exp003c1", 0).getString("marker", null) == "C0_HOST_LEAK" }
        }
        val config = Configuration(c.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        val contexts = linkedMapOf("base" to c,
            "configuration" to c.createConfigurationContext(config) as Exp003c1ControlledContext,
            "deviceProtected" to c.createDeviceProtectedStorageContext() as Exp003c1ControlledContext,
            "attribution" to c.createAttributionContext("task15") as Exp003c1ControlledContext)
        contexts.forEach { (label, ctx) ->
            fun check(name: String, block: () -> Boolean) = test("$label.$name", block)
            check("packageName") { ctx.packageName == record.packageName }
            check("classLoader") { ctx.classLoader === setup.loader }
            check("resources") { ctx.getString(ctx.resources.getIdentifier("exp002_string", "string", ctx.packageName)) == MARKER }
            check("assets") { ctx.assets.open("exp002_asset.txt").bufferedReader().use { it.readText().contains("EXP002_ASSET_") } }
            check("applicationInfo") { ctx.applicationInfo.packageName == record.packageName && ctx.applicationInfo.dataDir == ctx.dataDir.path }
            check("applicationContext") { ctx.applicationContext === setup.app }
            check("inflater") { LayoutInflater.from(ctx).context === ctx && LayoutInflater.from(ctx) === LayoutInflater.from(ctx) }
            check("theme") { ctx.theme.resolveAttribute(android.R.attr.windowBackground, TypedValue(), true) }
            line("$label.themeId=${ctx.selectedTheme()}")
            val paths = mapOf("files" to ctx.filesDir, "cache" to ctx.cacheDir, "codeCache" to ctx.codeCacheDir,
                "noBackup" to ctx.noBackupFilesDir, "data" to ctx.dataDir, "getDir" to ctx.getDir("probe", 0), "database" to ctx.getDatabasePath("c1-$label.db"), "preferences" to ctx.preferenceFile("c1-$label"))
            paths.forEach { (key, file) -> line("$label.$key.path=$file"); check("$key.path") { file.canonicalPath == ctx.dataDir.canonicalPath || file.canonicalPath.startsWith(ctx.dataDir.canonicalPath + "/") } }
            check("protection") { ctx.isDeviceProtectedStorage == (label == "deviceProtected") }
            line("$label.attribution=${ctx.attributionSource.packageName}:${ctx.attributionSource.uid}:${ctx.attributionTag}")
            val prefs = ctx.getSharedPreferences("c1-$label", 0)
            check("preferences.identity") { prefs === ctx.getSharedPreferences("c1-$label", 0) }
            if (!readOnly) {
                var notified = false
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> notified = true }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                prefs.edit().clear().commit()
                check("preferences.commit") { prefs.edit().putString("s", "C1_MARKER").putInt("i", 3).putBoolean("b", true).putLong("l", 4L).putFloat("f", 1.25f).putStringSet("set", setOf("a", "b")).commit() }
                check("preferences.listener") { notified }
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                prefs.edit().putString("remove", "x").apply()
                prefs.edit().remove("remove").apply()
                check("preferences.remove") { !prefs.contains("remove") }
                ctx.getSharedPreferences("delete-$label", 0).edit().putString("x", "x").commit()
                check("preferences.delete") { ctx.deleteSharedPreferences("delete-$label") && !ctx.preferenceFile("delete-$label").exists() }
            }
            check("preferences.values") { prefs.getString("s", null) == "C1_MARKER" && prefs.getInt("i", 0) == 3 && prefs.getBoolean("b", false) && prefs.getLong("l", 0) == 4L && prefs.getFloat("f", 0f) == 1.25f && prefs.getStringSet("set", null) == setOf("a", "b") }
            Database(ctx, "c1-$label.db").use { db -> if (!readOnly) db.write("C1_DB"); check("database.value") { db.read() == "C1_DB" } }
            check("database.list") { ctx.databaseList().contains("c1-$label.db") }
            listOf("externalFiles" to ctx.getExternalFilesDir(null), "externalCache" to ctx.externalCacheDir, "obb" to ctx.obbDir).forEach { (key, file) ->
                line("$label.$key.path=$file"); check(key) { file != null && file.path.contains("/sandbox/c1") }
            }
            check("host.preferences.absent") { !File(host.dataDir, "shared_prefs/c1-$label.xml").exists() }
            check("host.database.absent") { !host.getDatabasePath("c1-$label.db").exists() }
        }
        test("host.inflater.unchanged") { LayoutInflater.from(host).context === host }
        test("host.theme.unchanged") { TypedValue().also { host.theme.resolveAttribute(android.R.attr.windowBackground, it, true) }.toString() == hostTheme }
        line("host.externalFiles=${host.getExternalFilesDir(null)} host.externalCache=${host.externalCacheDir} host.obb=${host.obbDir}")
        line("contentResolver=${c.contentResolver.javaClass.name} HOST_IDENTITY_REALITY")
        line("permission.INTERNET=${c.checkSelfPermission(android.Manifest.permission.INTERNET)} host=${host.checkSelfPermission(android.Manifest.permission.INTERNET)} HOST_IDENTITY_REALITY")
        line("packageQuery=${runCatching { c.packageManager.getPackageInfo(c.packageName, 0); "FOUND" }.getOrElse { "${it.javaClass.name}:${it.message}" }} UNSUPPORTED")
        line("createPackageContext=${runCatching { c.createPackageContext(c.packageName, 0); "CREATED" }.getOrElse { "${it.javaClass.name}:${it.message}" }} UNSUPPORTED")
        test("broadcast.hostIdentity") {
            val received = CountDownLatch(1)
            val thread = HandlerThread("task15-receiver").apply { start() }
            val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { received.countDown() } }
            val action = host.packageName + ".task15." + java.util.UUID.randomUUID()
            try {
                if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(receiver, IntentFilter(action), null, Handler(thread.looper), Context.RECEIVER_NOT_EXPORTED)
                else c.registerReceiver(receiver, IntentFilter(action), null, Handler(thread.looper))
                try { c.sendBroadcast(Intent(action).setPackage(host.packageName)); received.await(3, TimeUnit.SECONDS) }
                finally { c.unregisterReceiver(receiver) }
            } finally { thread.quitSafely() }
        }
        line("startActivity/startService/bindService=UNSUPPORTED_NOT_CALLED")
        line("createDisplayContext/createWindowContext=DEFERRED_NOT_CALLED")
        line("services=${c.requestedServices()}")
        line("gate.c1=$passed")
        File(host.filesDir, "task15-c1-gate.txt").writeText("${android.os.Build.VERSION.SDK_INT}:$passed")
        return lines.joinToString("\n")
    }
}
