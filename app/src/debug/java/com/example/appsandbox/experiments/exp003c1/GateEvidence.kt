package com.example.appsandbox.experiments.exp003c1

import android.os.Build
import com.example.appsandbox.model.GuestPackageRecord
import java.io.File
import java.security.MessageDigest

object GateEvidence {
    fun stamp(record: GuestPackageRecord, pass: Boolean): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(File(record.apkPath).readBytes()).joinToString("") { "%02x".format(it) }
        return "${Build.VERSION.SDK_INT}:$sha:$pass"
    }
}
