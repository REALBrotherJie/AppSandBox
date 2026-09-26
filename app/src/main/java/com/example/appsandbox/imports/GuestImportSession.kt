package com.example.appsandbox.imports

import com.example.appsandbox.model.GuestPackageRecord

class GuestImportSession(restored: GuestPackageRecord? = null) {
    var state: GuestImportState = restored?.let { GuestImportState(GuestImportPhase.SUCCESS, it, "Restored supported Guest") }
        ?: GuestImportState(GuestImportPhase.EMPTY)
        private set

    fun selecting() { state = state.copy(phase = GuestImportPhase.SELECTING, message = null) }
    fun importing() { state = state.copy(phase = GuestImportPhase.IMPORTING, message = null) }
    fun canceled() { state = state.record?.let { GuestImportState(GuestImportPhase.SUCCESS, it, "Import canceled; previous Guest retained") } ?: GuestImportState(GuestImportPhase.EMPTY, message = "Import canceled") }
    fun complete(result: GuestImportState) {
        state = if (result.phase == GuestImportPhase.SUCCESS) result else result.copy(record = state.record)
    }
}
