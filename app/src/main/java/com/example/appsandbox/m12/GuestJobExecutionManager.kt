package com.example.appsandbox.m12

import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import com.example.appsandbox.runtime.VirtualProcessCoordinatorClient
import com.example.appsandbox.service.VirtualServiceRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Bridges a physical JobService execution to the real Guest JobServiceEngine binder. */
object GuestJobExecutionManager {
    private const val TAG = "AppSandbox.M12.Job"
    private const val KEY_GUEST_EXTRAS = "m12_guest_extras"
    private const val KEY_GUEST_ID = "m12_guest_job_id"
    private val sequence = AtomicLong()
    private val sessions = ConcurrentHashMap<Int, Session>()

    fun start(context: Context, hostParams: JobParameters, record: VirtualJobRecord, finishHost: (Boolean) -> Unit) {
        val token = sequence.incrementAndGet()
        Thread({
            runCatching { startBlocking(context, hostParams, record, token, finishHost) }
                .onFailure {
                    Log.e(TAG, "JOB_EXECUTION_FAILED hostJobId=${hostParams.jobId} token=$token", it)
                    sessions.remove(hostParams.jobId)?.let { runCatching { context.unbindService(it.connection) } }
                    Handler(Looper.getMainLooper()).post { finishHost(false) }
                }
        }, "virtual-job-$token").start()
    }

    private fun startBlocking(context: Context, hostParams: JobParameters, record: VirtualJobRecord, token: Long,
                              finishHost: (Boolean) -> Unit) {
        val component = ComponentName.unflattenFromString(record.guestService)
            ?: throw IllegalStateException("invalid guest service")
        val info = serviceInfo(context, component) ?: throw IllegalStateException("guest service missing")
        val route = runCatching { VirtualProcessCoordinatorClient.ensureService(context, record.key.instanceId, info) }
            .getOrElse { Log.e(TAG, "JOB_PROCESS_REQUEST token=$token", it); throw it }
        val stub = requireNotNull(route.stubComponent)
        val guestIntent = Intent(JobServiceIntent.ACTION).setComponent(component)
        val routed = VirtualServiceRuntime.routedProbeIntent(stub, guestIntent)
        val session = Session(token, record, hostParams, context.applicationContext, route.generation, finishHost)
        sessions[hostParams.jobId] = session
        Log.i(TAG, "JOB_PROCESS_READY instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${hostParams.jobId} generation=${record.generation} token=$token")
        val connected = context.bindService(routed, session.connection, Context.BIND_AUTO_CREATE)
        if (!connected) {
            sessions.remove(hostParams.jobId, session)
            throw IllegalStateException("guest bind returned false")
        }
        if (!session.connected.await(15, TimeUnit.SECONDS)) {
            sessions.remove(hostParams.jobId, session)
            runCatching { context.unbindService(session.connection) }
            throw IllegalStateException("guest bind timeout")
        }
        session.start()
    }

    fun stop(context: Context, hostParams: JobParameters): Boolean {
        val session = sessions[hostParams.jobId] ?: return false
        session.stop()
        Log.i(TAG, "JOB_HOST_STOP hostJobId=${hostParams.jobId} token=${session.token}")
        return false
    }

    private fun serviceInfo(context: Context, component: ComponentName): ServiceInfo? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            context.packageManager.getServiceInfo(component, android.content.pm.PackageManager.ComponentInfoFlags.of(android.content.pm.PackageManager.GET_META_DATA.toLong()))
        else @Suppress("DEPRECATION") context.packageManager.getServiceInfo(component, android.content.pm.PackageManager.GET_META_DATA)
    }.getOrNull()

    private class Session(
        val token: Long,
        val record: VirtualJobRecord,
        val host: JobParameters,
        val context: Context,
        val processGeneration: Long,
        val finishHost: (Boolean) -> Unit
    ) {
        val connected = CountDownLatch(1)
        private var binder: IBinder? = null
        private var guest: JobParameters? = null
        private var started = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service
                runCatching { service.linkToDeath({ terminateForProcessDeath() }, 0) }
                connected.countDown()
                Log.i(TAG, "JOB_GUEST_BIND instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
            }
            override fun onServiceDisconnected(name: ComponentName) {
                terminateForProcessDeath()
                connected.countDown()
            }
            override fun onBindingDied(name: ComponentName) = terminateForProcessDeath()
        }

        private fun terminateForProcessDeath() {
            binder = null
            if (!sessions.remove(host.jobId, this)) return
            Log.w(TAG, "JOB_EXECUTION_DESTROY hostJobId=${host.jobId} token=$token reason=guest-process-death")
            Handler(Looper.getMainLooper()).post { finishHost(false) }
        }

        fun start() {
            val raw = requireNotNull(binder)
            val ifaceName = "android.app.job.IJobService"
            val api = Class.forName("$ifaceName\$Stub").getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, raw)
            guest = cloneParameters(host, record.key.guestJobId, callbackBridge())
            started = true
            Log.i(TAG, "JOB_GUEST_START instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
            api.javaClass.getMethod("startJob", JobParameters::class.java).invoke(api, guest)
        }

        fun stop() {
            val raw = binder ?: return
            val guestParams = guest ?: return
            runCatching {
                val ifaceName = "android.app.job.IJobService"
                val api = Class.forName("$ifaceName\$Stub").getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, raw)
                api.javaClass.getMethod("stopJob", JobParameters::class.java).invoke(api, guestParams)
                Log.i(TAG, "JOB_GUEST_STOP instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
            }.onFailure { Log.e(TAG, "JOB_GUEST_STOP_FAILED hostJobId=${host.jobId}", it) }
        }

        private fun callbackBridge(): Any {
            val callbackField = findField(host.javaClass) { it.type.name.contains("IJobCallback") || it.name.contains("callback", true) }
            val callback = requireNotNull(callbackField?.get(host)) { "Host Job callback missing" }
            val original = callback as? IBinder
                ?: callback.javaClass.getMethod("asBinder").invoke(callback) as IBinder
            // IJobCallback.aidl has kept these transaction positions stable since API 21.
            // Some production framework jars strip the generated TRANSACTION_* fields.
            val ackStart = IBinder.FIRST_CALL_TRANSACTION
            val finished = IBinder.FIRST_CALL_TRANSACTION + 4
            val facade = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    data.enforceInterface("android.app.job.IJobCallback")
                    data.readInt() // Guest-visible logical job id.
                    if (code == ackStart) {
                        val ongoing = data.readInt() != 0
                        Log.i(TAG, "JOB_GUEST_START_ACK hostJobId=${host.jobId} guestJobId=${record.key.guestJobId} ongoing=$ongoing token=$token generation=$processGeneration")
                        if (!ongoing && sessions.remove(host.jobId, this@Session))
                            Handler(Looper.getMainLooper()).post { finishHost(false) }
                        return true
                    }
                    if (code == finished) {
                        val reschedule = data.readInt() != 0
                        if (sessions[host.jobId] !== this@Session) {
                            Log.w(TAG, "JOB_STALE_EXECUTION_REJECT hostJobId=${host.jobId} token=$token generation=$processGeneration")
                            return true
                        }
                        Log.i(TAG, "JOB_GUEST_FINISHED instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
                        sessions.remove(host.jobId)
                        runCatching { context.unbindService(connection) }
                        Handler(Looper.getMainLooper()).post { finishHost(reschedule) }
                        return true
                    }
                    val outgoing = Parcel.obtain()
                    return try {
                        outgoing.writeInterfaceToken("android.app.job.IJobCallback")
                        outgoing.writeInt(host.jobId)
                        if (data.dataAvail() > 0) outgoing.appendFrom(data, data.dataPosition(), data.dataAvail())
                        original.transact(code, outgoing, reply, flags)
                    } finally { outgoing.recycle() }
                }
            }
            facade.attachInterface(null, "android.app.job.IJobCallback")
            return facade
        }
    }

    private fun cloneParameters(source: JobParameters, guestId: Int, callback: Any): JobParameters {
        val encoded = Parcel.obtain()
        val rewritten = Parcel.obtain()
        return try {
            source.writeToParcel(encoded, 0)
            encoded.setDataPosition(0)
            val callbackFirst = android.os.Build.VERSION.SDK_INT >= 35
            if (callbackFirst) encoded.readStrongBinder()
            encoded.readInt()
            encoded.readPersistableBundle(javaClass.classLoader)
            val tailOffset = encoded.dataPosition()
            val guestExtras = source.extras.getPersistableBundle("m12_guest_extras")?.let(::PersistableBundle)
                ?: PersistableBundle()
            Log.i(TAG, "JOB_PARAMETER_BRIDGE hostJobId=${source.jobId} guestJobId=$guestId guestExtras=${guestExtras.keySet().sorted()}")
            if (callbackFirst) rewritten.writeStrongBinder(callback as IBinder)
            rewritten.writeInt(guestId)
            rewritten.writePersistableBundle(guestExtras)
            rewritten.appendFrom(encoded, tailOffset, encoded.dataSize() - tailOffset)
            rewritten.setDataPosition(0)
            JobParameters.CREATOR.createFromParcel(rewritten).also { copy ->
                if (!callbackFirst) findField(copy.javaClass) { it.name in setOf("mCallback", "callback") }?.set(copy, callback)
            }
        } finally {
            encoded.recycle()
            rewritten.recycle()
        }
    }

    private fun findField(type: Class<*>, predicate: (java.lang.reflect.Field) -> Boolean): java.lang.reflect.Field? =
        generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }
            .firstOrNull { runCatching { it.isAccessible = true; predicate(it) }.getOrDefault(false) }

    private object JobServiceIntent { const val ACTION = "android.app.job.JobService" }
}
