package com.example.appsandbox.dispatch

import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestResolutionReason
import org.json.JSONArray
import org.json.JSONObject

data class GuestComponentIdentity(
    val revisionId: String,
    val packageName: String,
    val className: String,
    val type: GuestComponentType
)

enum class GuestDispatchOperation(val code: String) {
    START_ACTIVITY("start-activity"),
    START_SERVICE("start-service"),
    DELIVER_RECEIVER("deliver-receiver"),
    LOOKUP_PROVIDER("lookup-provider");

    companion object {
        fun fromCode(code: String): GuestDispatchOperation =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("unsupported operation: $code")
    }
}

enum class GuestDispatchState(val code: String) {
    PLAN_READY("plan-ready"),
    COMMITTED("committed"),
    REJECTED("rejected"),
    CLEANED("cleaned")
}

enum class GuestDispatchFailureReason(val code: String) {
    INVALID_REQUEST("invalid-request"),
    INVALID_ARGUMENTS("invalid-arguments"),
    ARGUMENTS_TOO_LARGE("arguments-too-large"),
    INSTANCE_NOT_FOUND("instance-not-found"),
    REVISION_NOT_FOUND("revision-not-found"),
    INSTANCE_REVISION_MISMATCH("instance-revision-mismatch"),
    PACKAGE_MISMATCH("package-mismatch"),
    ARTIFACT_MISMATCH("artifact-mismatch"),
    ARTIFACT_UNAVAILABLE("artifact-unavailable"),
    COMPONENT_NOT_FOUND("component-not-found"),
    COMPONENT_TYPE_MISMATCH("component-type-mismatch"),
    COMPONENT_REVISION_MISMATCH("component-revision-mismatch"),
    COMPONENT_DISABLED("component-disabled"),
    COMPONENT_NOT_EXPORTED("component-not-exported"),
    COMPONENT_PERMISSION_REQUIRED("component-permission-required"),
    RESOLUTION_REJECTED("resolution-rejected"),
    UNSUPPORTED_OPERATION("unsupported-operation"),
    RUNTIME_UNAVAILABLE("runtime-unavailable"),
    DUPLICATE_OPERATION("duplicate-operation"),
    STALE_PLAN("stale-plan"),
    PLAN_NOT_FOUND("plan-not-found"),
    STORE_CORRUPT("store-corrupt")
}

enum class GuestCleanupAction(val code: String) {
    NONE("none"),
    RELEASE_LOGICAL_PLAN("release-logical-plan"),
    TEARDOWN_INSTANCE_NAMESPACE("teardown-instance-namespace")
}

data class GuestDispatchFailure(
    val reason: GuestDispatchFailureReason,
    val resolutionReason: GuestResolutionReason? = null
)

data class GuestStateTransition(
    val from: GuestDispatchState?,
    val to: GuestDispatchState
)

data class GuestDispatchRequest(
    val operationId: String,
    val instanceId: String,
    val component: GuestComponentIdentity,
    val callerScope: GuestCallerScope,
    val operationCode: String,
    val arguments: Map<String, String> = emptyMap()
)

data class GuestLogicalNamespace(
    val instanceId: String,
    val revisionId: String,
    val taskNamespace: String,
    val serviceNamespace: String,
    val receiverNamespace: String,
    val providerNamespace: String
) {
    companion object {
        fun forInstance(instanceId: String, revisionId: String) = GuestLogicalNamespace(
            instanceId = instanceId,
            revisionId = revisionId,
            taskNamespace = "guest:$instanceId:$revisionId:tasks",
            serviceNamespace = "guest:$instanceId:$revisionId:services",
            receiverNamespace = "guest:$instanceId:$revisionId:receivers",
            providerNamespace = "guest:$instanceId:$revisionId:providers"
        )
    }
}

sealed interface GuestDispatchPlan {
    val operationId: String
    val instanceId: String
    val component: GuestComponent
    val callerScope: GuestCallerScope
    val arguments: Map<String, String>
    val namespace: GuestLogicalNamespace
    val logicalResourceId: String
    val operation: GuestDispatchOperation

    fun matches(request: GuestDispatchRequest): Boolean =
        runCatching {
            request.operationId == operationId &&
                request.instanceId == instanceId &&
                request.component.revisionId == component.revisionId &&
                request.component.packageName == component.packageName &&
                GuestComponentNames.normalize(
                    request.component.packageName,
                    request.component.className
                ) == component.className &&
                request.component.type == component.type &&
                request.callerScope == callerScope &&
                GuestDispatchOperation.fromCode(request.operationCode) == operation &&
                request.arguments == arguments
        }.getOrDefault(false)
}

data class ActivityPlan(
    override val operationId: String,
    override val instanceId: String,
    override val component: GuestComponent,
    override val callerScope: GuestCallerScope,
    override val arguments: Map<String, String>,
    override val namespace: GuestLogicalNamespace,
    override val logicalResourceId: String
) : GuestDispatchPlan {
    override val operation = GuestDispatchOperation.START_ACTIVITY
}

data class ServicePlan(
    override val operationId: String,
    override val instanceId: String,
    override val component: GuestComponent,
    override val callerScope: GuestCallerScope,
    override val arguments: Map<String, String>,
    override val namespace: GuestLogicalNamespace,
    override val logicalResourceId: String
) : GuestDispatchPlan {
    override val operation = GuestDispatchOperation.START_SERVICE
}

data class ReceiverPlan(
    override val operationId: String,
    override val instanceId: String,
    override val component: GuestComponent,
    override val callerScope: GuestCallerScope,
    override val arguments: Map<String, String>,
    override val namespace: GuestLogicalNamespace,
    override val logicalResourceId: String
) : GuestDispatchPlan {
    override val operation = GuestDispatchOperation.DELIVER_RECEIVER
}

data class ProviderPlan(
    override val operationId: String,
    override val instanceId: String,
    override val component: GuestComponent,
    override val callerScope: GuestCallerScope,
    override val arguments: Map<String, String>,
    override val namespace: GuestLogicalNamespace,
    override val logicalResourceId: String
) : GuestDispatchPlan {
    override val operation = GuestDispatchOperation.LOOKUP_PROVIDER
}

data class GuestDispatchResult(
    val operationId: String,
    val state: GuestDispatchState,
    val transition: GuestStateTransition,
    val plan: GuestDispatchPlan? = null,
    val failure: GuestDispatchFailure? = null,
    val cleanup: GuestCleanupAction = GuestCleanupAction.NONE
)

sealed interface GuestDispatchResolution {
    data class Resolved(
        val component: GuestComponent,
        val source: Source
    ) : GuestDispatchResolution

    data class Rejected(
        val reason: GuestResolutionReason
    ) : GuestDispatchResolution

    enum class Source {
        EXPLICIT,
        IMPLICIT
    }

    companion object {
        fun from(result: com.example.appsandbox.resolver.GuestResolutionResult): GuestDispatchResolution =
            when (result) {
                is com.example.appsandbox.resolver.GuestResolutionResult.Resolved ->
                    Resolved(result.component, Source.EXPLICIT)
                is com.example.appsandbox.resolver.GuestResolutionResult.Rejected ->
                    Rejected(result.reason)
            }

        fun implicit(component: GuestComponent) = Resolved(component, Source.IMPLICIT)
    }
}

fun interface GuestInstanceSource {
    fun findInstance(instanceId: String): com.example.appsandbox.model.GuestInstanceRecord?
}

fun interface GuestArtifactGate {
    fun isValid(revision: com.example.appsandbox.model.GuestPackageRecord): Boolean
}

fun interface GuestRuntimeSession {
    fun isAvailable(
        instance: com.example.appsandbox.model.GuestInstanceRecord,
        revision: com.example.appsandbox.model.GuestPackageRecord
    ): Boolean
}

interface GuestDispatchStateStore {
    fun find(operationId: String): GuestDispatchPlan?
    fun committed(): List<GuestDispatchPlan>
    fun commit(plan: GuestDispatchPlan)
    fun removeForInstance(instanceId: String): Int
}

class GuestDispatchStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal object GuestDispatchValidation {
    const val MAX_ARGUMENT_COUNT = 32
    const val MAX_ARGUMENT_KEY_LENGTH = 64
    const val MAX_ARGUMENT_VALUE_LENGTH = 4096
    const val MAX_ARGUMENT_BYTES = 16 * 1024

    fun validToken(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= 255 &&
            value.none { it.isISOControl() || it.isWhitespace() || it == '/' || it == '\\' }

    fun validateArguments(arguments: Map<String, String>): GuestDispatchFailureReason? {
        if (arguments.size > MAX_ARGUMENT_COUNT) return GuestDispatchFailureReason.ARGUMENTS_TOO_LARGE
        var total = 0
        arguments.forEach { (key, value) ->
            if (key.length > MAX_ARGUMENT_KEY_LENGTH || value.length > MAX_ARGUMENT_VALUE_LENGTH) {
                return GuestDispatchFailureReason.ARGUMENTS_TOO_LARGE
            }
            if (key.isBlank() || key.any { it.isISOControl() || it.isWhitespace() } ||
                value.any { it.isISOControl() }
            ) {
                return GuestDispatchFailureReason.INVALID_ARGUMENTS
            }
            total += key.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
        }
        return if (total > MAX_ARGUMENT_BYTES) GuestDispatchFailureReason.ARGUMENTS_TOO_LARGE else null
    }
}

internal fun guestOperationAccepts(
    operation: GuestDispatchOperation,
    type: GuestComponentType
): Boolean = when (operation) {
    GuestDispatchOperation.START_ACTIVITY -> type == GuestComponentType.ACTIVITY
    GuestDispatchOperation.START_SERVICE -> type == GuestComponentType.SERVICE
    GuestDispatchOperation.DELIVER_RECEIVER -> type == GuestComponentType.RECEIVER
    GuestDispatchOperation.LOOKUP_PROVIDER -> type == GuestComponentType.PROVIDER
}

internal fun guestExpectedLogicalResourceId(
    namespace: GuestLogicalNamespace,
    operation: GuestDispatchOperation,
    operationId: String
): String = when (operation) {
    GuestDispatchOperation.START_ACTIVITY -> "${namespace.taskNamespace}:$operationId"
    GuestDispatchOperation.START_SERVICE -> "${namespace.serviceNamespace}:$operationId"
    GuestDispatchOperation.DELIVER_RECEIVER -> "${namespace.receiverNamespace}:$operationId"
    GuestDispatchOperation.LOOKUP_PROVIDER -> "${namespace.providerNamespace}:$operationId"
}

internal fun GuestDispatchPlan.toJson(): JSONObject {
    val component = component.toJson()
    return JSONObject()
        .put("operationId", operationId)
        .put("instanceId", instanceId)
        .put("operation", operation.code)
        .put("callerScope", callerScope.name)
        .put("logicalResourceId", logicalResourceId)
        .put("component", component)
        .put(
            "arguments",
            JSONArray().apply {
                arguments.toSortedMap().forEach { (key, value) ->
                    put(JSONObject().put("key", key).put("value", value))
                }
            }
        )
}

internal fun guestDispatchPlanFromJson(value: JSONObject): GuestDispatchPlan {
    try {
        val operation = GuestDispatchOperation.fromCode(value.getString("operation"))
        val callerScope = GuestCallerScope.valueOf(value.getString("callerScope"))
        val componentValue = value.getJSONObject("component")
        val packageName = componentValue.getString("packageName")
        val className = componentValue.getString("className")
        require(GuestComponentNames.normalize(packageName, className) == className) {
            "component class is not normalized"
        }
        val permissions = componentValue.optJSONArray("declaredPermissions").toStringList()
            .map(GuestComponentNames::normalizePermission)
        require(permissions == permissions.distinct().sorted()) {
            "component permissions are not normalized"
        }
        val component = GuestComponent(
            revisionId = componentValue.getString("revisionId"),
            packageName = packageName,
            className = className,
            type = GuestComponentType.fromCode(componentValue.getString("type")),
            enabled = componentValue.getBoolean("enabled"),
            exported = componentValue.getBoolean("exported"),
            declaredPermissions = permissions
        )
        val arguments = value.optJSONArray("arguments").toStringMap()
        val namespace = GuestLogicalNamespace.forInstance(
            value.getString("instanceId"),
            component.revisionId
        )
        val plan = when (operation) {
            GuestDispatchOperation.START_ACTIVITY -> ActivityPlan(
                value.getString("operationId"),
                value.getString("instanceId"),
                component,
                callerScope,
                arguments,
                namespace,
                value.getString("logicalResourceId")
            )
            GuestDispatchOperation.START_SERVICE -> ServicePlan(
                value.getString("operationId"),
                value.getString("instanceId"),
                component,
                callerScope,
                arguments,
                namespace,
                value.getString("logicalResourceId")
            )
            GuestDispatchOperation.DELIVER_RECEIVER -> ReceiverPlan(
                value.getString("operationId"),
                value.getString("instanceId"),
                component,
                callerScope,
                arguments,
                namespace,
                value.getString("logicalResourceId")
            )
            GuestDispatchOperation.LOOKUP_PROVIDER -> ProviderPlan(
                value.getString("operationId"),
                value.getString("instanceId"),
                component,
                callerScope,
                arguments,
                namespace,
                value.getString("logicalResourceId")
            )
        }
        require(GuestDispatchValidation.validToken(plan.operationId)) { "invalid operation id" }
        require(GuestDispatchValidation.validToken(plan.instanceId)) { "invalid instance id" }
        require(GuestDispatchValidation.validToken(component.revisionId)) { "invalid revision id" }
        require(GuestDispatchValidation.validToken(component.packageName)) { "invalid package name" }
        require(guestOperationAccepts(operation, component.type)) {
            "operation and component type mismatch"
        }
        require(GuestDispatchValidation.validateArguments(arguments) == null) {
            "invalid plan arguments"
        }
        require(plan.namespace == namespace) { "namespace mismatch" }
        require(
            plan.logicalResourceId ==
                guestExpectedLogicalResourceId(namespace, operation, plan.operationId)
        ) { "logical resource mismatch" }
        return plan
    } catch (error: Throwable) {
        throw GuestDispatchStoreException("Malformed logical dispatch plan", error)
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { getString(it) }
}

private fun JSONArray?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val entries = (0 until length()).map {
        val entry = getJSONObject(it)
        entry.getString("key") to entry.getString("value")
    }
    require(entries.map { it.first }.toSet().size == entries.size) {
        "duplicate dispatch argument"
    }
    return entries.toMap()
}
