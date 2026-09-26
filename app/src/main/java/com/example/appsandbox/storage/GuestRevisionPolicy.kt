package com.example.appsandbox.storage

import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord

object GuestRevisionPolicy {
    fun remove(records: List<GuestPackageRecord>, instances: List<GuestInstanceRecord>, revisionId: String): List<GuestPackageRecord> {
        require(records.any { it.revisionId == revisionId }) { "Guest revision not found" }
        check(instances.none { it.guestRevisionId == revisionId }) { "Delete instances using this revision first" }
        return records.filterNot { it.revisionId == revisionId }
    }
}
