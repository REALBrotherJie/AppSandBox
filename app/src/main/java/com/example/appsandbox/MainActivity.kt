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
import com.example.appsandbox.storage.GuestStore
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {
    private val importTag = "AppSandbox.Import"
    private val openApk = 1001
    private lateinit var summary: android.view.View
    private lateinit var errorText: TextView
    private var importedRecord: com.example.appsandbox.model.GuestPackageRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        summary = findViewById(R.id.guestSummary)
        errorText = findViewById(R.id.errorText)
        importedRecord = GuestStore(this).latestRecord()
        val experimentButton = findViewById<android.view.View>(R.id.runExperimentButton)
        if (BuildConfig.DEBUG) {
            experimentButton.visibility = android.view.View.VISIBLE
            experimentButton.setOnClickListener { runExp001() }
            experimentButton.setOnLongClickListener {
                runExp002()
                true
            }
        }
        findViewById<android.view.View>(R.id.importApkButton).setOnClickListener {
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
            data?.data?.let { importUri(it) }
        }
    }

    private fun importUri(uri: Uri) {
        errorText.visibility = android.view.View.GONE
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
            val reader = GuestPackageReader(this)
            reader.validate(temporary.absolutePath)
            val record = GuestStore(this).importApk(
                FileInputStream(temporary)
            ) { path -> reader.read(path, FileIds.fromPath(path)) }
            val info = reader.read(record.apkPath, record.internalGuestId)
            importedRecord = record
            val appInfo = reader.readApplicationInfo(record.apkPath)
            findViewById<ImageView>(R.id.appIcon).setImageDrawable(packageManager.getApplicationIcon(appInfo))
            findViewById<TextView>(R.id.appLabel).text = record.appLabel
            findViewById<TextView>(R.id.packageName).text = record.packageName
            findViewById<TextView>(R.id.version).text = "Version: ${record.versionName ?: "(none)"} (${record.versionCode})"
            findViewById<TextView>(R.id.componentCounts).text =
                "Activities: ${info.componentSummary.activityCount}  Services: ${info.componentSummary.serviceCount}\n" +
                    "Receivers: ${info.componentSummary.receiverCount}  Providers: ${info.componentSummary.providerCount}"
            findViewById<TextView>(R.id.importStatus).text = "Imported successfully"
            summary.visibility = android.view.View.VISIBLE
        } catch (error: Exception) {
            Log.e(importTag, "Import rejected", error)
            errorText.text = error.message ?: "APK import failed"
            errorText.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "APK import failed", Toast.LENGTH_SHORT).show()
        } finally {
            temporary?.delete()
        }
    }

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
}

private object FileIds {
    fun fromPath(path: String): String = java.io.File(path).parentFile?.name
        ?: error("Unable to determine guest storage id")
}
