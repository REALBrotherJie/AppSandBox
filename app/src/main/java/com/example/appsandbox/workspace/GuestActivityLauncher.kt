package com.example.appsandbox.workspace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.appsandbox.GuestActivityCarrierActivity
import com.example.appsandbox.activity.GuestActivityLaunchPolicy
import java.util.UUID

object GuestActivityLauncher {
    fun openForResult(
        activity: Activity,
        instanceId: String,
        revisionId: String,
        packageName: String,
        componentName: String,
        requestCode: Int
    ): String {
        val spec = GuestActivityLaunchPolicy.create(
            instanceId = instanceId,
            revisionId = revisionId,
            packageName = packageName,
            componentName = componentName
        )
        activity.startActivityForResult(intent(activity, spec), requestCode)
        return spec.launchId
    }

    private fun intent(context: Context, spec: com.example.appsandbox.activity.GuestActivityLaunchSpec) =
        Intent(context, GuestActivityCarrierActivity::class.java)
            .setData(Uri.parse(spec.documentUri))
            .putExtra(GuestActivityLaunchPolicy.EXTRA_LAUNCH_ID, spec.launchId)
            .putExtra(GuestActivityLaunchPolicy.EXTRA_INSTANCE_ID, spec.instanceId)
            .putExtra(GuestActivityLaunchPolicy.EXTRA_REVISION_ID, spec.revisionId)
            .putExtra(GuestActivityLaunchPolicy.EXTRA_PACKAGE_NAME, spec.packageName)
            .putExtra(GuestActivityLaunchPolicy.EXTRA_COMPONENT_NAME, spec.componentName)
}
