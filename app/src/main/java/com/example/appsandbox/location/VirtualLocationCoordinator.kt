package com.example.appsandbox.location

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

enum class VirtualLocationMode { REAL_PASSTHROUGH, FIXED, ROUTE, UNAVAILABLE }

data class VirtualLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null
) {
    fun location(provider: String): Location = Location(provider).also {
        it.latitude = latitude
        it.longitude = longitude
        it.accuracy = accuracy
        altitude?.let(it::setAltitude)
        speed?.let(it::setSpeed)
        bearing?.let(it::setBearing)
        it.time = System.currentTimeMillis()
        it.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun encode() = listOf(latitude, longitude, accuracy, altitude ?: "", speed ?: "", bearing ?: "").joinToString(",")

    companion object {
        fun decode(value: String): VirtualLocationPoint {
            val parts = value.split(',')
            require(parts.size >= 3)
            return VirtualLocationPoint(parts[0].toDouble(), parts[1].toDouble(), parts[2].toFloat(),
                parts.getOrNull(3)?.takeIf(String::isNotEmpty)?.toDouble(),
                parts.getOrNull(4)?.takeIf(String::isNotEmpty)?.toFloat(),
                parts.getOrNull(5)?.takeIf(String::isNotEmpty)?.toFloat())
        }
    }
}

data class VirtualLocationProfile(val generation: Long, val mode: VirtualLocationMode, val points: List<VirtualLocationPoint>, val providers: Set<String>)

class VirtualLocationCoordinator(private val context: Context) {
    private val preferences = context.getSharedPreferences("virtual_location_profiles", Context.MODE_PRIVATE)

    @Synchronized fun set(instanceId: String, mode: VirtualLocationMode, points: List<VirtualLocationPoint>, providers: Set<String>): VirtualLocationProfile {
        require(instanceId.isNotBlank())
        require(mode == VirtualLocationMode.REAL_PASSTHROUGH || mode == VirtualLocationMode.UNAVAILABLE || points.isNotEmpty())
        val generation = (get(instanceId)?.generation ?: 0L) + 1L
        val encoded = listOf(generation.toString(), mode.name, providers.joinToString(";"), points.joinToString("~") { it.encode() }).joinToString("#")
        check(preferences.edit().putString(instanceId, encoded).commit())
        return get(instanceId)!!
    }

    @Synchronized fun get(instanceId: String): VirtualLocationProfile? = preferences.getString(instanceId, null)?.let { encoded ->
        val parts = encoded.split('#', limit = 4)
        VirtualLocationProfile(parts[0].toLong(), VirtualLocationMode.valueOf(parts[1]),
            parts.getOrNull(3).orEmpty().split('~').filter(String::isNotBlank).map(VirtualLocationPoint::decode),
            parts.getOrNull(2).orEmpty().split(';').filter(String::isNotBlank).toSet())
    }

    @Synchronized fun delete(instanceId: String): Boolean = preferences.edit().remove(instanceId).commit()
}

class VirtualLocationCoordinatorProvider : ContentProvider() {
    override fun onCreate(): Boolean { coordinator = VirtualLocationCoordinator(requireNotNull(context)); return true }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        METHOD_GET -> Bundle().apply { coordinator.get(requireNotNull(arg))?.let { putProfile(it) } }
        METHOD_SET -> Bundle().apply {
            val profile = coordinator.set(requireNotNull(arg), VirtualLocationMode.valueOf(requireNotNull(extras).getString(KEY_MODE)!!),
                extras.getStringArrayList(KEY_POINTS).orEmpty().map(VirtualLocationPoint::decode),
                extras.getStringArrayList(KEY_PROVIDERS).orEmpty().toSet())
            putProfile(profile)
        }
        METHOD_DELETE -> Bundle().apply { putBoolean(KEY_OK, coordinator.delete(requireNotNull(arg))) }
        else -> error("unknown virtual location method $method")
    }
    private fun Bundle.putProfile(profile: VirtualLocationProfile) {
        putBoolean(KEY_OK, true); putLong(KEY_GENERATION, profile.generation); putString(KEY_MODE, profile.mode.name)
        putStringArrayList(KEY_POINTS, ArrayList(profile.points.map(VirtualLocationPoint::encode)))
        putStringArrayList(KEY_PROVIDERS, ArrayList(profile.providers))
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    companion object {
        const val AUTHORITY_SUFFIX = ".virtual-location"
        const val METHOD_GET = "get"; const val METHOD_SET = "set"; const val METHOD_DELETE = "delete"
        const val KEY_OK = "ok"; const val KEY_GENERATION = "generation"; const val KEY_MODE = "mode"
        const val KEY_POINTS = "points"; const val KEY_PROVIDERS = "providers"
        private lateinit var coordinator: VirtualLocationCoordinator
    }
}

object VirtualLocationCoordinatorClient {
    private fun authority(context: Context) = context.packageName + VirtualLocationCoordinatorProvider.AUTHORITY_SUFFIX
    fun get(context: Context, instanceId: String): VirtualLocationProfile? = context.contentResolver.call(authority(context), VirtualLocationCoordinatorProvider.METHOD_GET, instanceId, null)?.let(::decode)
    fun set(context: Context, instanceId: String, mode: VirtualLocationMode, points: List<VirtualLocationPoint>, providers: Set<String>): VirtualLocationProfile {
        val result = requireNotNull(context.contentResolver.call(authority(context), VirtualLocationCoordinatorProvider.METHOD_SET, instanceId, Bundle().apply {
            putString(VirtualLocationCoordinatorProvider.KEY_MODE, mode.name)
            putStringArrayList(VirtualLocationCoordinatorProvider.KEY_POINTS, ArrayList(points.map(VirtualLocationPoint::encode)))
            putStringArrayList(VirtualLocationCoordinatorProvider.KEY_PROVIDERS, ArrayList(providers))
        }))
        return requireNotNull(decode(result))
    }
    fun delete(context: Context, instanceId: String) = context.contentResolver.call(authority(context), VirtualLocationCoordinatorProvider.METHOD_DELETE, instanceId, null)?.getBoolean(VirtualLocationCoordinatorProvider.KEY_OK) == true
    private fun decode(bundle: Bundle): VirtualLocationProfile? {
        if (!bundle.getBoolean(VirtualLocationCoordinatorProvider.KEY_OK)) return null
        return VirtualLocationProfile(bundle.getLong(VirtualLocationCoordinatorProvider.KEY_GENERATION),
            VirtualLocationMode.valueOf(bundle.getString(VirtualLocationCoordinatorProvider.KEY_MODE)!!),
            bundle.getStringArrayList(VirtualLocationCoordinatorProvider.KEY_POINTS).orEmpty().map(VirtualLocationPoint::decode),
            bundle.getStringArrayList(VirtualLocationCoordinatorProvider.KEY_PROVIDERS).orEmpty().toSet())
    }
}
