package com.example.appsandbox.storage

import android.content.Context
import android.util.Log
import com.example.appsandbox.model.GuestPackageRecord
import org.json.JSONArray
import java.io.File

class GuestStore(private val context: Context) {
    private val tag = "AppSandbox.Import"
    private val root: File get() = File(context.filesDir, "guests")

    fun importApk(source: java.io.InputStream, parser: (String) -> GuestPackageRecord): GuestPackageRecord {
        val id = java.util.UUID.randomUUID().toString()
        val directory = File(root, id).apply { mkdirs() }
        val apk = File(directory, "base.apk")
        try {
            apk.outputStream().use { output -> source.use { it.copyTo(output) } }
            val record = parser(apk.absolutePath)
            appendRecord(record)
            Log.i(tag, "Imported ${record.packageName} as ${record.internalGuestId}")
            return record
        } catch (error: Exception) {
            Log.e(tag, "Import failed for ${apk.absolutePath}", error)
            directory.deleteRecursively()
            throw error
        }
    }

    private fun appendRecord(record: GuestPackageRecord) {
        val file = File(root, "registry.json")
        val records = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        records.put(record.toJson())
        file.writeText(records.toString(2))
    }
}
