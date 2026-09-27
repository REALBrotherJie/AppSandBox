package com.example.appsandbox.automation

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.dispatch.ActivityPlan
import com.example.appsandbox.dispatch.GuestArtifactGate
import com.example.appsandbox.dispatch.GuestComponentIdentity
import com.example.appsandbox.dispatch.GuestDispatchEngine
import com.example.appsandbox.dispatch.GuestDispatchFailureReason
import com.example.appsandbox.dispatch.GuestDispatchRequest
import com.example.appsandbox.dispatch.GuestDispatchState
import com.example.appsandbox.dispatch.GuestRuntimeSession
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.resolver.GuestIntentFilterNormalizer
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestImplicitCandidate
import com.example.appsandbox.resolver.GuestImplicitResolutionResult
import com.example.appsandbox.storage.GuestRegistryCodec
import org.json.JSONObject
import java.io.File

class Task48LogicalMatrixActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId") ?: return finish()
        val report = File(filesDir, "task48-matrix-$runId.result")
        report.writeText("status=STARTED\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=logical-matrix\nerrorCode=\n")
        val checks = runCatching { checks() }.getOrElse { listOf("exception" to false, "message=${it.message}" to false) }
        val failures = checks.filterNot { it.second }
        report.writeText(buildString {
            append("status=").append(if (failures.isEmpty()) "PASS" else "FAIL").append('\n')
            append("runId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=logical-matrix\n")
            append("errorCode=").append(if (failures.isEmpty()) "" else "MATRIX_FAILURE").append('\n')
            checks.forEach { append(it.first).append('=').append(if (it.second) "PASS" else "FAIL").append('\n') }
        })
        finish()
    }

    private fun checks(): List<Pair<String, Boolean>> {
        val filter = GuestIntentFilterNormalizer.normalize(REV, PKG, CLASS, GuestComponentType.ACTIVITY,
            listOf("test.ACTION"), listOf("android.intent.category.DEFAULT"), emptyList(), 0, false)
        val component = GuestComponent(REV, PKG, CLASS, GuestComponentType.ACTIVITY, true, true, intentFilters = listOf(filter))
        val revision = GuestPackageRecord("guest", PKG, "1", 1, filesDir.path, "Fixture", 1,
            ComponentSummary.fromComponents(listOf(component)), REV, SHA, 1, GuestPackageRecord.CURRENT_SCHEMA_VERSION, 1, listOf(component))
        val instance = GuestInstanceRecord(INSTANCE, REV, PKG, filesDir.path, SHA, File(filesDir, "instance").path, 1, 1)
        val engine = GuestDispatchEngine({ id -> revision.takeIf { id == REV } }, { id -> instance.takeIf { id == INSTANCE } },
            GuestRuntimeSession { _, _ -> true }, GuestArtifactGate { true })
        fun request(id: String, args: Map<String, String> = emptyMap()) = GuestDispatchRequest(id, INSTANCE,
            GuestComponentIdentity(REV, PKG, CLASS, GuestComponentType.ACTIVITY), GuestCallerScope.HOST_EXTERNAL,
            "start-activity", args)
        val candidate = GuestImplicitCandidate(component, filter)
        val unique = engine.prepareImplicit(request("unique"), GuestImplicitResolutionResult.Resolved(listOf(candidate)))
        val ambiguous = engine.prepareImplicit(request("ambiguous"), GuestImplicitResolutionResult.Ambiguous(listOf(candidate, candidate)))
        val committed = engine.commit(requireNotNull(unique.plan))
        val replay = engine.commit(requireNotNull(unique.plan))
        val duplicate = engine.dispatch(request("unique", mapOf("changed" to "true")))
        val stalePlan = (engine.prepareImplicit(request("stale"), GuestImplicitResolutionResult.Resolved(listOf(candidate))).plan as ActivityPlan)
        val stale = engine.commit(stalePlan.copy(logicalResourceId = "tampered"))

        val schema3 = JSONObject(GuestRegistryCodec.encode(listOf(revision))).apply {
            put("schemaVersion", 3)
            getJSONArray("records").getJSONObject(0).put("schemaVersion", 3)
        }
        val migrated = GuestRegistryCodec.decode(schema3.toString()).single()
        return listOf(
            "implicitUniquePlan" to (unique.state == GuestDispatchState.PLAN_READY && unique.plan != null),
            "ambiguousNoPlan" to (ambiguous.plan == null && ambiguous.failure?.reason == GuestDispatchFailureReason.AMBIGUOUS_RESOLUTION),
            "commit" to (committed.state == GuestDispatchState.COMMITTED),
            "sameOperationIdempotent" to (replay.state == GuestDispatchState.COMMITTED),
            "differentRequestRejected" to (duplicate.failure?.reason == GuestDispatchFailureReason.DUPLICATE_OPERATION),
            "stalePlanRejected" to (stale.failure?.reason == GuestDispatchFailureReason.STALE_PLAN),
            "schema3Migrated" to (migrated.schemaVersion == GuestPackageRecord.CURRENT_SCHEMA_VERSION),
            "migrationIdentityPreserved" to (migrated.revisionId == REV && migrated.sha256 == SHA && migrated.components.single().className == CLASS)
        )
    }

    companion object {
        private const val REV = "revision-a"
        private const val PKG = "com.example.logical"
        private const val CLASS = "com.example.logical.Activity"
        private const val INSTANCE = "11111111-1111-4111-8111-111111111111"
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
