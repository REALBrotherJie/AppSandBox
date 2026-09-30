package com.example.appsandbox.virtual

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import java.util.concurrent.ConcurrentHashMap

data class LaunchEnvelope(
    val packageName: String,
    val instanceId: String,
    val target: ComponentName,
    val originalIntent: Intent,
    val activityInfo: ActivityInfo,
    val processSlot: Int,
    val runId: String,
    val dataRoot: String
) {
    fun putInto(stubIntent: Intent): Intent = stubIntent.apply {
        putExtra(KEY_MARKER, VERSION)
        putExtra(KEY_PACKAGE, packageName)
        putExtra(KEY_INSTANCE, instanceId)
        putExtra(KEY_TARGET, target.flattenToString())
        putExtra(KEY_ORIGINAL_INTENT, originalIntent)
        putExtra(KEY_ACTIVITY_INFO, activityInfo)
        putExtra(KEY_SLOT, processSlot)
        putExtra(KEY_RUN_ID, runId)
        putExtra(KEY_DATA_ROOT, dataRoot)
    }

    fun putReferenceInto(stubIntent: Intent): Intent = stubIntent.apply {
        LaunchEnvelopeRegistry.put(this@LaunchEnvelope)
        putExtra(KEY_MARKER, VERSION)
        putExtra(KEY_RUN_ID, runId)
    }

    companion object {
        private const val VERSION = 1
        private const val KEY_MARKER = "com.example.appsandbox.launch.VERSION"
        private const val KEY_PACKAGE = "com.example.appsandbox.launch.PACKAGE"
        private const val KEY_INSTANCE = "com.example.appsandbox.launch.INSTANCE"
        private const val KEY_TARGET = "com.example.appsandbox.launch.TARGET"
        private const val KEY_ORIGINAL_INTENT = "com.example.appsandbox.launch.INTENT"
        private const val KEY_ACTIVITY_INFO = "com.example.appsandbox.launch.INFO"
        private const val KEY_SLOT = "com.example.appsandbox.launch.SLOT"
        private const val KEY_RUN_ID = "com.example.appsandbox.launch.RUN"
        private const val KEY_DATA_ROOT = "com.example.appsandbox.launch.DATA"

        @Suppress("DEPRECATION")
        fun from(intent: Intent): LaunchEnvelope? {
            if (intent.getIntExtra(KEY_MARKER, 0) != VERSION) return null
            val runId = intent.getStringExtra(KEY_RUN_ID) ?: return null
            LaunchEnvelopeRegistry.get(runId)?.let { return it }
            val packageName = intent.getStringExtra(KEY_PACKAGE) ?: return null
            val instanceId = intent.getStringExtra(KEY_INSTANCE) ?: return null
            val target = intent.getStringExtra(KEY_TARGET)?.let(ComponentName::unflattenFromString) ?: return null
            val original = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(KEY_ORIGINAL_INTENT, Intent::class.java) else intent.getParcelableExtra(KEY_ORIGINAL_INTENT)
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(KEY_ACTIVITY_INFO, ActivityInfo::class.java) else intent.getParcelableExtra(KEY_ACTIVITY_INFO)
            return LaunchEnvelope(packageName, instanceId, target, original ?: return null, info ?: return null,
                intent.getIntExtra(KEY_SLOT, -1), runId,
                intent.getStringExtra(KEY_DATA_ROOT) ?: return null)
        }
    }
}

private object LaunchEnvelopeRegistry {
    private val entries = ConcurrentHashMap<String, LaunchEnvelope>()
    fun put(envelope: LaunchEnvelope) { entries[envelope.runId] = envelope }
    fun get(runId: String): LaunchEnvelope? = entries[runId]
    fun remove(runId: String) { entries.remove(runId) }
}
