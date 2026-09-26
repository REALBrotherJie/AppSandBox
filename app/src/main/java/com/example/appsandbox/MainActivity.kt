package com.example.appsandbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.imports.GuestImportCoordinator
import com.example.appsandbox.imports.GuestImportPhase
import com.example.appsandbox.imports.GuestImportSession
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.workspace.GuestWorkspaceLauncher
import java.io.File

class MainActivity : AppCompatActivity() {
    private val importTag = "AppSandbox.Import"
    private val openApk = 1001
    private lateinit var summary: android.view.View
    private lateinit var errorText: TextView
    private var importedRecord: com.example.appsandbox.model.GuestPackageRecord? = null
    private lateinit var importSession: GuestImportSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        summary = findViewById(R.id.guestSummary)
        errorText = findViewById(R.id.errorText)
        importedRecord = runCatching { GuestStore(this).latestRecord() }.onFailure { showWorkspaceError("Guest revision 状态不可用: ${it.message}") }.getOrNull()
        importSession = GuestImportSession(importedRecord)
        importedRecord?.let { renderImportedRecord(it) }
        refreshLibrary()
        intent.getStringExtra(EXTRA_IMPORT_RESULT)?.let { showWorkspaceError(it) }
        findViewById<android.view.View>(R.id.createInstanceButton).setOnClickListener { createInstance() }
        refreshInstances()
        val experimentButton = findViewById<android.view.View>(R.id.runExperimentButton)
        if (BuildConfig.DEBUG && !intent.getBooleanExtra(EXTRA_AUTOMATION, false)) {
            experimentButton.visibility = android.view.View.VISIBLE
            experimentButton.setOnClickListener { runExp001() }
            experimentButton.setOnLongClickListener {
                runExp002()
                true
            }
            findViewById<android.view.View>(R.id.runExp003aButton).apply {
                visibility = android.view.View.VISIBLE
                setOnClickListener { runExp003a() }
            }
            findViewById<android.view.View>(R.id.runExp003b0Button).apply {
                visibility = android.view.View.VISIBLE
                setOnClickListener { runExp003b0() }
            }
        }
        findViewById<android.view.View>(R.id.importApkButton).setOnClickListener {
            importSession.selecting()
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "application/vnd.android.package-archive",
                            "application/octet-stream"
                        )
                    )
                },
                openApk
            )
        }
    }

    @Deprecated("The project targets the platform SAF callback used in this first phase.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == openApk && resultCode == Activity.RESULT_OK) {
            data?.data?.let { importUri(it) } ?: run { importSession.canceled(); findViewById<TextView>(R.id.importStatus).text = importSession.state.message }
        } else if (requestCode == openApk) {
            importSession.canceled(); findViewById<TextView>(R.id.importStatus).text = importSession.state.message
        }
    }

    private fun importUri(uri: Uri) {
        errorText.visibility = android.view.View.GONE
        importSession.importing()
        findViewById<TextView>(R.id.importStatus).text = "Importing..."
        var temporary: File? = null
        try {
            val resolver = contentResolver
            val displayName = queryDisplayName(uri)
            val providerMime = resolver.getType(uri)
            Log.i(
                importTag,
                "URI=$uri DISPLAY_NAME=${displayName ?: "(unknown)"} " +
                    "MIME=${providerMime ?: "(unknown)"} scheme=${uri.scheme} authority=${uri.authority}"
            )
            temporary = File.createTempFile("apk-import-", ".tmp", cacheDir)
            resolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open the selected document")
            val result = GuestImportCoordinator(this).importFile(temporary)
            importSession.complete(result)
            if (result.phase != GuestImportPhase.SUCCESS || result.record == null) {
                val message = result.message ?: "APK import failed"
                findViewById<TextView>(R.id.importStatus).text = message
                errorText.text = message
                errorText.visibility = android.view.View.VISIBLE
                return
            }
            val record = result.record
            val reader = GuestPackageReader(this)
            importedRecord = record
            renderImportedRecord(record)
            refreshLibrary()
            refreshInstances()
        } catch (error: Exception) {
            Log.e(importTag, "Import rejected", error)
            errorText.text = error.message ?: "APK import failed"
            errorText.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "APK import failed", Toast.LENGTH_SHORT).show()
        } finally {
            temporary?.delete()
        }
    }

    private fun createInstance() {
        val record = importedRecord ?: run { showWorkspaceError("Select a supported Guest revision first"); return }
        runCatching { GuestInstanceStore(this).create(record) }.onSuccess { refreshInstances(); refreshLibrary() }
            .onFailure { showWorkspaceError(it.message ?: "Unable to create instance") }
    }

    private fun refreshInstances() {
        val list = findViewById<android.widget.LinearLayout>(R.id.workspaceList) ?: return
        list.removeAllViews()
        val records = runCatching { GuestInstanceStore(this).list() }.getOrElse { showWorkspaceError(it.message ?: "Instance registry is corrupted"); return }
        records.forEach { instance ->
            val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            row.addView(android.widget.Button(this).apply {
                text = "Open ${instance.guestPackageName} r:${instance.guestRevisionId.take(8)} i:${instance.instanceId.take(8)}"
                setOnClickListener { runCatching { GuestWorkspaceLauncher.open(this@MainActivity, instance.instanceId) }.onFailure { showWorkspaceError(it.message ?: "Unable to open workspace") } }
            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(android.widget.Button(this).apply {
                text = "Delete instance ${instance.instanceId.take(8)}"
                setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete instance?")
                        .setMessage(instance.instanceId)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            runCatching { GuestInstanceStore(this@MainActivity).delete(instance.instanceId) }
                                .onSuccess { refreshInstances(); refreshLibrary() }
                                .onFailure { showWorkspaceError(it.message ?: "Unable to delete instance") }
                        }.show()
                }
            })
            list.addView(row)
        }
    }

    private fun showWorkspaceError(message: String) { errorText.text = message; errorText.visibility = android.view.View.VISIBLE }

    private fun refreshLibrary() {
        val list = findViewById<android.widget.LinearLayout>(R.id.guestLibraryList) ?: return
        list.removeAllViews()
        val instances = runCatching { GuestInstanceStore(this).list() }.getOrElse { emptyList() }
        val revisions = runCatching { GuestStore(this).records() }.getOrElse { showWorkspaceError("Guest Library is corrupted: ${it.message}"); return }
        revisions.sortedByDescending { it.importedAt }.forEach { record ->
            val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
            row.addView(TextView(this).apply { text = "${record.appLabel} | ${record.packageName}\nversion=${record.versionName ?: "?"} revision=${record.revisionId.take(8)} contract=v${record.contractVersion}" })
            val actions = android.widget.LinearLayout(this)
            actions.addView(android.widget.Button(this).apply { text = if (record.revisionId == importedRecord?.revisionId) "Selected ${record.revisionId.take(8)}" else "Select ${record.revisionId.take(8)}"; setOnClickListener { importedRecord = record; renderImportedRecord(record); refreshLibrary() } })
            actions.addView(android.widget.Button(this).apply { text = "Delete revision ${record.revisionId.take(8)}"; setOnClickListener {
                runCatching { GuestStore(this@MainActivity).deleteRevision(record.revisionId, GuestInstanceStore(this@MainActivity).list()) }
                    .onSuccess { if (importedRecord?.revisionId == record.revisionId) importedRecord = null; refreshLibrary(); refreshInstances() }
                    .onFailure { showWorkspaceError(it.message ?: "Unable to delete revision") }
            } })
            row.addView(actions); list.addView(row)
        }
    }

    private fun renderImportedRecord(record: com.example.appsandbox.model.GuestPackageRecord) {
        val reader = GuestPackageReader(this)
        val appInfo = reader.readApplicationInfo(record.apkPath)
        findViewById<ImageView>(R.id.appIcon).setImageDrawable(packageManager.getApplicationIcon(appInfo))
        findViewById<TextView>(R.id.appLabel).text = record.appLabel
        findViewById<TextView>(R.id.packageName).text = record.packageName
        findViewById<TextView>(R.id.version).text = "Version: ${record.versionName ?: "(none)"} (${record.versionCode})"
        findViewById<TextView>(R.id.componentCounts).text = "Revision: ${record.revisionId.take(8)}  Supported contract: v${record.contractVersion}"
        findViewById<TextView>(R.id.importStatus).text = "Imported successfully; supported Guest contract v${record.contractVersion}"
        summary.visibility = android.view.View.VISIBLE
    }

    companion object { const val EXTRA_IMPORT_RESULT = "importResult"; const val EXTRA_AUTOMATION = "automation" }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun runExp001() {
        val record = importedRecord
        if (record == null) {
            errorText.text = "Import GuestTestApp APK first"
            errorText.visibility = android.view.View.VISIBLE
            return
        }
        try {
            val runner = Class.forName("com.example.appsandbox.experiments.exp001.Exp001Runner")
            val result = runner.getMethod("run", Activity::class.java, com.example.appsandbox.model.GuestPackageRecord::class.java)
                .invoke(null, this, record) as String
            errorText.setTextColor(0xFF1B5E20.toInt())
            errorText.text = result
            errorText.visibility = android.view.View.VISIBLE
        } catch (error: Throwable) {
            errorText.setTextColor(0xFFB00020.toInt())
            errorText.text = "EXP-001 failed: ${error.cause?.message ?: error.message}"
            errorText.visibility = android.view.View.VISIBLE
        }
    }

    private fun runExp002() {
        val record = importedRecord ?: run {
            errorText.text = "Import GuestTestApp APK first"
            errorText.visibility = android.view.View.VISIBLE
            return
        }
        try {
            val runner = Class.forName("com.example.appsandbox.experiments.exp002.Exp002Runner")
            val result = runner.getMethod("run", Activity::class.java, com.example.appsandbox.model.GuestPackageRecord::class.java)
                .invoke(null, this, record) as String
            errorText.setTextColor(0xFF1B5E20.toInt())
            errorText.text = result
            errorText.visibility = android.view.View.VISIBLE
        } catch (error: Throwable) {
            errorText.setTextColor(0xFFB00020.toInt())
            errorText.text = "EXP-002 failed: ${error.cause?.message ?: error.message}"
            errorText.visibility = android.view.View.VISIBLE
        }
    }

    private fun runExp003a() {
        val record = importedRecord ?: run {
            errorText.text = "Import GuestTestApp APK first"
            errorText.visibility = android.view.View.VISIBLE
            return
        }
        try {
            val runner = Class.forName("com.example.appsandbox.experiments.exp003a.Exp003aRunner")
            val result = runner.getMethod(
                "run",
                Activity::class.java,
                com.example.appsandbox.model.GuestPackageRecord::class.java
            ).invoke(null, this, record) as String
            errorText.setTextColor(0xFF1B5E20.toInt())
            errorText.text = result
            errorText.visibility = android.view.View.VISIBLE
        } catch (error: Throwable) {
            errorText.setTextColor(0xFFB00020.toInt())
            errorText.text = "EXP-003A failed: ${error.cause?.message ?: error.message}"
            errorText.visibility = android.view.View.VISIBLE
        }
    }

    private fun runExp003b0() {
        val record = importedRecord ?: run {
            errorText.text = "Import GuestTestApp APK first"
            errorText.visibility = android.view.View.VISIBLE
            return
        }
        try {
            val runner = Class.forName("com.example.appsandbox.experiments.exp003b0.Exp003b0Runner")
            val result = runner.getMethod(
                "run",
                Activity::class.java,
                com.example.appsandbox.model.GuestPackageRecord::class.java
            ).invoke(null, this, record) as String
            errorText.setTextColor(0xFF1B5E20.toInt())
            errorText.text = result
            errorText.visibility = android.view.View.VISIBLE
        } catch (error: Throwable) {
            errorText.setTextColor(0xFFB00020.toInt())
            errorText.text = "EXP-003B0 failed: ${error.cause?.message ?: error.message}"
            errorText.visibility = android.view.View.VISIBLE
        }
    }
}
