package com.example.appsandbox.storage

import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File

object GuestInstanceBinding {
    fun validate(instance: GuestInstanceRecord, revision: GuestPackageRecord): String? {
        if (instance.guestRevisionId != revision.revisionId) return "revision mismatch"
        if (instance.guestPackageName != revision.packageName) return "package mismatch"
        if (File(instance.guestApkPath).canonicalFile != File(revision.apkPath).canonicalFile) return "APK path mismatch"
        if (!instance.guestSha256.equals(revision.sha256, true)) return "SHA mismatch"
        return null
    }
}
