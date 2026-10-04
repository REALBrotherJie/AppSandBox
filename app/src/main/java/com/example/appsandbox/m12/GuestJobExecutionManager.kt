package com.example.appsandbox.m12

import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import com.example.appsandbox.runtime.VirtualProcessCoordinatorClient
import com.example.appsandbox.service.VirtualServiceRuntime
import java.lang.reflect.Proxy
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

    fun start(context: Context, hostParams: JobParameters, record: VirtualJobRecord): Boolean {
        val token = sequence.incrementAndGet()
        val component = ComponentName.unflattenFromString(record.guestService)
            ?: throw IllegalStateException("invalid guest service")
        val info = serviceInfo(context, component) ?: throw IllegalStateException("guest service missing")
        val route = runCatching { VirtualProcessCoordinatorClient.ensureService(context, record.key.instanceId, info) }
            .getOrElse { Log.e(TAG, "JOB_PROCESS_REQUEST token=$token", it); throw it }
        val stub = requireNotNull(route.stubComponent)
        val guestIntent = Intent(JobServiceIntent.ACTION).setComponent(component)
        val routed = VirtualServiceRuntime.routedProbeIntent(stub, guestIntent)
        val session = Session(token, record, hostParams, context.applicationContext)
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
        return session.start()
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
        val context: Context
    ) {
        val connected = CountDownLatch(1)
        private var binder: IBinder? = null
        private var guest: JobParameters? = null
        private var started = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service
                connected.countDown()
                Log.i(TAG, "JOB_GUEST_BIND instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
            }
            override fun onServiceDisconnected(name: ComponentName) {
                binder = null
                sessions.remove(host.jobId)
                Log.w(TAG, "JOB_EXECUTION_DESTROY hostJobId=${host.jobId} token=$token reason=guest-process-death")
                connected.countDown()
            }
        }

        fun start(): Boolean {
            val raw = requireNotNull(binder)
            val ifaceName = "android.app.job.IJobService"
            val iface = Class.forName(ifaceName)
            val api = Class.forName("$ifaceName\$Stub").getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, raw)
            guest = cloneParameters(host, record.key.guestJobId, callbackProxy(iface))
            started = true
            Log.i(TAG, "JOB_GUEST_START instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
            api.javaClass.getMethod("startJob", JobParameters::class.java).invoke(api, guest)
            return true
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

        private fun callbackProxy(iface: Class<*>): Any {
            val callbackField = findField(host.javaClass) { it.type.name.contains("IJobCallback") || it.name.contains("callback", true) }
            val callback = callbackField?.get(host) ?: return Proxy.newProxyInstance(iface.classLoader, emptyArray()) { _, _, _ -> null }
            val callbackType = callbackField.type
            return Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
                val translated = args?.copyOf() ?: emptyArray()
                if (method.name.contains("jobFinished", true)) {
                    val current = M12RuntimeRegistries.jobs.get(record.key)
                    if (current == null || current.generation != record.generation || !current.active) {
                        Log.w(TAG, "JOB_STALE_EXECUTION_REJECT hostJobId=${host.jobId} token=$token generation=${record.generation}")
                        return@newProxyInstance null
                    }
                    if (translated.isNotEmpty() && translated[0] is JobParameters) translated[0] = host
                    Log.i(TAG, "JOB_GUEST_FINISHED instance=${record.key.instanceId} guestJobId=${record.key.guestJobId} hostJobId=${host.jobId} token=$token")
                    sessions.remove(host.jobId)
                    runCatching { context.unbindService(connection) }
                } else if (translated.isNotEmpty() && translated[0] is Int) translated[0] = host.jobId
                method.invoke(callback, *translated)
            }
        }
    }

    private fun cloneParameters(source: JobParameters, guestId: Int, callback: Any): JobParameters {
        val parcel = Parcel.obtain()
        source.writeToParcel(parcel, 0); parcel.setDataPosition(0)
        val copy = JobParameters.CREATOR.createFromParcel(parcel); parcel.recycle()
        findField(copy.javaClass) { it.name in setOf("mJobId", "jobId") }?.let { it.set(copy, guestId) }
        findField(copy.javaClass) { it.type == Bundle::class.java && it.name.contains("extras", true) }?.let {
            val hostExtras = (it.get(source) as? Bundle)
            val extras = hostExtras?.getBundle("m12_guest_extras")?.let { Bundle(it) } ?: Bundle()
            it.set(copy, extras)
        }
        findField(copy.javaClass) { it.type.name.contains("IJobCallback") || it.name.contains("callback", true) }?.let { it.set(copy, callback) }
        return copy
    }

    private fun findField(type: Class<*>, predicate: (java.lang.reflect.Field) -> Boolean): java.lang.reflect.Field? =
        generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }
            .firstOrNull { runCatching { it.isAccessible = true; predicate(it) }.getOrDefault(false) }

    private object JobServiceIntent { const val ACTION = "android.app.job.JobService" }
}
