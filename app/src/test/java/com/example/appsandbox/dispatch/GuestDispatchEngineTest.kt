package com.example.appsandbox.dispatch

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestResolutionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuestDispatchEngineTest {
    private val packageName = "com.example.dispatchfixture"
    private val revisionId = "revision-a"
    private val sha = "a".repeat(64)
    private val instanceA = "11111111-1111-4111-8111-111111111111"
    private val instanceB = "22222222-2222-4222-8222-222222222222"

    @Test
    fun allComponentTypesMapToPlansAndNamespacesStayIsolated() {
        val components = listOf(
            component("Activity", GuestComponentType.ACTIVITY),
            component("Service", GuestComponentType.SERVICE),
            component("Receiver", GuestComponentType.RECEIVER),
            component("Provider", GuestComponentType.PROVIDER)
        )
        val revision = record(revisionId, components)
        val instances = mutableMapOf(
            instanceA to instance(revision, instanceA),
            instanceB to instance(revision, instanceB)
        )
        val engine = engine(revision, instances)

        val activity = engine.dispatch(request("op-activity", instanceA, "Activity", "start-activity"))
        val service = engine.dispatch(request("op-service", instanceA, "Service", "start-service"))
        val receiver = engine.dispatch(request("op-receiver", instanceA, "Receiver", "deliver-receiver"))
        val provider = engine.dispatch(request("op-provider", instanceA, "Provider", "lookup-provider"))
        val otherActivity = engine.dispatch(request("op-other", instanceB, "Activity", "start-activity"))

        assertTrue(activity.plan is ActivityPlan)
        assertTrue(service.plan is ServicePlan)
        assertTrue(receiver.plan is ReceiverPlan)
        assertTrue(provider.plan is ProviderPlan)
        assertEquals(GuestDispatchState.COMMITTED, activity.state)
        assertNotEquals(activity.plan!!.namespace, otherActivity.plan!!.namespace)
        assertNotEquals(activity.plan.logicalResourceId, otherActivity.plan.logicalResourceId)
        assertTrue(engine.committedPlans().all { it.instanceId == instanceA || it.instanceId == instanceB })
    }

    @Test
    fun implicitResolvedComponentUsesTheSamePlanPolicyWithoutImplementingAnotherResolver() {
        val component = component("Receiver", GuestComponentType.RECEIVER)
        val revision = record(revisionId, listOf(component))
        val instances = mutableMapOf(instanceA to instance(revision, instanceA))
        val engine = engine(revision, instances)
        val request = request("implicit-op", instanceA, "Receiver", "deliver-receiver")

        val prepared = engine.prepareResolved(request, GuestDispatchResolution.implicit(component))

        assertEquals(GuestDispatchState.PLAN_READY, prepared.state)
        assertTrue(prepared.plan is ReceiverPlan)
        assertEquals(GuestDispatchState.COMMITTED, engine.commit(prepared.plan!!).state)
    }

    @Test
    fun duplicateOperationIsIdempotentOnlyForTheSameRequest() {
        val component = component("Activity", GuestComponentType.ACTIVITY)
        val revision = record(revisionId, listOf(component))
        val instances = mutableMapOf(instanceA to instance(revision, instanceA))
        val engine = engine(revision, instances)
        val firstRequest = request("same-op", instanceA, "Activity", "start-activity", mapOf("key" to "one"))

        val first = engine.dispatch(firstRequest)
        val replay = engine.dispatch(firstRequest)
        val conflict = engine.dispatch(
            firstRequest.copy(arguments = mapOf("key" to "two"))
        )

        assertEquals(GuestDispatchState.COMMITTED, first.state)
        assertEquals(GuestDispatchState.COMMITTED, replay.state)
        assertEquals(first.plan, replay.plan)
        assertEquals(GuestDispatchFailureReason.DUPLICATE_OPERATION, conflict.failure!!.reason)
    }

    @Test
    fun deletedInstanceAndTamperedPlanFailClosed() {
        val component = component("Activity", GuestComponentType.ACTIVITY)
        val revision = record(revisionId, listOf(component))
        val instances = mutableMapOf(instanceA to instance(revision, instanceA))
        val engine = engine(revision, instances)
        val request = request("stale-op", instanceA, "Activity", "start-activity")
        val prepared = engine.prepare(request)
        val fresh = engine.prepare(request("tampered-op", instanceA, "Activity", "start-activity"))

        val tampered = fresh.plan!! as ActivityPlan
        val stale = engine.commit(tampered.copy(logicalResourceId = "wrong-resource"))
        assertEquals(GuestDispatchFailureReason.STALE_PLAN, stale.failure!!.reason)

        instances.remove(instanceA)
        val deleted = engine.commit(prepared.plan!!)
        assertEquals(GuestDispatchFailureReason.INSTANCE_NOT_FOUND, deleted.failure!!.reason)
    }

    @Test
    fun instanceRevisionMismatchAndResolverRejectionsRemainStable() {
        val disabled = component("Disabled", GuestComponentType.SERVICE, enabled = false)
        val privateComponent = component("Private", GuestComponentType.ACTIVITY, exported = false)
        val protected = component(
            "Protected",
            GuestComponentType.SERVICE,
            permissions = listOf("$packageName.permission.TEST")
        )
        val revision = record(revisionId, listOf(disabled, privateComponent, protected))
        val otherRevision = record("revision-b", listOf(component("Other", GuestComponentType.ACTIVITY)))
        val instances = mutableMapOf(instanceA to instance(revision, instanceA))
        val engine = engine(mapOf(revisionId to revision, "revision-b" to otherRevision), instances)

        assertEquals(
            GuestDispatchFailureReason.COMPONENT_DISABLED,
            engine.dispatch(request("disabled", instanceA, "Disabled", "start-service")).failure!!.reason
        )
        assertEquals(
            GuestResolutionReason.DISABLED,
            engine.dispatch(request("disabled-2", instanceA, "Disabled", "start-service"))
                .failure!!.resolutionReason
        )
        assertEquals(
            GuestDispatchFailureReason.COMPONENT_NOT_EXPORTED,
            engine.dispatch(
                request(
                    "private",
                    instanceA,
                    "Private",
                    "start-activity",
                    callerScope = GuestCallerScope.HOST_EXTERNAL
                )
            ).failure!!.reason
        )
        assertEquals(
            GuestDispatchFailureReason.COMPONENT_PERMISSION_REQUIRED,
            engine.dispatch(request("protected", instanceA, "Protected", "start-service")).failure!!.reason
        )

        instances[instanceA] = instance(revision, instanceA).copy(guestRevisionId = "revision-b")
        assertEquals(
            GuestDispatchFailureReason.INSTANCE_REVISION_MISMATCH,
            engine.dispatch(request("binding", instanceA, "Disabled", "start-service")).failure!!.reason
        )
    }

    @Test
    fun runtimeUnavailableAndUnsupportedInputsFailClosed() {
        val component = component("Activity", GuestComponentType.ACTIVITY)
        val revision = record(revisionId, listOf(component))
        val instances = mutableMapOf(instanceA to instance(revision, instanceA))
        val unavailable = GuestDispatchEngine(
            revisions = { id -> if (id == revisionId) revision else null },
            instances = { id -> instances[id] },
            runtime = GuestRuntimeSession { _, _ -> error("runtime process is down") },
            artifactGate = GuestArtifactGate { true }
        )
        assertEquals(
            GuestDispatchFailureReason.RUNTIME_UNAVAILABLE,
            unavailable.dispatch(request("runtime", instanceA, "Activity", "start-activity"))
                .failure!!.reason
        )
        assertTrue(unavailable.committedPlans().isEmpty())

        val available = engine(revision, instances)
        assertEquals(
            GuestDispatchFailureReason.UNSUPPORTED_OPERATION,
            available.dispatch(request("operation", instanceA, "Activity", "stop-service")).failure!!.reason
        )
        assertEquals(
            GuestDispatchFailureReason.ARGUMENTS_TOO_LARGE,
            available.dispatch(
                request(
                    "large",
                    instanceA,
                    "Activity",
                    "start-activity",
                    arguments = mapOf("payload" to "x".repeat(GuestDispatchValidation.MAX_ARGUMENT_VALUE_LENGTH + 1))
                )
            ).failure!!.reason
        )
    }

    @Test
    fun providerUriIsValidatedWithoutCallingAProvider() {
        val component = component("Provider", GuestComponentType.PROVIDER)
        val revision = record(revisionId, listOf(component))
        val instances = mutableMapOf(instanceA to instance(revision, instanceA))
        val engine = engine(revision, instances)

        val valid = engine.dispatch(
            request(
                "provider-valid",
                instanceA,
                "Provider",
                "lookup-provider",
                arguments = mapOf("uri" to "content://$packageName/items/1")
            )
        )
        val invalid = engine.dispatch(
            request(
                "provider-invalid",
                instanceA,
                "Provider",
                "lookup-provider",
                arguments = mapOf("uri" to "not a uri")
            )
        )

        assertEquals(GuestDispatchState.COMMITTED, valid.state)
        assertEquals(GuestDispatchFailureReason.INVALID_ARGUMENTS, invalid.failure!!.reason)
    }

    @Test
    fun teardownOnlyRemovesTheSelectedInstanceNamespace() {
        val component = component("Activity", GuestComponentType.ACTIVITY)
        val revision = record(revisionId, listOf(component))
        val instances = mutableMapOf(
            instanceA to instance(revision, instanceA),
            instanceB to instance(revision, instanceB)
        )
        val engine = engine(revision, instances)
        engine.dispatch(request("a-op", instanceA, "Activity", "start-activity"))
        engine.dispatch(request("b-op", instanceB, "Activity", "start-activity"))

        val cleanup = engine.teardownInstance(instanceA)

        assertEquals(GuestDispatchState.CLEANED, cleanup.state)
        assertEquals(listOf("b-op"), engine.committedPlans().map { it.operationId })
        assertEquals(GuestCleanupAction.TEARDOWN_INSTANCE_NAMESPACE, cleanup.cleanup)
    }

    @Test
    fun committedStateRecoversButPreparedStateDoesNotBecomeVisible() {
        val root = createTempDir("dispatch-state")
        try {
            val component = component("Activity", GuestComponentType.ACTIVITY)
            val revision = record(revisionId, listOf(component))
            val instances = mutableMapOf(instanceA to instance(revision, instanceA))
            val file = File(root, "dispatch.json")
            val store = FileGuestDispatchStateStore(file)
            val engine = engine(revision, instances, store = store)
            val prepared = engine.prepare(request("prepared", instanceA, "Activity", "start-activity"))
            assertTrue(prepared.plan != null)
            assertTrue(!file.exists())

            engine.dispatch(request("committed", instanceA, "Activity", "start-activity"))
            val recovered = FileGuestDispatchStateStore(file)

            assertEquals(listOf("committed"), recovered.committed().map { it.operationId })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptPrimaryRecoversOnlyFromTheLastCommittedBackup() {
        val root = createTempDir("dispatch-recovery")
        try {
            val component = component("Activity", GuestComponentType.ACTIVITY)
            val revision = record(revisionId, listOf(component))
            val instances = mutableMapOf(instanceA to instance(revision, instanceA))
            val file = File(root, "dispatch.json")
            val engine = engine(
                revision,
                instances,
                FileGuestDispatchStateStore(file)
            )
            engine.dispatch(request("first", instanceA, "Activity", "start-activity"))
            engine.dispatch(request("second", instanceA, "Activity", "start-activity"))
            file.writeText("corrupt")

            val recovered = FileGuestDispatchStateStore(file)

            assertEquals(listOf("first"), recovered.committed().map { it.operationId })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun engine(
        revision: GuestPackageRecord,
        instances: MutableMap<String, GuestInstanceRecord>,
        store: GuestDispatchStateStore = InMemoryGuestDispatchStateStore()
    ) = engine(mapOf(revision.revisionId to revision), instances, store)

    private fun engine(
        revisions: Map<String, GuestPackageRecord>,
        instances: MutableMap<String, GuestInstanceRecord>,
        store: GuestDispatchStateStore = InMemoryGuestDispatchStateStore()
    ) = GuestDispatchEngine(
        revisions = { id -> revisions[id] },
        instances = { id -> instances[id] },
        runtime = GuestRuntimeSession { _, _ -> true },
        artifactGate = GuestArtifactGate { true },
        stateStore = store
    )

    private fun request(
        operationId: String,
        instanceId: String,
        className: String,
        operation: String,
        arguments: Map<String, String> = emptyMap(),
        callerScope: GuestCallerScope = GuestCallerScope.HOST_EXTERNAL
    ) = GuestDispatchRequest(
        operationId = operationId,
        instanceId = instanceId,
        component = GuestComponentIdentity(
            revisionId = revisionId,
            packageName = packageName,
            className = "$packageName.$className",
            type = when (operation) {
                "start-service" -> GuestComponentType.SERVICE
                "deliver-receiver" -> GuestComponentType.RECEIVER
                "lookup-provider" -> GuestComponentType.PROVIDER
                else -> GuestComponentType.ACTIVITY
            }
        ),
        callerScope = callerScope,
        operationCode = operation,
        arguments = arguments
    )

    private fun component(
        className: String,
        type: GuestComponentType,
        enabled: Boolean = true,
        exported: Boolean = true,
        permissions: List<String> = emptyList()
    ) = GuestComponent(
        revisionId = revisionId,
        packageName = packageName,
        className = GuestComponentNames.normalize(packageName, className),
        type = type,
        enabled = enabled,
        exported = exported,
        declaredPermissions = permissions
    )

    private fun record(id: String, components: List<GuestComponent>) = GuestPackageRecord(
        internalGuestId = id,
        packageName = packageName,
        versionName = "1",
        versionCode = 1,
        apkPath = "/immutable/$id/base.apk",
        appLabel = "Dispatch Fixture",
        importedAt = 1,
        componentSummary = ComponentSummary.fromComponents(components),
        revisionId = id,
        sha256 = sha,
        fileSize = 1,
        schemaVersion = GuestPackageRecord.CURRENT_SCHEMA_VERSION,
        components = components.map { it.copy(revisionId = id) }
    )

    private fun instance(record: GuestPackageRecord, id: String) = GuestInstanceRecord(
        instanceId = id,
        guestRevisionId = record.revisionId,
        guestPackageName = record.packageName,
        guestApkPath = record.apkPath,
        guestSha256 = sha,
        dataRoot = "/instances/$id",
        createdAt = 1,
        updatedAt = 1
    )
}
