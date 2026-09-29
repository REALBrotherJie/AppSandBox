package com.example.appsandbox.experiments.act008

import java.util.LinkedHashMap

internal class Act008RequestLedger(private val capacity: Int = 128) {
    private data class Identity(val operationId: String, val runId: String, val instanceId: String)
    private val entries = object : LinkedHashMap<String, Identity>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Identity>?) = size > capacity
    }

    @Synchronized fun check(requestId: String, operationId: String, runId: String, instanceId: String): Boolean? {
        val identity = Identity(operationId, runId, instanceId)
        val old = entries[requestId]
        if (old != null) return old == identity
        entries[requestId] = identity
        return null
    }
}
