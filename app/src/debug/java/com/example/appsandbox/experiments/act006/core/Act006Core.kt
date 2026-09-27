package com.example.appsandbox.experiments.act006.core

import org.json.JSONObject

enum class Act006Phase { RECEIVED, VALIDATED, OBJECT_CONSTRUCTED, ATTACH_PENDING, ATTACHED, PROCESS_RECOVERY_REQUIRED, REJECTED }
enum class Act006Reason { NONE, INVALID_INPUT, STALE_INSTANCE, STALE_REVISION, ARTIFACT_MISMATCH, COMPONENT_MISMATCH, NON_ACTIVITY, HOST_CARRIER_MISMATCH, API_FINGERPRINT_MISMATCH, DUPLICATE_OPERATION, DUPLICATE_LAUNCH, CONSTRUCTOR_FAILED, ATTACH_FAILED, PROCESS_RECOVERY, EXECUTION_IN_PROGRESS, CORRUPT_RESULT }

data class Act006InputSnapshot(
    val launchId: String, val operationId: String, val instanceId: String, val revisionId: String,
    val artifactSha256: String, val guestClass: String, val hostCarrier: String,
    val apiAdapterFingerprint: String
)

data class Act006Expected(
    val instanceId: String, val revisionId: String, val artifactSha256: String,
    val guestClass: String, val hostCarrier: String, val apiAdapterFingerprint: String,
    val activityClass: Boolean = true
)

data class Act006Counters(
    val constructorAttempted: Int = 0, val constructorCompleted: Int = 0,
    val attachAttempted: Int = 0, val attachCompleted: Int = 0,
    val lifecycleAttempted: Int = 0, val lifecycleCompleted: Int = 0
)

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
    val irreversible: Boolean get() = Act006Phase.ATTACH_PENDING in phases
    val attachedNoLifecycle: Boolean get() = phase == Act006Phase.ATTACHED &&
        counters.attachCompleted == 1 && counters.lifecycleAttempted == 0 && counters.lifecycleCompleted == 0
}

class Act006ProcessGuard {
    internal enum class State { READY, EXECUTING, IRREVERSIBLE }
    internal val monitor = Any()
    internal val operations = LinkedHashMap<String, Pair<Act006InputSnapshot, Act006Result>>()
    internal val launches = HashMap<String, String>()
    internal var state = State.READY

    companion object { val process = Act006ProcessGuard() }
}

class Act006StateMachine(
    private val runId: String,
    private val executor: Act006AttachExecutor,
    private val processGuard: Act006ProcessGuard = Act006ProcessGuard.process
) {
    fun execute(input: Act006InputSnapshot, expected: Act006Expected): Act006Result = synchronized(processGuard.monitor) {
        val invalid = validate(input, expected)
        if (invalid != null) return@synchronized reject(input, invalid)
        processGuard.operations[input.operationId]?.let {
            return@synchronized if (it.first == input) it.second else reject(input, Act006Reason.DUPLICATE_OPERATION)
        }
        processGuard.launches[input.launchId]?.let {
            return@synchronized reject(input, if (it == input.operationId) Act006Reason.DUPLICATE_OPERATION else Act006Reason.DUPLICATE_LAUNCH)
        }
        when (processGuard.state) {
            Act006ProcessGuard.State.EXECUTING -> return@synchronized reject(input, Act006Reason.EXECUTION_IN_PROGRESS)
            Act006ProcessGuard.State.IRREVERSIBLE -> return@synchronized reject(input, Act006Reason.PROCESS_RECOVERY)
            Act006ProcessGuard.State.READY -> processGuard.state = Act006ProcessGuard.State.EXECUTING
        }

        var result = Act006Result(runId, input, listOf(Act006Phase.RECEIVED, Act006Phase.VALIDATED), Act006Reason.NONE,
            Act006Counters(constructorAttempted = 1))
        val constructed = try { executor.construct(input) } catch (t: Throwable) {
            processGuard.state = Act006ProcessGuard.State.READY
            return@synchronized store(input, result.copy(phases = result.phases + Act006Phase.REJECTED,
                reason = Act006Reason.CONSTRUCTOR_FAILED, error = t.message))
        }
        result = result.copy(phases = result.phases + Act006Phase.OBJECT_CONSTRUCTED,
            counters = result.counters.copy(constructorCompleted = 1))
        processGuard.state = Act006ProcessGuard.State.IRREVERSIBLE
        result = result.copy(phases = result.phases + Act006Phase.ATTACH_PENDING,
            counters = result.counters.copy(attachAttempted = 1))
        try {
            executor.attach(constructed, input)
            store(input, result.copy(phases = result.phases + Act006Phase.ATTACHED,
                counters = result.counters.copy(attachCompleted = 1)))
        } catch (t: Throwable) {
            store(input, result.copy(phases = result.phases + Act006Phase.PROCESS_RECOVERY_REQUIRED,
                reason = Act006Reason.ATTACH_FAILED, error = t.message))
        }
    }

    fun recover(newRunId: String): Act006StateMachine = Act006StateMachine(newRunId, executor, processGuard)

    private fun validate(i: Act006InputSnapshot, e: Act006Expected): Act006Reason? {
        if (runId.isBlank() || listOf(i.launchId, i.operationId, i.instanceId, i.revisionId, i.artifactSha256,
                i.guestClass, i.hostCarrier, i.apiAdapterFingerprint).any(String::isBlank)) return Act006Reason.INVALID_INPUT
        if (i.instanceId != e.instanceId) return Act006Reason.STALE_INSTANCE
        if (i.revisionId != e.revisionId) return Act006Reason.STALE_REVISION
        if (i.artifactSha256 != e.artifactSha256) return Act006Reason.ARTIFACT_MISMATCH
        if (i.guestClass != e.guestClass) return Act006Reason.COMPONENT_MISMATCH
        if (!e.activityClass) return Act006Reason.NON_ACTIVITY
        if (i.hostCarrier != e.hostCarrier) return Act006Reason.HOST_CARRIER_MISMATCH
        if (i.apiAdapterFingerprint != e.apiAdapterFingerprint) return Act006Reason.API_FINGERPRINT_MISMATCH
        return null
    }

    private fun reject(i: Act006InputSnapshot, reason: Act006Reason) =
        Act006Result(runId, i, listOf(Act006Phase.RECEIVED, Act006Phase.REJECTED), reason)

    private fun store(i: Act006InputSnapshot, r: Act006Result): Act006Result {
        processGuard.operations[i.operationId] = i to r
        processGuard.launches[i.launchId] = i.operationId
        return r
    }
}

object Act006ResultCodec {
    private val stringKeys = listOf("runId", "launchId", "operationId", "instanceId", "revisionId",
        "artifactSha256", "guestClass", "hostCarrier", "apiAdapterFingerprint", "phases", "reason")
    private val countKeys = listOf("constructorAttempted", "constructorCompleted", "attachAttempted",
        "attachCompleted", "lifecycleAttempted", "lifecycleCompleted")

    fun encode(r: Act006Result): String = JSONObject().apply {
        put("runId", r.runId); put("launchId", r.input.launchId); put("operationId", r.input.operationId)
        put("instanceId", r.input.instanceId); put("revisionId", r.input.revisionId); put("artifactSha256", r.input.artifactSha256)
        put("guestClass", r.input.guestClass); put("hostCarrier", r.input.hostCarrier); put("apiAdapterFingerprint", r.input.apiAdapterFingerprint)
        put("phases", r.phases.joinToString(",")); put("reason", r.reason.name)
        put("constructorAttempted", r.counters.constructorAttempted); put("constructorCompleted", r.counters.constructorCompleted)
        put("attachAttempted", r.counters.attachAttempted); put("attachCompleted", r.counters.attachCompleted)
        put("lifecycleAttempted", r.counters.lifecycleAttempted); put("lifecycleCompleted", r.counters.lifecycleCompleted)
    }.toString()

    fun decode(raw: String): Act006Result = try {
        val o = JSONObject(raw)
        if ((stringKeys + countKeys).any { !o.has(it) || o.isNull(it) }) invalid()
        val strings = stringKeys.associateWith { key -> (o.get(key) as? String)?.takeIf(String::isNotBlank) ?: invalid() }
        val counts = countKeys.associateWith { key ->
            val value = o.get(key)
            if (value !is Number || value.toDouble() != value.toInt().toDouble() || value.toInt() !in 0..1) invalid()
            value.toInt()
        }
        val phases = strings.getValue("phases").split(",").map(Act006Phase::valueOf)
        val input = Act006InputSnapshot(strings.getValue("launchId"), strings.getValue("operationId"),
            strings.getValue("instanceId"), strings.getValue("revisionId"), strings.getValue("artifactSha256"),
            strings.getValue("guestClass"), strings.getValue("hostCarrier"), strings.getValue("apiAdapterFingerprint"))
        val counters = Act006Counters(counts.getValue("constructorAttempted"), counts.getValue("constructorCompleted"),
            counts.getValue("attachAttempted"), counts.getValue("attachCompleted"),
            counts.getValue("lifecycleAttempted"), counts.getValue("lifecycleCompleted"))
        Act006Result(strings.getValue("runId"), input, phases, Act006Reason.valueOf(strings.getValue("reason")), counters)
            .also(::validateSemantics)
    } catch (e: Throwable) { throw IllegalArgumentException("corrupt act006 result", e) }

    private fun validateSemantics(r: Act006Result) {
        if (r.phases.firstOrNull() != Act006Phase.RECEIVED || r.phases.zipWithNext().any { !validTransition(it.first, it.second) }) invalid()
        val c = r.counters
        if (c.constructorCompleted > c.constructorAttempted || c.attachCompleted > c.attachAttempted ||
            c.lifecycleCompleted > c.lifecycleAttempted || c.lifecycleAttempted != 0) invalid()
        when (r.phase) {
            Act006Phase.ATTACHED -> if (r.reason != Act006Reason.NONE || c != Act006Counters(1, 1, 1, 1, 0, 0)) invalid()
            Act006Phase.PROCESS_RECOVERY_REQUIRED -> if (r.reason != Act006Reason.ATTACH_FAILED || c != Act006Counters(1, 1, 1, 0, 0, 0)) invalid()
            Act006Phase.REJECTED -> when (r.reason) {
                Act006Reason.NONE, Act006Reason.ATTACH_FAILED -> invalid()
                Act006Reason.CONSTRUCTOR_FAILED -> if (c != Act006Counters(constructorAttempted = 1)) invalid()
                else -> if (c != Act006Counters()) invalid()
            }
            else -> invalid()
        }
    }

    private fun validTransition(a: Act006Phase, b: Act006Phase) = when (a) {
        Act006Phase.RECEIVED -> b == Act006Phase.VALIDATED || b == Act006Phase.REJECTED
        Act006Phase.VALIDATED -> b == Act006Phase.OBJECT_CONSTRUCTED || b == Act006Phase.REJECTED
        Act006Phase.OBJECT_CONSTRUCTED -> b == Act006Phase.ATTACH_PENDING
        Act006Phase.ATTACH_PENDING -> b == Act006Phase.ATTACHED || b == Act006Phase.PROCESS_RECOVERY_REQUIRED
        else -> false
    }

    private fun invalid(): Nothing = throw IllegalArgumentException()
}
