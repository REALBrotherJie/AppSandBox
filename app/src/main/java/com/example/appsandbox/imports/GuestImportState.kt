package com.example.appsandbox.imports

import com.example.appsandbox.model.GuestPackageRecord

enum class GuestImportPhase { EMPTY, SELECTING, IMPORTING, SUCCESS, FAILURE, UNSUPPORTED }
data class GuestImportState(val phase: GuestImportPhase, val record: GuestPackageRecord? = null, val message: String? = null)
