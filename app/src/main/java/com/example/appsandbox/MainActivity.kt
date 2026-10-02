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
import com.example.appsandbox.runtime.M10ArchitectureProbeCoordinator
import com.example.appsandbox.location.VirtualLocationCoordinatorClient
import com.example.appsandbox.location.VirtualLocationMode
import com.example.appsandbox.location.VirtualLocationPoint

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
        if (intent.getBooleanExtra("m10ArchProbe", false)) {
            M10ArchitectureProbeCoordinator(this).run()
            return
        }
        if (intent.action == ACTION_DELETE_VIRTUAL_INSTANCE) {
            deleteVirtualInstance(
                requireNotNull(intent.getStringExtra(EXTRA_GUEST_PACKAGE)),
                requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_ID))
            )
            return
        }
        if (intent.action == ACTION_CONFIGURE_VIRTUAL_LOCATION) {
            configureVirtualLocation(intent)
            return
        }
        if (intent.action == ACTION_QUERY_VIRTUAL_LOCATION) {
            val instanceId = requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_ID))
            val profile = VirtualLocationCoordinatorClient.get(this, instanceId)
            status.text = "Virtual location: $instanceId / ${profile?.mode ?: "ABSENT"}"
            Log.i("AppSandbox.M11", "VLOCATION_QUERY instance=$instanceId profile=${profile?.mode ?: "ABSENT"} generation=${profile?.generation ?: -1}")
            return
        }
        runFoundationProbe(intent.getIntExtra(EXTRA_STUB_SLOT, 0))
        intent.getStringExtra(EXTRA_GUEST_PACKAGE)?.let { packageName ->
            runCatching {
                VirtualActivityLauncher(this).launch(
                    packageName,
                    intent.getStringExtra(EXTRA_INSTANCE_ID) ?: "m2-default",
                    intent.getIntExtra(EXTRA_STUB_SLOT, 0),
                    Bundle(intent.extras ?: Bundle()).apply {
                        remove(EXTRA_GUEST_PACKAGE)
                        remove(EXTRA_INSTANCE_ID)
                        remove(EXTRA_STUB_SLOT)
                    }
                )
            }.onFailure { error ->
                Log.e(TAG, "M2 launch failed package=$packageName", error)
                status.append("\nM2 launch failed: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun configureVirtualLocation(intent: Intent) {
        val instanceId = requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_ID))
        val mode = VirtualLocationMode.valueOf(intent.getStringExtra("locationMode") ?: VirtualLocationMode.FIXED.name)
        val encoded = intent.getStringExtra("locationPoints").orEmpty()
        val points = encoded.split('~').filter(String::isNotBlank).map(VirtualLocationPoint::decode)
        val providers = intent.getStringExtra("locationProviders").orEmpty().split(',').filter(String::isNotBlank).toSet()
        val profile = VirtualLocationCoordinatorClient.set(this, instanceId, mode, points, providers)
        status.text = "Virtual location configured: $instanceId / ${profile.mode} / generation ${profile.generation}"
        Log.i("AppSandbox.M11", "VLOCATION_CONFIG instance=$instanceId mode=${profile.mode} generation=${profile.generation} points=${profile.points.size} providers=${profile.providers}")
    }

    private fun deleteVirtualInstance(packageName: String, instanceId: String) {
        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val replyTo = Messenger(android.os.Handler(mainLooper) { reply ->
                    val ok = reply.data.getBoolean(GuestRuntimeProtocol.KEY_OK)
                    status.text = if (ok) "Deleted $packageName / $instanceId" else
                        "Delete failed: ${reply.data.getString(GuestRuntimeProtocol.KEY_MESSAGE)}"
                    Log.i(TAG, "delete-instance reply package=$packageName instance=$instanceId ok=$ok " +
                        "slot=${reply.data.getInt(GuestRuntimeProtocol.KEY_SLOT, -1)} " +
                        "running=${reply.data.getBoolean(GuestRuntimeProtocol.KEY_RUNNING)} " +
                        "storageRemoved=${reply.data.getBoolean(GuestRuntimeProtocol.KEY_STORAGE_REMOVED)}")
                    unbindService(connection)
                    true
                })
                Messenger(binder).send(Message.obtain(null, GuestRuntimeProtocol.MSG_DELETE_INSTANCE).apply {
                    data = Bundle().apply {
                        putString(GuestRuntimeProtocol.KEY_PACKAGE_NAME, packageName)
                        putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, instanceId)
                    }
                    this.replyTo = replyTo
                })
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        bindService(Intent(this, GuestRuntimeService::class.java), connection, BIND_AUTO_CREATE)
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
        const val ACTION_DELETE_VIRTUAL_INSTANCE = "com.example.appsandbox.action.DELETE_VIRTUAL_INSTANCE"
        const val ACTION_CONFIGURE_VIRTUAL_LOCATION = "com.example.appsandbox.action.CONFIGURE_VIRTUAL_LOCATION"
        const val ACTION_QUERY_VIRTUAL_LOCATION = "com.example.appsandbox.action.QUERY_VIRTUAL_LOCATION"
        private const val TAG = "AppSandbox.M1"
    }
}
