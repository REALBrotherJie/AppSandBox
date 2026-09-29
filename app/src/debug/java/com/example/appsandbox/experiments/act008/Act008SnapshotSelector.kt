package com.example.appsandbox.experiments.act008

internal object Act008SnapshotSelector {
    fun exact(snapshots: List<Act008SessionSnapshot>, instanceId: String, runId: String): Act008SessionSnapshot? =
        snapshots.lastOrNull { it.instanceId == instanceId && it.runId == runId }
}
