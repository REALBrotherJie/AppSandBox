package com.example.appsandbox

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.appsandbox.hidden.HiddenApiAccess
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.runtime.GuestRuntimeProtocol
import com.example.appsandbox.runtime.GuestRuntimeService
import com.example.appsandbox.runtime.VirtualActivityLauncher

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { setPadding(32, 32, 32, 32) }
        val apps = GuestPackageReader(this).queryLaunchableInstalledApps()
        status.text = buildString {
            appendLine("AppSandBox virtualization foundation")
            appendLine("Launchable installed apps: ${apps.size}")
            apps.take(12).forEach { appendLine("${it.appLabel} (${it.packageName})") }
        }
        setContentView(LinearLayout(this).apply { addView(status) })
        runFoundationProbe(intent.getIntExtra(EXTRA_STUB_SLOT, 0))
        intent.getStringExtra(EXTRA_GUEST_PACKAGE)?.let { packageName ->
            runCatching {
                VirtualActivityLauncher(this).launch(
                    packageName,
                    intent.getStringExtra(EXTRA_INSTANCE_ID) ?: "m2-default",
                    intent.getIntExtra(EXTRA_STUB_SLOT, 0)
                )
            }.onFailure { error ->
                Log.e(TAG, "M2 launch failed package=$packageName", error)
                status.append("\nM2 launch failed: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun runFoundationProbe(slot: Int) {
        val hidden = HiddenApiAccess.probe()
        Log.i(TAG, "hidden-api $hidden")
        status.append("\nHidden API: ${if (hidden.isSuccess) "OK" else hidden.exceptionOrNull()?.message}")
        bindService(Intent(this, GuestRuntimeService::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val request = Message.obtain(null, GuestRuntimeProtocol.MSG_ALLOCATE).apply {
                    data = Bundle().apply {
                        putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, "m1-probe")
                        putInt(GuestRuntimeProtocol.KEY_PREFERRED_SLOT, slot)
                    }
                    replyTo = Messenger(android.os.Handler(mainLooper) { reply ->
                        val ok = reply.data.getBoolean(GuestRuntimeProtocol.KEY_OK)
                        val allocated = reply.data.getInt(GuestRuntimeProtocol.KEY_SLOT, -1)
                        status.append("\nStub pool: ${if (ok) "p$allocated started" else reply.data.getString(GuestRuntimeProtocol.KEY_MESSAGE)}")
                        Log.i(TAG, "stub-pool ok=$ok slot=$allocated")
                        true
                    })
                }
                Messenger(binder).send(request)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }, BIND_AUTO_CREATE)
    }

    companion object {
        const val EXTRA_STUB_SLOT = "stubSlot"
        const val EXTRA_GUEST_PACKAGE = "guestPackage"
        const val EXTRA_INSTANCE_ID = "instanceId"
        private const val TAG = "AppSandbox.M1"
    }
}
