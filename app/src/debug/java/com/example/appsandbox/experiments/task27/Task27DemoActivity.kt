package com.example.appsandbox.experiments.task27

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.appsandbox.experiments.exp003c1.Exp003c1ControlledContext
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import dalvik.system.DexClassLoader
import java.io.File

class Task27DemoActivity : Activity() {
    private lateinit var instanceStore: GuestInstanceStore
    private lateinit var hostRoot: LinearLayout
    private lateinit var summary: TextView
    private lateinit var state: TextView
    private lateinit var guestContainer: LinearLayout
    private var instances = emptyList<GuestInstanceRecord>()
    private var current: GuestInstanceRecord? = null
    private var record: GuestPackageRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceStore = GuestInstanceStore(this)
        record = loadRecord()
        if (record == null) { showError("Guest revision unavailable"); return }
        instances = instanceStore.list()
        if (instances.isEmpty()) {
            instances = listOf(instanceStore.create(record!!), instanceStore.create(record!!))
        }
        val active = File(filesDir, "guest-instances/active.txt").takeIf { it.isFile }?.readText()?.trim()
        current = instances.firstOrNull { it.instanceId == active } ?: instances.first()
        buildUi()
        render()
        writeReport("initial")
        intent.getStringExtra(EXTRA_ACTION)?.let { action ->
            window.decorView.post { runAction(action); writeReport("action=$action") }
        }
    }

    private fun loadRecord(): GuestPackageRecord? {
        val store = GuestStore(this)
        val input = File(filesDir, "task27-input.apk")
        if (input.isFile) {
            val reader = GuestPackageReader(this)
            return store.importApk(input.inputStream()) { path -> reader.read(path, File(path).parentFile!!.name) }.also { input.delete() }
        }
        return runCatching { store.latestRecord() }.getOrNull()
    }

    private fun buildUi() {
        hostRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        summary = TextView(this).apply { id = ID_SUMMARY; textSize = 14f }
        state = TextView(this).apply { id = ID_STATE; textSize = 20f; gravity = Gravity.CENTER; setPadding(8, 16, 8, 16) }
        guestContainer = LinearLayout(this).apply { id = ID_GUEST; orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER }
        instances.forEachIndexed { index, instance ->
            tabs.addView(Button(this).apply { id = if (index == 0) ID_A else ID_B; text = "Instance ${'A' + index}"; setOnClickListener { select(instance) } })
        }
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(Button(this).apply { id = ID_INCREMENT; text = "Increment"; setOnClickListener { increment() } })
        actions.addView(Button(this).apply { id = ID_RESET; text = "Reset"; setOnClickListener { reset() } })
        actions.addView(Button(this).apply { id = ID_DELETE; text = "Delete"; setOnClickListener { delete() } })
        hostRoot.addView(summary); hostRoot.addView(tabs); hostRoot.addView(actions); hostRoot.addView(state); hostRoot.addView(guestContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(hostRoot)
    }

    private fun select(instance: GuestInstanceRecord) { current = instance; persistActive(); render() }
    private fun increment() { val item = current ?: return; val value = readCounter(item) + 1; writeState(item, value); render() }
    private fun reset() { current?.let { writeState(it, 0) }; render() }
    private fun delete() {
        val item = current ?: return
        instanceStore.delete(item.instanceId)
        instances = instanceStore.list()
        current = instances.firstOrNull()
        persistActive()
        render()
    }
    private fun runAction(action: String) {
        when (action) {
            "increment-a" -> instances.firstOrNull()?.let { select(it); increment() }
            "increment-b" -> (instances.getOrNull(1) ?: instances.firstOrNull())?.let { select(it); increment() }
            "reset-a" -> instances.firstOrNull()?.let { select(it); reset() }
            "delete-a" -> instances.firstOrNull()?.let { select(it); delete() }
            "select-a" -> instances.firstOrNull()?.let { select(it); render() }
            "select-b" -> instances.getOrNull(1)?.let { select(it); render() }
        }
    }
    private fun writeReport(note: String) {
        val lines = mutableListOf(note, "guestSystemInstalled=false", "hostComponent=$componentName", "hostTaskId=$taskId", "guestActivityAttached=false", "guestLifecycleExecuted=false")
        instanceStore.list().forEachIndexed { index, item ->
            val prefs = item.preferences()
            lines += "instance${'A' + index}.id=${item.instanceId}"
            lines += "instance${'A' + index}.revision=${item.guestRevisionId}"
            lines += "instance${'A' + index}.apk=${item.guestApkPath}"
            lines += "instance${'A' + index}.sha=${item.guestSha256}"
            lines += "instance${'A' + index}.root=${item.dataRoot}"
            lines += "instance${'A' + index}.counter=${prefs.getInt("counter", 0)}"
            lines += "instance${'A' + index}.marker=${File(item.dataRoot, "files/instance-marker.txt").takeIf { it.isFile }?.readText() ?: "none"}"
        }
        File(filesDir, REPORT_FILE).writeText(lines.joinToString("\n"))
    }
    private fun persistActive() { val file = File(filesDir, "guest-instances/active.txt"); file.parentFile!!.mkdirs(); file.writeText(current?.instanceId ?: "") }
    private fun instanceContext(item: GuestInstanceRecord): Exp003c1ControlledContext {
        val guest = requireNotNull(record)
        val apk = File(guest.apkPath)
        val loader = DexClassLoader(apk.path, codeCacheDir.path, null, classLoader)
        @Suppress("DEPRECATION") val info = android.content.pm.ApplicationInfo(packageManager.getPackageArchiveInfo(apk.path, 0)!!.applicationInfo).apply { sourceDir = apk.path; publicSourceDir = apk.path; dataDir = item.dataRoot }
        return Exp003c1ControlledContext(applicationContext, loader, packageManager.getResourcesForApplication(info), info, File(item.dataRoot), item.instanceId)
    }
    private fun render() {
        val item = current
        summary.text = if (item == null) "No instances" else "Guest=${item.guestPackageName}\nrevision=${item.guestRevisionId}\ninstance=${item.instanceId}\nroot=${item.dataRoot}"
        state.text = if (item == null) "Deleted" else "Counter: ${readCounter(item)}"
        guestContainer.removeAllViews()
        if (item != null) runCatching {
            val context = instanceContext(item)
            val id = context.resources.getIdentifier("exp002_test_layout", "layout", context.packageName)
            val guestView = android.view.LayoutInflater.from(context).inflate(id, guestContainer, false)
            guestContainer.addView(guestView)
        }.onFailure { guestContainer.addView(TextView(this).apply { text = "Guest layout failed: ${it.javaClass.simpleName}" }) }
        persistActive()
    }
    private fun readCounter(item: GuestInstanceRecord): Int = runCatching { item.preferences().getInt("counter", 0) }.getOrDefault(0)
    private fun writeState(item: GuestInstanceRecord, value: Int) {
        val prefs = item.preferences(); prefs.edit().putInt("counter", value).putString("marker", "${item.instanceId}:$value").commit()
        File(item.dataRoot, "files/instance-marker.txt").apply { parentFile!!.mkdirs(); writeText("${item.instanceId}:$value") }
    }
    private fun GuestInstanceRecord.preferences(): android.content.SharedPreferences {
        val c = instanceContext(this); return c.getSharedPreferences("guest-demo", 0)
    }
    private fun showError(message: String) { setContentView(TextView(this).apply { text = message; textSize = 18f; setPadding(24, 24, 24, 24) }) }
    companion object {
        const val EXTRA_ACTION = "task27Action"
        const val REPORT_FILE = "task27-result.txt"
        const val ID_SUMMARY = 27001; const val ID_A = 27002; const val ID_B = 27003; const val ID_INCREMENT = 27004; const val ID_RESET = 27005; const val ID_DELETE = 27006; const val ID_STATE = 27007; const val ID_GUEST = 27008
    }
}
