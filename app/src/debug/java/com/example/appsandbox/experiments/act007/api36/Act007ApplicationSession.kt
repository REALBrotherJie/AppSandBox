package com.example.appsandbox.experiments.act007.api36

import android.content.Context
import com.example.appsandbox.experiments.act007.core.*
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File

object Act007ApplicationSessions {
    fun controller(host: Context, instance: GuestInstanceRecord): Pair<GuestApplicationSessionController, GuestPackageRecord> {
        val revision = requireNotNull(com.example.appsandbox.storage.GuestStore(host).findRevision(instance.guestRevisionId))
        return GuestApplicationSessionController(GuestApplicationSessionRegistry(File(instance.dataRoot, "files/application-sessions.json"))) to revision
    }
    fun request(instance: GuestInstanceRecord, revision: GuestPackageRecord, runId: String, operationId: String, appClass: String) =
        GuestApplicationSessionRequest(runId, operationId, instance.instanceId, revision.revisionId, requireNotNull(revision.sha256), revision.packageName, appClass, instance.dataRoot)
    fun expected(host: Context, instance: GuestInstanceRecord, revision: GuestPackageRecord, appClass: String) =
        GuestApplicationSessionExpected(instance.instanceId, revision.revisionId, requireNotNull(revision.sha256), revision.packageName, appClass, instance.dataRoot, File(host.filesDir, "guest-instances").canonicalPath)
}
