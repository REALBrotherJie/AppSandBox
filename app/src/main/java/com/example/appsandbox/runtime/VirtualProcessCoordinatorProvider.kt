package com.example.appsandbox.runtime

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.content.pm.ActivityInfo
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VirtualProcessCoordinatorProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        coordinator = ProductionVirtualProcessCoordinator(requireNotNull(context).applicationContext)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        METHOD_ENSURE_SERVICE -> {
            val data = requireNotNull(extras)
            val info = requireNotNull(data.parcelable<ServiceInfo>(KEY_SERVICE_INFO))
            coordinator.ensureService(requireNotNull(data.getString(KEY_INSTANCE)), info).toBundle()
        }
        METHOD_ENSURE_RECEIVER -> {
            val data = requireNotNull(extras)
            val info = requireNotNull(data.parcelable<ActivityInfo>(KEY_RECEIVER_INFO))
            coordinator.ensureReceiver(requireNotNull(data.getString(KEY_INSTANCE)), info).toBundle()
        }
        METHOD_ENSURE_ACTIVITY -> {
            val data = requireNotNull(extras)
            val info = requireNotNull(data.parcelable<ActivityInfo>(KEY_RECEIVER_INFO))
            coordinator.ensureActivity(requireNotNull(data.getString(KEY_INSTANCE)), info).toBundle()
        }
        METHOD_ENSURE_PROVIDER -> {
            val data = requireNotNull(extras)
            val info = requireNotNull(data.parcelable<ProviderInfo>(KEY_PROVIDER_INFO))
            coordinator.ensureProvider(requireNotNull(data.getString(KEY_INSTANCE)), info).toBundle()
        }
        METHOD_REGISTER_ENTRY -> {
            val data = requireNotNull(extras)
            val key = VirtualProcessKey(data.getLong(KEY_REVISION), requireNotNull(data.getString(KEY_PACKAGE)),
                requireNotNull(data.getString(KEY_INSTANCE)), requireNotNull(data.getString(KEY_LOGICAL_PROCESS)))
            coordinator.registerEntry(key, data.getInt(KEY_SLOT), data.getInt(KEY_PID), requireNotNull(data.getBinder(KEY_TOKEN)))
            Bundle()
        }
        METHOD_TERMINATE_INSTANCE -> {
            val instanceId = requireNotNull(extras?.getString(KEY_INSTANCE))
            Bundle().apply {
                putStringArrayList(KEY_WEBVIEW_SUFFIXES, ArrayList(coordinator.terminateInstance(instanceId)))
            }
        }
        else -> error("unknown process coordinator method $method")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY_SUFFIX = ".process.coordinator"
        const val METHOD_ENSURE_SERVICE = "ensureService"
        const val METHOD_ENSURE_RECEIVER = "ensureReceiver"
        const val METHOD_ENSURE_PROVIDER = "ensureProvider"
        const val METHOD_ENSURE_ACTIVITY = "ensureActivity"
        const val METHOD_TERMINATE_INSTANCE = "terminateInstance"
        const val METHOD_REGISTER_ENTRY = "registerEntry"
        const val KEY_PACKAGE = "package"
        const val KEY_LOGICAL_PROCESS = "logicalProcess"
        const val KEY_REVISION = "revision"
        const val KEY_TOKEN = "token"
        const val KEY_STUB_COMPONENT = "stubComponent"
        const val KEY_INSTANCE = "instance"
        const val KEY_SERVICE_INFO = "serviceInfo"
        const val KEY_RECEIVER_INFO = "receiverInfo"
        const val KEY_PROVIDER_INFO = "providerInfo"
        const val KEY_PROVIDER_BINDER = "providerBinder"
        const val KEY_SLOT = "slot"
        const val KEY_PID = "pid"
        const val KEY_GENERATION = "generation"
        const val KEY_TRANSACTION = "transaction"
        const val KEY_WEBVIEW_SUFFIXES = "webViewSuffixes"
        private lateinit var coordinator: ProductionVirtualProcessCoordinator
    }
}

data class ProcessRoute(
    val slot: Int,
    val pid: Int,
    val generation: Long,
    val transactionId: String,
    val providerBinder: IBinder? = null,
    val stubComponent: ComponentName? = null
) {
    fun toBundle() = Bundle().apply {
        putInt(VirtualProcessCoordinatorProvider.KEY_SLOT, slot)
        putInt(VirtualProcessCoordinatorProvider.KEY_PID, pid)
        putLong(VirtualProcessCoordinatorProvider.KEY_GENERATION, generation)
        putString(VirtualProcessCoordinatorProvider.KEY_TRANSACTION, transactionId)
        providerBinder?.let { putBinder(VirtualProcessCoordinatorProvider.KEY_PROVIDER_BINDER, it) }
        stubComponent?.let { putParcelable(VirtualProcessCoordinatorProvider.KEY_STUB_COMPONENT, it) }
    }
}

object VirtualProcessCoordinatorClient {
    fun terminateInstance(context: Context, instanceId: String): List<String> {
        val authority = context.packageName + VirtualProcessCoordinatorProvider.AUTHORITY_SUFFIX
        val result = requireNotNull(context.contentResolver.call(Uri.parse("content://$authority"),
            VirtualProcessCoordinatorProvider.METHOD_TERMINATE_INSTANCE, null, Bundle().apply {
                putString(VirtualProcessCoordinatorProvider.KEY_INSTANCE, instanceId)
            }))
        return result.getStringArrayList(VirtualProcessCoordinatorProvider.KEY_WEBVIEW_SUFFIXES).orEmpty()
    }

    fun ensureService(context: Context, instanceId: String, info: ServiceInfo): ProcessRoute {
        val authority = context.packageName + VirtualProcessCoordinatorProvider.AUTHORITY_SUFFIX
        val result = requireNotNull(context.contentResolver.call(Uri.parse("content://$authority"),
            VirtualProcessCoordinatorProvider.METHOD_ENSURE_SERVICE, null, Bundle().apply {
                putString(VirtualProcessCoordinatorProvider.KEY_INSTANCE, instanceId)
                putParcelable(VirtualProcessCoordinatorProvider.KEY_SERVICE_INFO, ServiceInfo(info))
            }))
        return ProcessRoute(
            result.getInt(VirtualProcessCoordinatorProvider.KEY_SLOT),
            result.getInt(VirtualProcessCoordinatorProvider.KEY_PID),
            result.getLong(VirtualProcessCoordinatorProvider.KEY_GENERATION),
            requireNotNull(result.getString(VirtualProcessCoordinatorProvider.KEY_TRANSACTION)),
            stubComponent = result.parcelable(VirtualProcessCoordinatorProvider.KEY_STUB_COMPONENT)
        )
    }

    /**
     * Records a logical process that an Activity launch started in a pool slot, so later Service,
     * provider, receiver and Activity routes to it reuse this process instead of starting a second
     * copy of it. [token] lives as long as this process and tells the coordinator when it dies.
     */
    fun registerEntryProcess(context: Context, key: VirtualProcessKey, slot: Int, token: IBinder) {
        val authority = context.packageName + VirtualProcessCoordinatorProvider.AUTHORITY_SUFFIX
        context.contentResolver.call(Uri.parse("content://$authority"),
            VirtualProcessCoordinatorProvider.METHOD_REGISTER_ENTRY, null, Bundle().apply {
                putString(VirtualProcessCoordinatorProvider.KEY_INSTANCE, key.instanceId)
                putString(VirtualProcessCoordinatorProvider.KEY_PACKAGE, key.packageName)
                putString(VirtualProcessCoordinatorProvider.KEY_LOGICAL_PROCESS, key.logicalProcessName)
                putLong(VirtualProcessCoordinatorProvider.KEY_REVISION, key.packageRevision)
                putInt(VirtualProcessCoordinatorProvider.KEY_SLOT, slot)
                putInt(VirtualProcessCoordinatorProvider.KEY_PID, android.os.Process.myPid())
                putBinder(VirtualProcessCoordinatorProvider.KEY_TOKEN, token)
            })
    }

    fun ensureReceiver(context: Context, instanceId: String, info: ActivityInfo): ProcessRoute {
        val authority = context.packageName + VirtualProcessCoordinatorProvider.AUTHORITY_SUFFIX
        val result = requireNotNull(context.contentResolver.call(Uri.parse("content://$authority"),
            VirtualProcessCoordinatorProvider.METHOD_ENSURE_RECEIVER, null, Bundle().apply {
                putString(VirtualProcessCoordinatorProvider.KEY_INSTANCE, instanceId)
                putParcelable(VirtualProcessCoordinatorProvider.KEY_RECEIVER_INFO, ActivityInfo(info))
            }))
        return ProcessRoute(
            result.getInt(VirtualProcessCoordinatorProvider.KEY_SLOT),
            result.getInt(VirtualProcessCoordinatorProvider.KEY_PID),
            result.getLong(VirtualProcessCoordinatorProvider.KEY_GENERATION),
            requireNotNull(result.getString(VirtualProcessCoordinatorProvider.KEY_TRANSACTION))
        )
    }

    fun ensureActivity(context: Context, instanceId: String, info: ActivityInfo): ProcessRoute {
        val authority = context.packageName + VirtualProcessCoordinatorProvider.AUTHORITY_SUFFIX
        val result = requireNotNull(context.contentResolver.call(Uri.parse("content://$authority"),
            VirtualProcessCoordinatorProvider.METHOD_ENSURE_ACTIVITY, null, Bundle().apply {
                putString(VirtualProcessCoordinatorProvider.KEY_INSTANCE, instanceId)
                putParcelable(VirtualProcessCoordinatorProvider.KEY_RECEIVER_INFO, ActivityInfo(info))
            }))
        return ProcessRoute(
            result.getInt(VirtualProcessCoordinatorProvider.KEY_SLOT),
            result.getInt(VirtualProcessCoordinatorProvider.KEY_PID),
            result.getLong(VirtualProcessCoordinatorProvider.KEY_GENERATION),
            requireNotNull(result.getString(VirtualProcessCoordinatorProvider.KEY_TRANSACTION))
        )
    }

    fun ensureProvider(context: Context, instanceId: String, info: ProviderInfo): ProcessRoute {
        val authority = context.packageName + VirtualProcessCoordinatorProvider.AUTHORITY_SUFFIX
        val result = requireNotNull(context.contentResolver.call(Uri.parse("content://$authority"),
            VirtualProcessCoordinatorProvider.METHOD_ENSURE_PROVIDER, null, Bundle().apply {
                putString(VirtualProcessCoordinatorProvider.KEY_INSTANCE, instanceId)
                putParcelable(VirtualProcessCoordinatorProvider.KEY_PROVIDER_INFO, ProviderInfo(info))
            }))
        return ProcessRoute(result.getInt(VirtualProcessCoordinatorProvider.KEY_SLOT),
            result.getInt(VirtualProcessCoordinatorProvider.KEY_PID), result.getLong(VirtualProcessCoordinatorProvider.KEY_GENERATION),
            requireNotNull(result.getString(VirtualProcessCoordinatorProvider.KEY_TRANSACTION)),
            result.getBinder(VirtualProcessCoordinatorProvider.KEY_PROVIDER_BINDER))
    }
}

private class ProductionVirtualProcessCoordinator(private val context: Context) {
    private enum class State { ALLOCATING, STARTING_PHYSICAL_PROCESS, AGENT_CONNECTED, BINDING_GUEST, READY, DYING, DEAD }
    private data class Record(
        val key: VirtualProcessKey,
        val slot: Int,
        val generation: Long,
        val ready: CountDownLatch = CountDownLatch(1),
        val physicalDeath: CountDownLatch = CountDownLatch(1),
        var state: State = State.ALLOCATING,
        var pid: Int = -1,
        var agent: IBinder? = null,
        var connection: ServiceConnection? = null,
        var providerBinder: IBinder? = null,
        var stubComponent: ComponentName? = null,
        var error: Throwable? = null
    )

    private val handler = Handler(Looper.getMainLooper())
    private val records = linkedMapOf<VirtualProcessKey, Record>()
    private val slots = linkedMapOf<Int, VirtualProcessKey>()

    fun ensureService(instanceId: String, info: ServiceInfo): ProcessRoute {
        val packageName = info.packageName
        val key = VirtualProcessKey(
            File(info.applicationInfo.sourceDir).lastModified(), packageName, instanceId,
            VirtualProcessKey.canonicalProcessName(packageName, info.processName)
        )
        val (route, fresh) = ensureRecord(key) { record -> startAgent(record, M10ArchProtocol.COMPONENT_SERVICE, info, null) }
        val record = route.first
        // A process started for this Service already allocated its stub; any other Service of a running
        // process gets its own stub from that process.
        val stub = if (fresh) record.stubComponent else request(record, M10ArchProtocol.COMPONENT_SERVICE) {
            putParcelable(M10ArchProtocol.KEY_SERVICE_INFO, ServiceInfo(info))
        }.parcelable<ComponentName>(M10ArchProtocol.KEY_STUB_COMPONENT)
        return ProcessRoute(record.slot, record.pid, record.generation, route.second, stubComponent = requireNotNull(stub) {
            "no stub Service for ${info.name} in $key"
        })
    }

    /** Registers a logical process started by an Activity launch in a pool slot (see the client). */
    fun registerEntry(key: VirtualProcessKey, slot: Int, pid: Int, token: IBinder) {
        val record = synchronized(this) {
            val existing = records[key]?.takeIf { it.state != State.DEAD && it.state != State.DYING }
            if (existing != null) {
                Log.i(TAG, "VPROCESS_ENTRY key=$key slot=$slot pid=$pid existingSlot=${existing.slot} existingPid=${existing.pid} result=EXISTING")
                return
            }
            slots[slot]?.let { owner ->
                Log.w(TAG, "VPROCESS_ENTRY key=$key slot=$slot pid=$pid owner=$owner result=SLOT_CONFLICT")
                return
            }
            val prefs = context.getSharedPreferences("virtual_process_generations", Context.MODE_PRIVATE)
            val generation = prefs.getLong("next", 0L) + 1
            check(prefs.edit().putLong("next", generation).commit())
            Record(key, slot, generation, state = State.READY, pid = pid, agent = token).also {
                records[key] = it
                slots[slot] = key
                it.ready.countDown()
            }
        }
        token.linkToDeath({
            Log.i(TAG, "VPROCESS_TEARDOWN event=ENTRY_DEATH key=$key generation=${record.generation}")
            handler.post { markDead(record) }
        }, 0)
        Log.i(TAG, "VPROCESS_ENTRY key=$key slot=$slot pid=$pid generation=${record.generation} result=REGISTERED")
    }

    fun ensureReceiver(instanceId: String, info: ActivityInfo): ProcessRoute {
        val key = VirtualProcessKey(
            File(info.applicationInfo.sourceDir).lastModified(), info.packageName, instanceId,
            VirtualProcessKey.canonicalProcessName(info.packageName, info.processName)
        )
        return ensure(key) { record -> startAgent(record, M10ArchProtocol.COMPONENT_RECEIVER, null, info) }
    }

    fun ensureActivity(instanceId: String, info: ActivityInfo): ProcessRoute {
        val key = VirtualProcessKey(File(info.applicationInfo.sourceDir).lastModified(), info.packageName, instanceId,
            VirtualProcessKey.canonicalProcessName(info.packageName, info.processName))
        return ensure(key) { record -> startAgent(record, M10ArchProtocol.COMPONENT_ACTIVITY, null, info) }
    }

    fun ensureProvider(instanceId: String, info: ProviderInfo): ProcessRoute {
        val key = VirtualProcessKey(File(info.applicationInfo.sourceDir).lastModified(), info.packageName, instanceId,
            VirtualProcessKey.canonicalProcessName(info.packageName, info.processName))
        val (route, fresh) = ensureRecord(key) { record -> startAgent(record, M10ArchProtocol.COMPONENT_PROVIDER, null, null, info) }
        val record = route.first
        // The binder from process start belongs to the authority that started it; every other authority
        // is published by the running process on request.
        val binder = if (fresh) record.providerBinder else request(record, M10ArchProtocol.COMPONENT_PROVIDER) {
            putParcelable(M10ArchProtocol.KEY_PROVIDER_INFO, ProviderInfo(info))
        }.getBinder(M10ArchProtocol.KEY_PROVIDER_BINDER)
        return ProcessRoute(record.slot, record.pid, record.generation, route.second, providerBinder = requireNotNull(binder) {
            "no provider binder for ${info.authority} in $key"
        })
    }

    fun terminateInstance(instanceId: String): List<String> {
        val owned = synchronized(this) {
            records.values.filter { it.key.instanceId == instanceId && it.state != State.DEAD }.onEach {
                if (it.state != State.DYING) it.state = State.DYING
            }
        }
        val suffixes = owned.map { VirtualWebViewProcessPolicy.stableSuffix(it.key) }.distinct()
        Log.i(TAG, "VPROCESS_TEARDOWN event=REQUEST thread=${Thread.currentThread().name} instance=$instanceId records=${owned.size}")
        owned.forEach { record ->
            record.agent?.let { binder ->
                runCatching { Messenger(binder).send(Message.obtain(null, M10ArchProtocol.MSG_DIE)) }
                    .onFailure { record.physicalDeath.countDown() }
            } ?: record.physicalDeath.countDown()
        }
        owned.forEach { record ->
            check(record.physicalDeath.await(5, TimeUnit.SECONDS)) {
                "remote physical death timeout instance=$instanceId generation=${record.generation}"
            }
            markDead(record)
        }
        Log.i(TAG, "VPROCESS_INSTANCE_TERMINATED instance=$instanceId records=${owned.size} suffixes=$suffixes")
        return suffixes
    }

    private fun ensure(key: VirtualProcessKey, starter: (Record) -> Unit): ProcessRoute {
        val (route, _) = ensureRecord(key, starter)
        val record = route.first
        return ProcessRoute(record.slot, record.pid, record.generation, route.second)
    }

    /** The READY record for [key] with its transaction id, and whether this call started the process. */
    private fun ensureRecord(key: VirtualProcessKey, starter: (Record) -> Unit): Pair<Pair<Record, String>, Boolean> {
        val transaction = UUID.randomUUID().toString()
        var fresh = false
        val record = synchronized(this) {
            records[key]?.takeIf { it.state != State.DEAD && it.state != State.DYING } ?: allocate(key).also {
                records[key] = it
                slots[it.slot] = key
                fresh = true
                handler.post { starter(it) }
            }
        }
        Log.i(TAG, "VPROCESS_TX id=$transaction key=$key generation=${record.generation} state=QUEUED slot=${record.slot} fresh=$fresh")
        check(record.ready.await(15, TimeUnit.SECONDS)) { "process READY timeout key=$key slot=${record.slot}" }
        record.error?.let { throw IllegalStateException("process bootstrap failed key=$key", it) }
        check(record.state == State.READY) { "process not READY key=$key state=${record.state}" }
        Log.i(TAG, "VPROCESS_TX id=$transaction key=$key generation=${record.generation} state=DISPATCHED slot=${record.slot} pid=${record.pid}")
        return (record to transaction) to fresh
    }

    /** Asks the agent of a READY process to prepare one more component; runs on a Binder thread. */
    private fun request(record: Record, componentKind: String, fill: Bundle.() -> Unit): Bundle {
        val agent = agentBinder(record)
        val latch = CountDownLatch(1)
        var result: Bundle? = null
        Messenger(agent).send(Message.obtain(null, M10ArchProtocol.MSG_PREPARE_COMPONENT).apply {
            data = Bundle().apply {
                putString(M10ArchProtocol.KEY_PACKAGE, record.key.packageName)
                putString(M10ArchProtocol.KEY_INSTANCE, record.key.instanceId)
                putInt(M10ArchProtocol.KEY_SLOT, record.slot)
                putString(M10ArchProtocol.KEY_COMPONENT_KIND, componentKind)
                fill()
            }
            replyTo = Messenger(Handler(replyThread.looper) { reply ->
                result = reply.data
                latch.countDown()
                true
            })
        })
        check(latch.await(15, TimeUnit.SECONDS)) { "agent prepare timeout key=${record.key} kind=$componentKind" }
        val data = requireNotNull(result)
        data.getString(M10ArchProtocol.KEY_ERROR)?.let { throw IllegalStateException("agent prepare failed key=${record.key}: $it") }
        return data
    }

    /** The agent of [record]'s slot; entry processes are bound on first use. */
    private fun agentBinder(record: Record): IBinder {
        record.agent?.takeIf { it.isBinderAlive }?.let { return it }
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                record.agent = binder
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                record.agent = null
            }
        }
        synchronized(this) {
            record.connection?.let { runCatching { context.unbindService(it) } }
            record.connection = connection
        }
        check(context.bindService(Intent(context, agentClass(record.slot)), connection, Context.BIND_AUTO_CREATE)) {
            "agent bind returned false slot=${record.slot}"
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "agent connect timeout slot=${record.slot}" }
        return requireNotNull(record.agent) { "agent disconnected slot=${record.slot}" }
    }

    private val replyThread = android.os.HandlerThread("VirtualProcessReplies").apply { start() }

    @Synchronized
    private fun allocate(key: VirtualProcessKey): Record {
        val slot = (2..8).firstOrNull { it !in slots } ?: error("virtual process pool exhausted: no free slot for $key")
        val prefs = context.getSharedPreferences("virtual_process_generations", Context.MODE_PRIVATE)
        val generation = prefs.getLong("next", 0L) + 1
        check(prefs.edit().putLong("next", generation).commit())
        return Record(key, slot, generation, state = State.STARTING_PHYSICAL_PROCESS)
    }

    private fun startAgent(record: Record, componentKind: String, serviceInfo: ServiceInfo?, receiverInfo: ActivityInfo?, providerInfo: ProviderInfo? = null) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                record.state = State.AGENT_CONNECTED
                record.agent = binder
                binder.linkToDeath({
                    Log.i(TAG, "VPROCESS_TEARDOWN event=PHYSICAL_DEATH thread=${Thread.currentThread().name} key=${record.key} generation=${record.generation}")
                    record.physicalDeath.countDown()
                    handler.post { markDead(record) }
                }, 0)
                record.state = State.BINDING_GUEST
                Messenger(binder).send(Message.obtain(null, M10ArchProtocol.MSG_BIND_PROCESS).apply {
                    data = Bundle().apply {
                        putString(M10ArchProtocol.KEY_PACKAGE, record.key.packageName)
                        putString(M10ArchProtocol.KEY_INSTANCE, record.key.instanceId)
                        putString(M10ArchProtocol.KEY_LOGICAL_PROCESS, record.key.logicalProcessName)
                        putLong(M10ArchProtocol.KEY_REVISION, record.key.packageRevision)
                        putLong(M10ArchProtocol.KEY_GENERATION, record.generation)
                        putInt(M10ArchProtocol.KEY_SLOT, record.slot)
                        putString(M10ArchProtocol.KEY_COMPONENT_KIND, componentKind)
                        serviceInfo?.let { putParcelable(M10ArchProtocol.KEY_SERVICE_INFO, ServiceInfo(it)) }
                        receiverInfo?.let { putParcelable(M10ArchProtocol.KEY_RECEIVER_INFO, ActivityInfo(it)) }
                        providerInfo?.let { putParcelable(M10ArchProtocol.KEY_PROVIDER_INFO, ProviderInfo(it)) }
                    }
                    replyTo = Messenger(Handler(Looper.getMainLooper()) { reply ->
                        if (reply.data.getBoolean(M10ArchProtocol.KEY_READY) &&
                            reply.data.getLong(M10ArchProtocol.KEY_GENERATION) == record.generation) {
                            record.pid = reply.data.getInt(M10ArchProtocol.KEY_PID)
                            record.providerBinder = reply.data.getBinder(M10ArchProtocol.KEY_PROVIDER_BINDER)
                            record.stubComponent = reply.data.parcelable(M10ArchProtocol.KEY_STUB_COMPONENT)
                            record.state = State.READY
                            Log.i(TAG, "VPROCESS_READY key=${record.key} slot=${record.slot} pid=${record.pid} generation=${record.generation}")
                        } else record.error = IllegalStateException("invalid agent READY")
                        record.ready.countDown()
                        true
                    })
                })
            }
            override fun onServiceDisconnected(name: ComponentName) {
                record.physicalDeath.countDown()
                markDead(record)
            }
        }
        record.connection = connection
        val bound = context.bindService(Intent(context, agentClass(record.slot)), connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            record.error = IllegalStateException("agent bind returned false slot=${record.slot}")
            record.ready.countDown()
        }
    }

    @Synchronized
    private fun markDead(record: Record) {
        if (record.state == State.DEAD) return
        record.physicalDeath.countDown()
        record.state = State.DEAD
        record.agent = null
        record.connection?.let { runCatching { context.unbindService(it) } }
        record.connection = null
        slots.remove(record.slot, record.key)
        records.remove(record.key, record)
        record.ready.countDown()
        Log.i(TAG, "VPROCESS_DEAD thread=${Thread.currentThread().name} key=${record.key} slot=${record.slot} generation=${record.generation}")
    }

    private fun agentClass(slot: Int): Class<out android.app.Service> = arrayOf(
        P0ProcessAgent::class.java, P1ProcessAgent::class.java, P2ProcessAgent::class.java,
        P3ProcessAgent::class.java, P4ProcessAgent::class.java, P5ProcessAgent::class.java,
        P6ProcessAgent::class.java, P7ProcessAgent::class.java, P8ProcessAgent::class.java
    )[slot]

    companion object { private const val TAG = "AppSandbox.M10" }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Bundle.parcelable(key: String): T? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java) else getParcelable(key)
