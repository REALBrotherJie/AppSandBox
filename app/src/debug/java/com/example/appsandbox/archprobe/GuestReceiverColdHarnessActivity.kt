package com.example.appsandbox.archprobe

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.receiver.BroadcastSessionClient
import com.example.appsandbox.receiver.BroadcastSessionProvider
import com.example.appsandbox.stub.StubReceivers

/** Debug-only in-app sender for proving a not-stopped Host can cold-start a target stub process. */
class GuestReceiverColdHarnessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val guestPackage = requireNotNull(intent.getStringExtra(EXTRA_PACKAGE))
        val instanceId = requireNotNull(intent.getStringExtra(EXTRA_INSTANCE))
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        val guestAction = requireNotNull(intent.getStringExtra(EXTRA_ACTION))
        @Suppress("DEPRECATION")
        val infos = packageManager.queryBroadcastReceivers(Intent(guestAction).setPackage(guestPackage), 0)
            .sortedByDescending { it.priority }
            .mapNotNull { it.activityInfo }
        require(infos.isNotEmpty() && infos.size <= StubReceivers.MAX_RANKS)
        val identity = RuntimeIdentity.create(this, guestPackage, instanceId, slot)
        val original = Intent(guestAction).setPackage(guestPackage)
        val session = BroadcastSessionClient.create(this, identity, original, infos, ordered = true)
        val physical = Intent(StubReceivers.action(slot, infos.size)).setPackage(packageName)
            .putExtra(BroadcastSessionProvider.EXTRA_SESSION_ID, session)
        Log.i(TAG, "COLD event=SEND session=$session slot=$slot receivers=${infos.map { it.name }}")
        sendOrderedBroadcast(physical, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                Log.i(TAG, "COLD event=COMPLETE session=$session code=$resultCode data=$resultData")
                finish()
            }
        }, null, 0, null, null)
    }

    companion object {
        private const val TAG = "AppSandbox.M8"
        const val EXTRA_PACKAGE = "guestPackage"
        const val EXTRA_INSTANCE = "instanceId"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_ACTION = "guestAction"
    }
}
