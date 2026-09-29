package com.example.appsandbox.workspace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.appsandbox.GuestActivityCarrierActivity
import com.example.appsandbox.activity.GuestActivityLaunchPolicy
import com.example.appsandbox.activity.LogicalActivityStore
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.model.resolver.GuestComponentNames
import java.io.File
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
        val instance = requireNotNull(GuestInstanceStore(activity).get(instanceId)) { "Instance unavailable" }
        val current = LogicalActivityStore(File(instance.dataRoot)).current()
        require(instance.guestRevisionId == revisionId && instance.guestPackageName == packageName) { "Instance identity mismatch" }
        current?.let {
            require(GuestActivityUiState.boundTo(it, instanceId, revisionId, packageName, instance.guestSha256)) {
                "Persisted logical Activity identity mismatch"
            }
        }
        val component = GuestComponentNames.normalize(packageName, componentName)
        val spec = GuestActivityLaunchPolicy.create(
            launchId = GuestActivityUiState.resumedLaunch(current, component) ?: UUID.randomUUID().toString(),
            instanceId = instanceId,
            revisionId = revisionId,
            packageName = packageName,
            componentName = component
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
