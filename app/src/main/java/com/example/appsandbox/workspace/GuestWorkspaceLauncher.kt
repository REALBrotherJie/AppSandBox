package com.example.appsandbox.workspace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.appsandbox.GuestWorkspaceActivity
import com.example.appsandbox.storage.GuestInstanceStore

object GuestWorkspaceLauncher {
    private fun existingTask(context: Context, instanceId: String): ActivityManager.AppTask? {
        val spec = GuestWorkspaceLaunchPolicy.create(instanceId)
        return context.getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.firstOrNull { task ->
                val baseIntent = task.taskInfo.baseIntent
                baseIntent?.component?.className == GuestWorkspaceLaunchPolicy.COMPONENT_CLASS &&
                    baseIntent.dataString == spec.documentUri
            }
    }

    fun taskId(context: Context, instanceId: String): Int? {
        return existingTask(context, instanceId)?.taskInfo?.taskId
    }

    fun actionLabel(context: Context, instanceId: String): String =
        GuestWorkspaceTaskPolicy.actionLabel(taskId(context, instanceId) != null)

    fun intent(context: Context, instanceId: String): Intent {
        val spec = GuestWorkspaceLaunchPolicy.create(instanceId)
        return Intent(context, GuestWorkspaceActivity::class.java)
            .setData(Uri.parse(spec.documentUri))
            .putExtra(GuestWorkspaceActivity.EXTRA_INSTANCE_ID, spec.instanceId)
            .addFlags(spec.flags)
    }

    fun open(context: Context, instanceId: String) {
        requireNotNull(GuestInstanceStore(context).get(instanceId)) { "Instance unavailable or registry is corrupted" }
        // Document matching also restores stopped tasks without OEM AppTask calls.
        context.startActivity(intent(context, instanceId))
    }
}
