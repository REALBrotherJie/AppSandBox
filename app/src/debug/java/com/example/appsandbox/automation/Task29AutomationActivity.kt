package com.example.appsandbox.automation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.example.appsandbox.MainActivity
import com.example.appsandbox.imports.GuestImportCoordinator
import com.example.appsandbox.imports.GuestImportPhase
import java.io.File

class Task29AutomationActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val path = intent.getStringExtra(EXTRA_APK)
        val result = path?.let { GuestImportCoordinator(this).importFile(File(it)) }
        val text = when {
            result == null -> "FAILURE: missing staged APK"
            result.phase != GuestImportPhase.SUCCESS -> "${result.phase}: ${result.message}"
            else -> "SUCCESS: imported supported Guest contract v1"
        }
        setContentView(TextView(this).apply { this.text = text; textSize = 18f; setPadding(32, 32, 32, 32) })
        if (result?.phase == GuestImportPhase.SUCCESS) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_IMPORT_RESULT, text).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }
    companion object { const val EXTRA_APK = "stagedApk" }
}
