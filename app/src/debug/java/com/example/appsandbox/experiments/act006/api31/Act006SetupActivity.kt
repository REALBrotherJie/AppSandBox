package com.example.appsandbox.experiments.act006.api31

import android.app.Activity
import android.os.Bundle
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

class Act006SetupActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId").orEmpty()
        val destination = File(filesDir, "task51-setup-$runId.result")
        val result = runCatching {
            val record = GuestStore(this).importApk(File(requireNotNull(intent.getStringExtra("stagedApk"))).inputStream()) {
                GuestPackageReader(this).read(it, File(it).parentFile!!.name)
            }
            val instance = GuestInstanceStore(this).create(record)
            "status=FINAL\nrunId=$runId\noutcome=IMPORTED\ninstanceId=${instance.instanceId}\nrevisionId=${record.revisionId}\nsha256=${record.sha256}\napkPath=${record.apkPath}\n"
        }.getOrElse { "status=FINAL\nrunId=$runId\noutcome=FAIL\nexceptionType=${it.javaClass.name}\ndetail=${it.message}\n" }
        val temp = File(filesDir, destination.name + ".tmp"); temp.writeText(result); check(temp.renameTo(destination)); finish()
    }
}
