package com.example.appsandbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestStore

class MainActivity : AppCompatActivity() {
    private val openApk = 1001
    private lateinit var summary: android.view.View
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        summary = findViewById(R.id.guestSummary)
        errorText = findViewById(R.id.errorText)
        findViewById<android.view.View>(R.id.importApkButton).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/vnd.android.package-archive"
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
        try {
            val resolver = contentResolver
            val record = GuestStore(this).importApk(
                resolver.openInputStream(uri) ?: error("Unable to open the selected document")
            ) { path -> GuestPackageReader(this).read(path, FileIds.fromPath(path)) }
            val info = GuestPackageReader(this).read(record.apkPath, record.internalGuestId)
            val appInfo = GuestPackageReader(this).readApplicationInfo(record.apkPath)
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
            errorText.text = error.message ?: "APK import failed"
            errorText.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "APK import failed", Toast.LENGTH_SHORT).show()
        }
    }
}

private object FileIds {
    fun fromPath(path: String): String = java.io.File(path).parentFile?.name
        ?: error("Unable to determine guest storage id")
}
