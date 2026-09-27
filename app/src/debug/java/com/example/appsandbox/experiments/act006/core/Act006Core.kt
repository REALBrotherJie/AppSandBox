package com.example.appsandbox.experiments.act006.core

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class Act006Phase { RECEIVED, VALIDATED, OBJECT_CONSTRUCTED, ATTACH_PENDING, ATTACHED, PROCESS_RECOVERY_REQUIRED, REJECTED }
enum class Act006Reason { NONE, INVALID_INPUT, STALE_INSTANCE, STALE_REVISION, ARTIFACT_MISMATCH, COMPONENT_MISMATCH, NON_ACTIVITY, HOST_CARRIER_MISMATCH, API_FINGERPRINT_MISMATCH, DUPLICATE_OPERATION, DUPLICATE_LAUNCH, CONSTRUCTOR_FAILED, ATTACH_FAILED, PROCESS_RECOVERY, INVALID_TRANSITION, CORRUPT_RESULT }

data class Act006InputSnapshot(
    val launchId: String, val operationId: String, val instanceId: String, val revisionId: String,
    val artifactSha256: String, val guestClass: String, val hostCarrier: String, val apiAdapterFingerprint: String
)

data class Act006Expected(
    val instanceId: String, val revisionId: String, val artifactSha256: String,
    val guestClass: String, val hostCarrier: String, val apiAdapterFingerprint: String,
    val activityClass: Boolean = true
)

data class Act006Counters(val constructor: Int = 0, val attach: Int = 0, val lifecycle: Int = 0)

interface Act006AttachExecutor {
    fun construct(input: Act006InputSnapshot): Any
    fun attach(instance: Any, input: Act006InputSnapshot)
}

data class Act006Result(
    val runId: String, val input: Act006InputSnapshot, val phases: List<Act006Phase>,
    val reason: Act006Reason, val counters: Act006Counters = Act006Counters(),
    val error: String? = null
) {
    val phase: Act006Phase get() = phases.last()
    val irreversible: Boolean get() = phases.any { it == Act006Phase.ATTACH_PENDING || it == Act006Phase.ATTACHED || it == Act006Phase.PROCESS_RECOVERY_REQUIRED }
    val attachedNoLifecycle: Boolean get() = phase == Act006Phase.ATTACHED && counters.lifecycle == 0
}

class Act006StateMachine(private val runId: String, private val executor: Act006AttachExecutor) {
    private val operations = LinkedHashMap<String, Pair<Act006InputSnapshot, Act006Result>>()
    private val launches = HashMap<String, String>()
    private var recoveryRequired = false
    private var irreversibleOperation: String? = null

    @Synchronized fun execute(input: Act006InputSnapshot, expected: Act006Expected): Act006Result {
        operations[input.operationId]?.let { return if (it.first == input) it.second else reject(input, Act006Reason.DUPLICATE_OPERATION) }
        launches[input.launchId]?.let { return if (it == input.operationId) reject(input, Act006Reason.DUPLICATE_OPERATION) else reject(input, Act006Reason.DUPLICATE_LAUNCH) }
        if (recoveryRequired) return reject(input, Act006Reason.PROCESS_RECOVERY)
        irreversibleOperation?.let { return reject(input, Act006Reason.INVALID_TRANSITION) }
        val received = Act006Result(runId, input, listOf(Act006Phase.RECEIVED), Act006Reason.NONE)
        val invalid = validate(input, expected)
        if (invalid != null) return store(input, received.copy(phases = listOf(Act006Phase.RECEIVED, Act006Phase.REJECTED), reason = invalid))
        var result = received.copy(phases = listOf(Act006Phase.RECEIVED, Act006Phase.VALIDATED))
        val constructed = try { executor.construct(input) } catch (t: Throwable) {
            return store(input, result.copy(phases = result.phases + Act006Phase.REJECTED, reason = Act006Reason.CONSTRUCTOR_FAILED, error = t.message))
        }
        result = result.copy(phases = result.phases + Act006Phase.OBJECT_CONSTRUCTED, counters = result.counters.copy(constructor = 1))
        result = result.copy(phases = result.phases + Act006Phase.ATTACH_PENDING)
        irreversibleOperation = input.operationId
        return try {
            executor.attach(constructed, input)
            store(input, result.copy(phases = result.phases + Act006Phase.ATTACHED, counters = result.counters.copy(attach = 1)))
        } catch (t: Throwable) {
            recoveryRequired = true
            store(input, result.copy(phases = result.phases + Act006Phase.PROCESS_RECOVERY_REQUIRED, reason = Act006Reason.ATTACH_FAILED, counters = result.counters.copy(attach = 1), error = t.message))
        }
    }

    @Synchronized fun recover(newRunId: String): Act006StateMachine = Act006StateMachine(newRunId, executor)

    private fun validate(i: Act006InputSnapshot, e: Act006Expected): Act006Reason? {
        if (listOf(i.launchId, i.operationId, i.instanceId, i.revisionId, i.artifactSha256, i.guestClass, i.hostCarrier, i.apiAdapterFingerprint).any(String::isBlank)) return Act006Reason.INVALID_INPUT
        if (i.instanceId != e.instanceId) return Act006Reason.STALE_INSTANCE
        if (i.revisionId != e.revisionId) return Act006Reason.STALE_REVISION
        if (i.artifactSha256 != e.artifactSha256) return Act006Reason.ARTIFACT_MISMATCH
        if (i.guestClass != e.guestClass) return Act006Reason.COMPONENT_MISMATCH
        if (!e.activityClass) return Act006Reason.NON_ACTIVITY
        if (i.hostCarrier != e.hostCarrier) return Act006Reason.HOST_CARRIER_MISMATCH
        if (i.apiAdapterFingerprint != e.apiAdapterFingerprint) return Act006Reason.API_FINGERPRINT_MISMATCH
        return null
    }

    private fun reject(i: Act006InputSnapshot, reason: Act006Reason) = Act006Result(runId, i, listOf(Act006Phase.RECEIVED, Act006Phase.REJECTED), reason)
    private fun store(i: Act006InputSnapshot, r: Act006Result): Act006Result { operations[i.operationId] = i to r; launches[i.launchId] = i.operationId; return r }
}

object Act006ResultCodec {
    fun encode(r: Act006Result): String = JSONObject().apply {
        put("runId", r.runId); put("launchId", r.input.launchId); put("operationId", r.input.operationId)
        put("instanceId", r.input.instanceId); put("revisionId", r.input.revisionId); put("artifactSha256", r.input.artifactSha256)
        put("guestClass", r.input.guestClass); put("hostCarrier", r.input.hostCarrier); put("apiAdapterFingerprint", r.input.apiAdapterFingerprint)
        put("phases", r.phases.joinToString(",")); put("reason", r.reason.name); put("constructor", r.counters.constructor); put("attach", r.counters.attach); put("lifecycle", r.counters.lifecycle)
    }.toString()

    fun decode(raw: String): Act006Result {
        try {
            val o = JSONObject(raw)
            val keys = listOf("runId","launchId","operationId","instanceId","revisionId","artifactSha256","guestClass","hostCarrier","apiAdapterFingerprint","phases","reason","constructor","attach","lifecycle")
            if (keys.any { !o.has(it) || o.isNull(it) }) throw IllegalArgumentException()
            val phases = o.getString("phases").split(",").map { Act006Phase.valueOf(it) }
            if (phases.isEmpty() || phases.first() != Act006Phase.RECEIVED || phases.zipWithNext().any { !validTransition(it.first, it.second) }) throw IllegalArgumentException()
            val input = Act006InputSnapshot(o.getString("launchId"), o.getString("operationId"), o.getString("instanceId"), o.getString("revisionId"), o.getString("artifactSha256"), o.getString("guestClass"), o.getString("hostCarrier"), o.getString("apiAdapterFingerprint"))
            return Act006Result(o.getString("runId"), input, phases, Act006Reason.valueOf(o.getString("reason")), Act006Counters(o.getInt("constructor"), o.getInt("attach"), o.getInt("lifecycle")))
        } catch (_: Throwable) { throw IllegalArgumentException("corrupt act006 result") }
    }
    private fun validTransition(a: Act006Phase, b: Act006Phase) = when (a) {
        Act006Phase.RECEIVED -> b == Act006Phase.VALIDATED || b == Act006Phase.REJECTED
        Act006Phase.VALIDATED -> b == Act006Phase.OBJECT_CONSTRUCTED || b == Act006Phase.REJECTED
        Act006Phase.OBJECT_CONSTRUCTED -> b == Act006Phase.ATTACH_PENDING || b == Act006Phase.REJECTED
        Act006Phase.ATTACH_PENDING -> b == Act006Phase.ATTACHED || b == Act006Phase.PROCESS_RECOVERY_REQUIRED
        else -> false
    }
}
