package com.example.appsandbox.archprobe

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.runtime.VirtualActivityLauncher

/** Debug-only driver; all Job scheduling remains inside the zero-adaptation Guest Activity. */
class JobReachabilityHarnessActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val packageName = requireNotNull(intent.getStringExtra("packageName"))
        val instanceId = requireNotNull(intent.getStringExtra("instanceId"))
        val slot = intent.getIntExtra("slot", 0)
        val extras = Bundle().apply {
            putString("mode", intent.getStringExtra("mode") ?: "false")
            putInt("jobId", intent.getIntExtra("jobId", 701))
            putLong("delayMs", intent.getLongExtra("delayMs", 1L))
            intent.getStringExtra("targetActivity")?.let { putString("m12TargetActivity", it) }
        }
        VirtualActivityLauncher(this).launch(packageName, instanceId, slot, extras)
        finish()
    }
}
