package com.example.appsandbox.receiver

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.stub.StubReceivers
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Process-independent control plane for restoring framework-owned receiver deliveries. */
class BroadcastSessionProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        METHOD_CREATE -> create(requireNotNull(extras))
        METHOD_CONSUME -> consume(requireNotNull(extras))
        METHOD_REMOVE_INSTANCE -> removeInstance(requireNotNull(extras))
        else -> error("unknown receiver session method $method")
    }

    private fun create(data: Bundle): Bundle {
        prune()
        val count = data.getInt(KEY_COUNT)
        require(count in 1..StubReceivers.MAX_RANKS) { "unsupported receiver count $count" }
        val slot = data.getInt(KEY_SLOT)
        val infos = (0 until count).map { requireNotNull(data.parcelable<ActivityInfo>("$KEY_INFO$it")) }
        val id = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val session = Session(
            id = id,
            packageName = requireNotNull(data.getString(KEY_PACKAGE)),
            instanceId = requireNotNull(data.getString(KEY_INSTANCE)),
            virtualUid = requireNotNull(data.getString(KEY_VIRTUAL_UID)),
            slot = slot,
            originalIntent = requireNotNull(data.parcelable<Intent>(KEY_INTENT)),
            ordered = data.getBoolean(KEY_ORDERED),
            infos = infos,
            createdAt = SystemClock.elapsedRealtime()
        )
        sessions[id] = session
        Log.i(TAG, "VRECEIVER_SESSION event=CREATE id=$id package=${session.packageName} instance=${session.instanceId} slot=$slot ordered=${session.ordered} receivers=${infos.map { it.name }}")
        return Bundle().apply { putString(KEY_SESSION, id) }
    }

    private fun consume(data: Bundle): Bundle {
        prune()
        val id = requireNotNull(data.getString(KEY_SESSION))
        val stub = requireNotNull(data.getString(KEY_STUB))
        val session = sessions[id] ?: return Bundle().apply { putBoolean(KEY_OK, false) }
        val index = session.infos.indices.firstOrNull {
            StubReceivers.component(session.slot, session.infos.size, it).className == stub
        } ?: return Bundle().apply { putBoolean(KEY_OK, false) }
        synchronized(session) {
            if (!session.consumed.add(index)) return Bundle().apply { putBoolean(KEY_OK, false) }
        }
        Log.i(TAG, "VRECEIVER_SESSION event=CONSUME id=$id index=$index stub=$stub guest=${session.infos[index].name} consumed=${session.consumed.size}/${session.infos.size}")
        return Bundle().apply {
            putBoolean(KEY_OK, true)
            putString(KEY_SESSION, session.id)
            putString(KEY_PACKAGE, session.packageName)
            putString(KEY_INSTANCE, session.instanceId)
            putString(KEY_VIRTUAL_UID, session.virtualUid)
            putInt(KEY_SLOT, session.slot)
            putBoolean(KEY_ORDERED, session.ordered)
            putInt(KEY_INDEX, index)
            putParcelable(KEY_INTENT, Intent(session.originalIntent))
            putParcelable(KEY_INFO, ActivityInfo(session.infos[index]))
        }
    }

    private fun removeInstance(data: Bundle): Bundle {
        val instance = requireNotNull(data.getString(KEY_INSTANCE))
        val ids = sessions.values.filter { it.instanceId == instance }.map { it.id }
        ids.forEach(sessions::remove)
        return Bundle().apply { putInt(KEY_COUNT, ids.size) }
    }

    private fun prune() {
        val cutoff = SystemClock.elapsedRealtime() - SESSION_TTL_MS
        sessions.entries.removeIf { it.value.createdAt < cutoff }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private data class Session(
        val id: String,
        val packageName: String,
        val instanceId: String,
        val virtualUid: String,
        val slot: Int,
        val originalIntent: Intent,
        val ordered: Boolean,
        val infos: List<ActivityInfo>,
        val createdAt: Long,
        val consumed: MutableSet<Int> = mutableSetOf()
    )

    companion object {
        private const val TAG = "AppSandbox.M8"
        private const val SESSION_TTL_MS = 120_000L
        const val AUTHORITY_SUFFIX = ".receiver.sessions"
        const val EXTRA_SESSION_ID = "com.example.appsandbox.receiver.SESSION_ID"
        const val METHOD_CREATE = "create"
        const val METHOD_CONSUME = "consume"
        const val METHOD_REMOVE_INSTANCE = "removeInstance"
        const val KEY_OK = "ok"
        const val KEY_SESSION = "session"
        const val KEY_PACKAGE = "package"
        const val KEY_INSTANCE = "instance"
        const val KEY_VIRTUAL_UID = "virtualUid"
        const val KEY_SLOT = "slot"
        const val KEY_ORDERED = "ordered"
        const val KEY_COUNT = "count"
        const val KEY_INDEX = "index"
        const val KEY_INTENT = "intent"
        const val KEY_INFO = "info"
        const val KEY_STUB = "stub"
        private val random = SecureRandom()
        private val sessions = ConcurrentHashMap<String, Session>()

        @Suppress("DEPRECATION")
        private inline fun <reified T : android.os.Parcelable> Bundle.parcelable(key: String): T? =
            if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java) else getParcelable(key)
    }
}

data class ReceiverSessionDelivery(
    val sessionId: String,
    val packageName: String,
    val instanceId: String,
    val virtualUid: String,
    val slot: Int,
    val ordered: Boolean,
    val index: Int,
    val intent: Intent,
    val info: ActivityInfo
)

object BroadcastSessionClient {
    private fun uri(context: Context) = Uri.parse("content://${context.packageName}${BroadcastSessionProvider.AUTHORITY_SUFFIX}")

    fun create(context: Context, identity: RuntimeIdentity, intent: Intent, infos: List<ActivityInfo>, ordered: Boolean): String {
        val request = Bundle().apply {
            putString(BroadcastSessionProvider.KEY_PACKAGE, identity.guestPackageName)
            putString(BroadcastSessionProvider.KEY_INSTANCE, identity.instanceId)
            putString(BroadcastSessionProvider.KEY_VIRTUAL_UID, identity.virtualUid)
            putInt(BroadcastSessionProvider.KEY_SLOT, identity.processSlot)
            putBoolean(BroadcastSessionProvider.KEY_ORDERED, ordered)
            putInt(BroadcastSessionProvider.KEY_COUNT, infos.size)
            putParcelable(BroadcastSessionProvider.KEY_INTENT, Intent(intent))
            infos.forEachIndexed { index, info -> putParcelable("${BroadcastSessionProvider.KEY_INFO}$index", ActivityInfo(info)) }
        }
        return requireNotNull(context.contentResolver.call(uri(context), BroadcastSessionProvider.METHOD_CREATE, null, request))
            .getString(BroadcastSessionProvider.KEY_SESSION) ?: error("receiver session owner returned no id")
    }

    fun consume(context: Context, sessionId: String, stubClass: String): ReceiverSessionDelivery? {
        val result = context.contentResolver.call(uri(context), BroadcastSessionProvider.METHOD_CONSUME, null, Bundle().apply {
            putString(BroadcastSessionProvider.KEY_SESSION, sessionId)
            putString(BroadcastSessionProvider.KEY_STUB, stubClass)
        }) ?: return null
        if (!result.getBoolean(BroadcastSessionProvider.KEY_OK)) return null
        return ReceiverSessionDelivery(
            sessionId,
            requireNotNull(result.getString(BroadcastSessionProvider.KEY_PACKAGE)),
            requireNotNull(result.getString(BroadcastSessionProvider.KEY_INSTANCE)),
            requireNotNull(result.getString(BroadcastSessionProvider.KEY_VIRTUAL_UID)),
            result.getInt(BroadcastSessionProvider.KEY_SLOT),
            result.getBoolean(BroadcastSessionProvider.KEY_ORDERED),
            result.getInt(BroadcastSessionProvider.KEY_INDEX),
            requireNotNull(result.parcelable(BroadcastSessionProvider.KEY_INTENT)),
            requireNotNull(result.parcelable(BroadcastSessionProvider.KEY_INFO))
        )
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Bundle.parcelable(key: String): T? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java) else getParcelable(key)
}
