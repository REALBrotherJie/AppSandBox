package com.example.appsandbox.dispatch

import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestComponentRequest
import com.example.appsandbox.resolver.GuestComponentResolver
import com.example.appsandbox.resolver.GuestResolutionReason
import com.example.appsandbox.resolver.GuestResolutionResult
import com.example.appsandbox.resolver.GuestRevisionSource
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.storage.GuestInstanceBinding

class GuestDispatchEngine(
    private val revisions: GuestRevisionSource,
    private val instances: GuestInstanceSource,
    private val runtime: GuestRuntimeSession = GuestRuntimeSession { _, _ -> false },
    private val artifactGate: GuestArtifactGate = GuestArtifactGate {
        GuestArtifactVerifier.verify(it).state ==
            com.example.appsandbox.storage.ArtifactState.VALID
    },
    private val stateStore: GuestDispatchStateStore = InMemoryGuestDispatchStateStore()
) {
    private val resolver = GuestComponentResolver(revisions)
    private val lock = Any()

    fun prepare(request: GuestDispatchRequest): GuestDispatchResult {
        val resolution = runCatching {
            GuestDispatchResolution.from(
                resolver.resolve(
                    GuestComponentRequest(
                        revisionId = request.component.revisionId,
                        packageName = request.component.packageName,
                        className = request.component.className,
                        type = request.component.type,
                        callerScope = request.callerScope
                    )
                )
            )
        }.getOrElse {
            return rejected(
                request.operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.STORE_CORRUPT)
            )
        }
        return prepareResolved(request, resolution)
    }

    fun prepareResolved(
        request: GuestDispatchRequest,
        resolution: GuestDispatchResolution
    ): GuestDispatchResult = synchronized(lock) {
        validateRequest(request)?.let { return@synchronized rejected(request.operationId, it) }
        stateStore.find(request.operationId)?.let { existing ->
            return@synchronized if (existing.matches(request)) {
                committed(existing)
            } else {
                rejected(
                    request.operationId,
                    GuestDispatchFailure(GuestDispatchFailureReason.DUPLICATE_OPERATION)
                )
            }
        }

        val context = validateContext(request)
            ?: return@synchronized rejected(
                request.operationId,
                lastContextFailure!!
            )
        if (!runtimeAvailable(context)) {
            return@synchronized rejected(
                request.operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.RUNTIME_UNAVAILABLE)
            )
        }
        val component = when (resolution) {
            is GuestDispatchResolution.Rejected -> {
                return@synchronized rejected(
                    request.operationId,
                    mapResolutionFailure(resolution.reason)
                )
            }
            is GuestDispatchResolution.Resolved -> {
                validateResolvedComponent(request, context, resolution.component)
                    ?: return@synchronized rejected(
                        request.operationId,
                        lastComponentFailure!!
                    )
                resolution.component
            }
        }
        val operation = runCatching {
            GuestDispatchOperation.fromCode(request.operationCode)
        }.getOrElse {
            return@synchronized rejected(
                request.operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.UNSUPPORTED_OPERATION)
            )
        }
        if (!operationAccepts(operation, component.type)) {
            return@synchronized rejected(
                request.operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.UNSUPPORTED_OPERATION)
            )
        }
        validateOperationArguments(operation, request.arguments)?.let {
            return@synchronized rejected(request.operationId, it)
        }
        val plan = createPlan(request, component, context.namespace, operation)
        GuestDispatchResult(
            operationId = request.operationId,
            state = GuestDispatchState.PLAN_READY,
            transition = GuestStateTransition(null, GuestDispatchState.PLAN_READY),
            plan = plan,
            cleanup = GuestCleanupAction.RELEASE_LOGICAL_PLAN
        )
    }

    fun dispatch(request: GuestDispatchRequest): GuestDispatchResult {
        val prepared = prepare(request)
        val plan = prepared.plan ?: return prepared
        return commit(plan)
    }

    fun commit(plan: GuestDispatchPlan): GuestDispatchResult = synchronized(lock) {
        val existing = stateStore.find(plan.operationId)
        if (existing != null) {
            return@synchronized if (existing == plan) {
                committed(existing)
            } else {
                rejected(
                    plan.operationId,
                    GuestDispatchFailure(GuestDispatchFailureReason.DUPLICATE_OPERATION)
                )
            }
        }
        val request = GuestDispatchRequest(
            operationId = plan.operationId,
            instanceId = plan.instanceId,
            component = GuestComponentIdentity(
                revisionId = plan.component.revisionId,
                packageName = plan.component.packageName,
                className = plan.component.className,
                type = plan.component.type
            ),
            callerScope = plan.callerScope,
            operationCode = plan.operation.code,
            arguments = plan.arguments.toMap()
        )
        validateRequest(request)?.let { return@synchronized rejected(plan.operationId, it) }
        val context = validateContext(request)
            ?: return@synchronized rejected(plan.operationId, lastContextFailure!!)
        if (plan.namespace != context.namespace ||
            plan.logicalResourceId != guestExpectedLogicalResourceId(
                plan.namespace,
                plan.operation,
                plan.operationId
            )
        ) {
            return@synchronized rejected(
                plan.operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.STALE_PLAN)
            )
        }
        validateResolvedComponent(request, context, plan.component)
            ?: return@synchronized rejected(plan.operationId, lastComponentFailure!!)
        if (!runtimeAvailable(context)) {
            return@synchronized rejected(
                plan.operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.RUNTIME_UNAVAILABLE)
            )
        }
        validateOperationArguments(plan.operation, plan.arguments)?.let {
            return@synchronized rejected(plan.operationId, it)
        }
        runCatching { stateStore.commit(plan) }.getOrElse {
            val reason = if (it is GuestDispatchStoreException &&
                it.message.orEmpty().contains("already belongs")
            ) {
                GuestDispatchFailureReason.DUPLICATE_OPERATION
            } else {
                GuestDispatchFailureReason.STORE_CORRUPT
            }
            return@synchronized rejected(
                plan.operationId,
                GuestDispatchFailure(reason)
            )
        }
        GuestDispatchResult(
            operationId = plan.operationId,
            state = GuestDispatchState.COMMITTED,
            transition = GuestStateTransition(
                GuestDispatchState.PLAN_READY,
                GuestDispatchState.COMMITTED
            ),
            plan = plan,
            cleanup = GuestCleanupAction.NONE
        )
    }

    fun teardownInstance(
        instanceId: String,
        operationId: String = "teardown:$instanceId"
    ): GuestDispatchResult {
        if (!GuestDispatchValidation.validToken(instanceId) ||
            !GuestDispatchValidation.validToken(operationId)
        ) {
            return rejected(
                operationId,
                GuestDispatchFailure(GuestDispatchFailureReason.INVALID_REQUEST)
            )
        }
        val removed = stateStore.removeForInstance(instanceId)
        return GuestDispatchResult(
            operationId = operationId,
            state = GuestDispatchState.CLEANED,
            transition = GuestStateTransition(
                if (removed == 0) null else GuestDispatchState.COMMITTED,
                GuestDispatchState.CLEANED
            ),
            cleanup = GuestCleanupAction.TEARDOWN_INSTANCE_NAMESPACE
        )
    }

    fun committedPlans(): List<GuestDispatchPlan> = stateStore.committed()

    private var lastContextFailure: GuestDispatchFailure? = null
    private var lastComponentFailure: GuestDispatchFailure? = null

    private data class DispatchContext(
        val instance: GuestInstanceRecord,
        val revision: GuestPackageRecord,
        val namespace: GuestLogicalNamespace
    )

    private fun validateRequest(request: GuestDispatchRequest): GuestDispatchFailure? {
        if (!GuestDispatchValidation.validToken(request.operationId) ||
            !GuestDispatchValidation.validToken(request.instanceId) ||
            !GuestDispatchValidation.validToken(request.component.revisionId) ||
            !GuestDispatchValidation.validToken(request.component.packageName) ||
            request.component.className.isBlank()
        ) {
            return GuestDispatchFailure(GuestDispatchFailureReason.INVALID_REQUEST)
        }
        return GuestDispatchValidation.validateArguments(request.arguments)?.let(::GuestDispatchFailure)
    }

    private fun validateContext(request: GuestDispatchRequest): DispatchContext? {
        lastContextFailure = null
        val instance = instances.findInstance(request.instanceId)
        if (instance == null) {
            lastContextFailure = GuestDispatchFailure(GuestDispatchFailureReason.INSTANCE_NOT_FOUND)
            return null
        }
        val revision = revisions.findRevision(request.component.revisionId)
        if (revision == null) {
            lastContextFailure = GuestDispatchFailure(GuestDispatchFailureReason.REVISION_NOT_FOUND)
            return null
        }
        val bindingFailure = GuestInstanceBinding.validate(instance, revision)
        if (bindingFailure != null) {
            lastContextFailure = GuestDispatchFailure(
                when {
                    bindingFailure == "revision mismatch" ->
                        GuestDispatchFailureReason.INSTANCE_REVISION_MISMATCH
                    bindingFailure == "package mismatch" ->
                        GuestDispatchFailureReason.PACKAGE_MISMATCH
                    else -> GuestDispatchFailureReason.ARTIFACT_MISMATCH
                }
            )
            return null
        }
        if (revision.packageName != request.component.packageName) {
            lastContextFailure = GuestDispatchFailure(GuestDispatchFailureReason.PACKAGE_MISMATCH)
            return null
        }
        if (!runCatching { artifactGate.isValid(revision) }.getOrDefault(false)) {
            lastContextFailure = GuestDispatchFailure(GuestDispatchFailureReason.ARTIFACT_UNAVAILABLE)
            return null
        }
        return DispatchContext(
            instance,
            revision,
            GuestLogicalNamespace.forInstance(instance.instanceId, revision.revisionId)
        )
    }

    private fun validateResolvedComponent(
        request: GuestDispatchRequest,
        context: DispatchContext,
        resolved: GuestComponent
    ): GuestComponent? {
        lastComponentFailure = null
        val normalizedClass = runCatching {
            GuestComponentNames.normalize(request.component.packageName, request.component.className)
        }.getOrElse {
            lastComponentFailure = GuestDispatchFailure(GuestDispatchFailureReason.INVALID_REQUEST)
            return null
        }
        if (resolved.revisionId != context.revision.revisionId ||
            resolved.packageName != context.revision.packageName
        ) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_REVISION_MISMATCH)
            return null
        }
        if (resolved.className != normalizedClass || resolved.type != request.component.type) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_TYPE_MISMATCH)
            return null
        }
        val currentMatches = context.revision.components.filter { it.className == normalizedClass }
        if (currentMatches.isEmpty()) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_NOT_FOUND)
            return null
        }
        val current = currentMatches.firstOrNull { it.type == request.component.type }
        if (current == null) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_TYPE_MISMATCH)
            return null
        }
        if (current != resolved) {
            lastComponentFailure = GuestDispatchFailure(GuestDispatchFailureReason.STALE_PLAN)
            return null
        }
        if (!current.enabled) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_DISABLED)
            return null
        }
        if (request.callerScope == GuestCallerScope.HOST_EXTERNAL && !current.exported) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_NOT_EXPORTED)
            return null
        }
        if (current.declaredPermissions.isNotEmpty()) {
            lastComponentFailure =
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_PERMISSION_REQUIRED)
            return null
        }
        return current
    }

    private fun operationAccepts(
        operation: GuestDispatchOperation,
        type: com.example.appsandbox.model.resolver.GuestComponentType
    ): Boolean = guestOperationAccepts(operation, type)

    private fun runtimeAvailable(context: DispatchContext): Boolean =
        runCatching {
            runtime.isAvailable(context.instance, context.revision)
        }.getOrDefault(false)

    private fun validateOperationArguments(
        operation: GuestDispatchOperation,
        arguments: Map<String, String>
    ): GuestDispatchFailure? {
        if (operation != GuestDispatchOperation.LOOKUP_PROVIDER) return null
        val rawUri = arguments["uri"] ?: return null
        val uri = runCatching { java.net.URI(rawUri) }.getOrNull()
        if (uri == null || uri.scheme != "content" ||
            uri.rawAuthority.isNullOrBlank() ||
            rawUri.any { it.isISOControl() || it.isWhitespace() }
        ) {
            return GuestDispatchFailure(GuestDispatchFailureReason.INVALID_ARGUMENTS)
        }
        return null
    }

    private fun createPlan(
        request: GuestDispatchRequest,
        component: GuestComponent,
        namespace: GuestLogicalNamespace,
        operation: GuestDispatchOperation
    ): GuestDispatchPlan {
        val logicalResourceId = guestExpectedLogicalResourceId(namespace, operation, request.operationId)
        val arguments = request.arguments.toMap()
        return when (operation) {
            GuestDispatchOperation.START_ACTIVITY -> ActivityPlan(
                request.operationId,
                request.instanceId,
                component,
                request.callerScope,
                arguments,
                namespace,
                logicalResourceId
            )
            GuestDispatchOperation.START_SERVICE -> ServicePlan(
                request.operationId,
                request.instanceId,
                component,
                request.callerScope,
                arguments,
                namespace,
                logicalResourceId
            )
            GuestDispatchOperation.DELIVER_RECEIVER -> ReceiverPlan(
                request.operationId,
                request.instanceId,
                component,
                request.callerScope,
                arguments,
                namespace,
                logicalResourceId
            )
            GuestDispatchOperation.LOOKUP_PROVIDER -> ProviderPlan(
                request.operationId,
                request.instanceId,
                component,
                request.callerScope,
                arguments,
                namespace,
                logicalResourceId
            )
        }
    }

    private fun mapResolutionFailure(reason: GuestResolutionReason): GuestDispatchFailure =
        when (reason) {
            GuestResolutionReason.INVALID_REQUEST ->
                GuestDispatchFailure(GuestDispatchFailureReason.INVALID_REQUEST, reason)
            GuestResolutionReason.REVISION_NOT_FOUND ->
                GuestDispatchFailure(GuestDispatchFailureReason.REVISION_NOT_FOUND, reason)
            GuestResolutionReason.PACKAGE_MISMATCH ->
                GuestDispatchFailure(GuestDispatchFailureReason.PACKAGE_MISMATCH, reason)
            GuestResolutionReason.REVISION_MISMATCH ->
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_REVISION_MISMATCH, reason)
            GuestResolutionReason.NOT_FOUND ->
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_NOT_FOUND, reason)
            GuestResolutionReason.TYPE_MISMATCH ->
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_TYPE_MISMATCH, reason)
            GuestResolutionReason.DISABLED ->
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_DISABLED, reason)
            GuestResolutionReason.NOT_EXPORTED ->
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_NOT_EXPORTED, reason)
            GuestResolutionReason.PERMISSION_REQUIRED ->
                GuestDispatchFailure(GuestDispatchFailureReason.COMPONENT_PERMISSION_REQUIRED, reason)
        }

    private fun committed(plan: GuestDispatchPlan) = GuestDispatchResult(
        operationId = plan.operationId,
        state = GuestDispatchState.COMMITTED,
        transition = GuestStateTransition(
            GuestDispatchState.COMMITTED,
            GuestDispatchState.COMMITTED
        ),
        plan = plan
    )

    private fun rejected(
        operationId: String,
        failure: GuestDispatchFailure
    ) = GuestDispatchResult(
        operationId = operationId,
        state = GuestDispatchState.REJECTED,
        transition = GuestStateTransition(null, GuestDispatchState.REJECTED),
        failure = failure,
        cleanup = GuestCleanupAction.RELEASE_LOGICAL_PLAN
    )
}
