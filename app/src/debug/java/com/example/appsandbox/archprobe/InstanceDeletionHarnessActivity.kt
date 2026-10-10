package com.example.appsandbox.archprobe

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.example.appsandbox.runtime.GuestRuntimeProtocol
import com.example.appsandbox.runtime.GuestRuntimeService

/** Debug-only entry into the same deletion protocol used by the production UI. */
class InstanceDeletionHarnessActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        bindService(Intent(this, GuestRuntimeService::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Messenger(binder).send(Message.obtain(null, GuestRuntimeProtocol.MSG_DELETE_INSTANCE).apply {
                    data = Bundle().apply {
                        putString(GuestRuntimeProtocol.KEY_PACKAGE_NAME, requireNotNull(intent.getStringExtra("packageName")))
                        putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, requireNotNull(intent.getStringExtra("instanceId")))
                    }
                })
                Log.i("AppSandbox.M12.Job", "DELETE_HARNESS_REQUEST instance=${intent.getStringExtra("instanceId")}")
                unbindService(this)
                finish()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }, BIND_AUTO_CREATE)
    }
}
