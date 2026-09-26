package com.example.appsandbox.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.appsandbox.GuestWorkspaceActivity
import com.example.appsandbox.storage.GuestInstanceStore

object GuestWorkspaceLauncher {
    fun intent(context: Context, instanceId: String): Intent {
        val spec = GuestWorkspaceLaunchPolicy.create(instanceId)
        return Intent(context, GuestWorkspaceActivity::class.java)
            .setData(Uri.parse(spec.documentUri))
            .putExtra(GuestWorkspaceActivity.EXTRA_INSTANCE_ID, spec.instanceId)
            .addFlags(spec.flags)
    }

    fun open(context: Context, instanceId: String) {
        requireNotNull(GuestInstanceStore(context).get(instanceId)) { "Instance unavailable or registry is corrupted" }
        context.startActivity(intent(context, instanceId))
    }
}
