package com.example.appsandbox.imports

import android.content.Context
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.storage.GuestStore
import java.io.File
import java.io.FileInputStream

class GuestImportCoordinator(private val context: Context) {
    fun importFile(source: File): GuestImportState {
        if (!source.isFile || source.length() == 0L) return GuestImportState(GuestImportPhase.FAILURE, message = "文件不可读或为空")
        return try {
            val reader = GuestPackageReader(context)
            reader.validate(source.path)
            val record = GuestStore(context).importApk(FileInputStream(source)) { path -> reader.read(path, source.nameWithoutExtension) }
            GuestImportState(GuestImportPhase.SUCCESS, record, "Guest contract v${record.contractVersion} 已通过")
        } catch (error: Throwable) {
            val message = error.message ?: "APK 无效"
            val unsupported = message.contains("Unsupported Guest", true) || message.contains("contract", true)
            GuestImportState(if (unsupported) GuestImportPhase.UNSUPPORTED else GuestImportPhase.FAILURE, message = if (unsupported) "Guest contract 不支持: $message" else message)
        }
    }
}
