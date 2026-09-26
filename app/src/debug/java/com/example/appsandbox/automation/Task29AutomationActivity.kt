package com.example.appsandbox.automation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.example.appsandbox.MainActivity
import com.example.appsandbox.imports.GuestImportCoordinator
import com.example.appsandbox.imports.GuestImportPhase
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.workspace.GuestWorkspaceLauncher
import java.io.File

class Task29AutomationActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        intent.getStringExtra(EXTRA_INSTANCE)?.let { id ->
            val resolved = GuestInstanceStore(this).list().singleOrNull { it.instanceId.startsWith(id, ignoreCase = true) }?.instanceId ?: id
            GuestWorkspaceLauncher.open(this, resolved)
            finish(); return
        }
        intent.getStringExtra(EXTRA_PACKAGE)?.let { packageName ->
            val ordinal = intent.getIntExtra(EXTRA_INSTANCE_ORDINAL, 0)
            val resolved = GuestInstanceStore(this).list().filter { it.guestPackageName == packageName }.sortedBy { it.createdAt }[ordinal].instanceId
            GuestWorkspaceLauncher.open(this, resolved)
            finish(); return
        }
        intent.getStringExtra(EXTRA_STALE_INSTANCE)?.let { instanceId ->
            startActivity(GuestWorkspaceLauncher.intent(this, instanceId))
            finish(); return
        }
        intent.getStringExtra(EXTRA_CREATE_PACKAGE)?.let { packageName ->
            val revision = GuestStore(this).recordsForPackage(packageName).maxBy { it.importedAt }
            val created = GuestInstanceStore(this).create(revision)
            setContentView(TextView(this).apply { text = "SUCCESS: created ${created.instanceId}"; textSize = 18f })
            return
        }
        intent.getStringExtra(EXTRA_DELETE_REVISION)?.let { prefix ->
            val store = GuestStore(this); val revision = store.records().single { it.revisionId.startsWith(prefix, true) }
            store.deleteRevision(revision.revisionId, GuestInstanceStore(this).list())
            setContentView(TextView(this).apply { text = "SUCCESS: revision deleted" }); return
        }
        intent.getStringExtra(EXTRA_TRY_DELETE_REVISION)?.let { prefix ->
            val store = GuestStore(this); val revision = store.records().single { it.revisionId.startsWith(prefix, true) }
            val text = runCatching { store.deleteRevision(revision.revisionId, GuestInstanceStore(this).list()); "UNEXPECTED: revision deleted" }
                .getOrElse { "BLOCKED: ${it.message}" }
            setContentView(TextView(this).apply { this.text = text; textSize = 18f }); return
        }
        val path = intent.getStringExtra(EXTRA_APK)
        val result = path?.let { GuestImportCoordinator(this).importFile(File(it)) }
        val text = when {
            result == null -> "FAILURE: missing staged APK"
            result.phase != GuestImportPhase.SUCCESS -> "${result.phase}: ${result.message}"
            else -> "SUCCESS: imported supported Guest contract v${result.record?.contractVersion}"
        }
        setContentView(TextView(this).apply { this.text = text; textSize = 18f; setPadding(32, 32, 32, 32) })
        if (result?.phase == GuestImportPhase.SUCCESS) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_IMPORT_RESULT, text).putExtra(MainActivity.EXTRA_AUTOMATION, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }
    companion object { const val EXTRA_APK = "stagedApk"; const val EXTRA_INSTANCE = "instanceId"; const val EXTRA_PACKAGE = "packageName"; const val EXTRA_INSTANCE_ORDINAL = "instanceOrdinal"; const val EXTRA_STALE_INSTANCE = "staleInstanceId"; const val EXTRA_CREATE_PACKAGE = "createPackage"; const val EXTRA_DELETE_REVISION = "deleteRevision"; const val EXTRA_TRY_DELETE_REVISION = "tryDeleteRevision" }
}
