package com.example.appsandbox

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.appsandbox.experiments.GuestWorkspaceContext
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import dalvik.system.DexClassLoader
import java.io.File

class GuestWorkspaceActivity : Activity() {
    private lateinit var instance: GuestInstanceRecord
    private lateinit var state: TextView
    private lateinit var guestRoot: LinearLayout
    private lateinit var store: GuestInstanceStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuestInstanceStore(this)
        val found = intent.getStringExtra(EXTRA_INSTANCE_ID)?.let { store.get(it) }
        if (found == null) { show("Instance unavailable or registry is corrupted"); return }
        instance = found
        buildUi(); render()
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "Guest workspace\ninstance=${instance.instanceId}\nrevision=${instance.guestRevisionId}"; textSize = 16f })
        state = TextView(this).apply { textSize = 22f; setPadding(0, 24, 0, 24) }
        val actions = LinearLayout(this)
        actions.addView(Button(this).apply { text = "Increment"; setOnClickListener { writeCounter(counter() + 1); render() } })
        actions.addView(Button(this).apply { text = "Reset"; setOnClickListener { writeCounter(0); render() } })
        guestRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(state); root.addView(actions); root.addView(guestRoot, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }
    private fun render() {
        state.text = "Counter: ${counter()}"; guestRoot.removeAllViews()
        runCatching {
            val apk = File(instance.guestApkPath); val reader = GuestPackageReader(this); val info = reader.readApplicationInfo(apk.path)
            val metadata = info.metaData ?: error("Unsupported Guest: missing View contract")
            require(metadata.getInt(GuestPackageReader.CONTRACT_VERSION, 0) == 1) { "Unsupported Guest: contract version" }
            val layoutName = metadata.getString(GuestPackageReader.VIEW_LAYOUT) ?: error("Unsupported Guest: missing View layout")
            val loader = DexClassLoader(apk.path, codeCacheDir.path, null, classLoader)
            val context = GuestWorkspaceContext(this, loader, packageManager.getResourcesForApplication(info), info, File(instance.dataRoot))
            val layoutId = context.resources.getIdentifier(layoutName, "layout", context.packageName)
            require(layoutId != 0) { "Unsupported Guest: layout resource missing" }
            guestRoot.addView(android.view.LayoutInflater.from(context).inflate(layoutId, guestRoot, false))
        }.onFailure { guestRoot.addView(TextView(this).apply { text = it.message ?: "Unsupported Guest" }) }
    }
    private fun stateFile() = File(instance.dataRoot, "files/counter.txt")
    private fun counter() = runCatching { stateFile().readText().trim().toInt() }.getOrDefault(0)
    private fun writeCounter(value: Int) { stateFile().apply { parentFile!!.mkdirs(); writeText(value.toString()) } }
    private fun show(message: String) { setContentView(TextView(this).apply { text = message; textSize = 18f; setPadding(24, 24, 24, 24) }) }
    companion object { const val EXTRA_INSTANCE_ID = "instanceId" }
}
