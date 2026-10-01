package com.example.appsandbox.vpm

import android.content.Context
import android.util.AtomicFile
import com.example.appsandbox.virtual.VirtualPackageSnapshot
import android.content.pm.PackageInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VirtualPackageRecord(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val signingSummary: String,
    val requestedPermissions: List<String>,
    val declaredPermissions: List<String>,
    val instances: Map<String, VirtualInstanceRecord>
)

data class VirtualInstanceRecord(val instanceId: String, val virtualUid: Int, val processSlot: Int, val dataRoot: String)

class VirtualPackageRegistry(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "virtual/packages/registry.json").apply { parentFile?.mkdirs() })

    @Synchronized fun registerPackage(snapshot: VirtualPackageSnapshot, info: PackageInfo): VirtualPackageRecord {
        val current = readAll().associateBy { it.packageName }.toMutableMap()
        val previous = current[snapshot.packageName]
        val record = VirtualPackageRecord(
            snapshot.packageName, snapshot.versionCode, snapshot.versionName, snapshot.sourceDir,
            snapshot.splitSourceDirs, snapshot.activities.map { it.name }, info.services.orEmpty().map { it.name },
            info.receivers.orEmpty().map { it.name }, info.providers.orEmpty().map { it.name }, snapshot.signingSummary,
            info.requestedPermissions.orEmpty().distinct().sorted(), info.permissions.orEmpty().map { it.name }.distinct().sorted(),
            previous?.instances.orEmpty()
        )
        current[record.packageName] = record
        write(current.values.sortedBy { it.packageName })
        return record
    }

    @Synchronized fun registerInstance(packageName: String, instance: VirtualInstanceRecord) {
        val current = readAll().associateBy { it.packageName }.toMutableMap()
        val pkg = requireNotNull(current[packageName]) { "virtual package is not registered: $packageName" }
        current[packageName] = pkg.copy(instances = pkg.instances + (instance.instanceId to instance))
        write(current.values.sortedBy { it.packageName })
    }

    @Synchronized fun deleteInstance(packageName: String, instanceId: String, cleanup: (VirtualInstanceRecord) -> Unit): VirtualInstanceRecord? {
        val current = readAll().associateBy { it.packageName }.toMutableMap()
        val pkg = current[packageName] ?: return null
        val instance = pkg.instances[instanceId] ?: return null
        current[packageName] = pkg.copy(instances = pkg.instances - instanceId)
        write(current.values.sortedBy { it.packageName })
        try {
            cleanup(instance)
        } catch (error: Throwable) {
            runCatching { write(readAll().associateBy { it.packageName }.toMutableMap().apply {
                this[packageName] = pkg
            }.values.sortedBy { it.packageName }) }.onFailure(error::addSuppressed)
            throw error
        }
        return instance
    }

    @Synchronized fun find(packageName: String): VirtualPackageRecord? = readAll().firstOrNull { it.packageName == packageName }
    @Synchronized fun packagesForUid(uid: Int): List<String> = readAll().filter { p -> p.instances.values.any { it.virtualUid == uid } }.map { it.packageName }

    @Synchronized fun readAll(): List<VirtualPackageRecord> {
        if (!file.baseFile.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            val packages = root.getJSONArray("packages")
            (0 until packages.length()).map { decode(packages.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun write(records: List<VirtualPackageRecord>) {
        val stream = file.startWrite()
        try {
            val json = JSONObject().put("schema", 1).put("packages", JSONArray(records.map(::encode)))
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun encode(record: VirtualPackageRecord) = JSONObject()
        .put("package", record.packageName).put("versionCode", record.versionCode).put("versionName", record.versionName)
        .put("source", record.sourceDir).put("splits", JSONArray(record.splitSourceDirs))
        .put("activities", JSONArray(record.activities)).put("services", JSONArray(record.services))
        .put("receivers", JSONArray(record.receivers)).put("providers", JSONArray(record.providers))
        .put("signing", record.signingSummary).put("permissions", JSONArray(record.requestedPermissions))
        .put("declaredPermissions", JSONArray(record.declaredPermissions))
        .put("instances", JSONArray(record.instances.values.map { i -> JSONObject().put("id", i.instanceId).put("uid", i.virtualUid).put("slot", i.processSlot).put("root", i.dataRoot) }))

    private fun decode(json: JSONObject): VirtualPackageRecord {
        fun strings(name: String) = json.getJSONArray(name).let { a -> (0 until a.length()).map(a::getString) }
        val instances = json.getJSONArray("instances").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.associate { item ->
                val value = VirtualInstanceRecord(item.getString("id"), item.getInt("uid"), item.getInt("slot"), item.getString("root"))
                value.instanceId to value
            }
        }
        return VirtualPackageRecord(json.getString("package"), json.getLong("versionCode"), json.optString("versionName").takeIf { it.isNotEmpty() },
            json.getString("source"), strings("splits"), strings("activities"), strings("services"), strings("receivers"),
            strings("providers"), json.getString("signing"), strings("permissions"), strings("declaredPermissions"), instances)
    }
}
