package com.example.appsandbox.binder

import android.app.job.JobInfo
import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.m12.*
import com.example.appsandbox.location.GuestProcessLocationBindings
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Guest-aware facade for the framework JobScheduler client. */
class JobSchedulerAdapter(private val identity: RuntimeIdentity) : BinderServiceAdapter {
    override val serviceName = "jobscheduler"
    override val interfaceName = "android.app.job.IJobScheduler"

    override fun install(): AdapterInstallResult = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        val cache = sm.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<String, IBinder>
        val raw = sm.getDeclaredMethod("getService", String::class.java).invoke(null, serviceName) as? IBinder
            ?: error("jobscheduler unavailable")
        val stub = Class.forName("$interfaceName\$Stub")
        val original = stub.getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, raw)
        val iface = Class.forName(interfaceName)
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { p, method, args0 ->
            if (method.name == "equals") return@newProxyInstance p === args0?.firstOrNull()
            if (method.name == "hashCode") return@newProxyInstance System.identityHashCode(p)
            val args = args0?.copyOf() ?: emptyArray()
            when (method.name) {
                "schedule", "enqueue" -> {
                    val index = args.indexOfFirst { it is JobInfo }
                    if (index >= 0) {
                        val guest = args[index] as JobInfo
                        val component = guest.service
                        val guestId = guest.id
                        val key = VirtualJobKey(identity.virtualUid, identity.guestPackageName, identity.instanceId, guestId)
                        val hostId = M12RuntimeRegistries.jobs.hostJobId(key)
                        val stubComponent = ComponentName(identity.hostPackageName, "${identity.hostPackageName}.m12.JobStubService")
                        val physical = copyJob(guest, hostId, stubComponent, key, component)
                        args[index] = physical
                        val record = VirtualJobRecord(key, hostId, component?.flattenToString().orEmpty(),
                            GuestProcessLocationBindings.current()?.generation ?: 0L,
                            stubComponent.flattenToString())
                        M12RuntimeRegistries.jobs.put(record)
                        Log.i("AppSandbox.M12.Job", "GUEST_JOB_REQUEST instance=${identity.instanceId} guestJobId=$guestId " +
                            "HOST_JOB_ID=$hostId guest=${identity.guestPackageName} event=HOST_JOB_SCHEDULE")
                    }
                }
                "cancel", "cancelJob" -> {
                    val guestId = args.firstOrNull { it is Int } as? Int
                    if (guestId != null) {
                        val key = VirtualJobKey(identity.virtualUid, identity.guestPackageName, identity.instanceId, guestId)
                        val rec = M12RuntimeRegistries.jobs.get(key)
                        if (rec != null) args[args.indexOfFirst { it == guestId }] = rec.hostJobId
                        M12RuntimeRegistries.jobs.remove(key)
                        Log.i("AppSandbox.M12.Job", "JOB_CANCEL instance=${identity.instanceId} guestJobId=$guestId")
                    }
                }
                "cancelAll" -> {
                    val records = M12RuntimeRegistries.jobs.cancelAllForInstance(identity.guestPackageName, identity.instanceId)
                    // JobScheduler.cancelAll() has no namespace. Cancel individually to preserve isolation.
                    val cancel = original.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterTypes.any { p -> p == Int::class.javaPrimitiveType } }
                    records.forEach { record -> runCatching { cancel?.invoke(original, record.hostJobId) } }
                    return@newProxyInstance 0
                }
            }
            try { method.invoke(original, *args) } catch (e: InvocationTargetException) { throw e.targetException }
        }
        cache[serviceName] = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { p, m, a ->
            when (m.name) { "queryLocalInterface" -> proxy; "toString" -> "VirtualJobScheduler($identity)"; "equals" -> p === a?.firstOrNull(); "hashCode" -> System.identityHashCode(p); else -> m.invoke(raw, *(a ?: emptyArray())) }
        } as IBinder
        AdapterInstallResult(serviceName, true)
    }.getOrElse { Log.e("AppSandbox.M12.Job", "JOB_ADAPTER_INSTALL_FAILED", it); AdapterInstallResult(serviceName, false, failureReason = it.toString()) }

    private fun copyJob(source: JobInfo, id: Int, component: ComponentName, key: VirtualJobKey, guest: ComponentName?): JobInfo {
        val parcel = android.os.Parcel.obtain()
        source.writeToParcel(parcel, 0); parcel.setDataPosition(0)
        val copy = JobInfo.CREATOR.createFromParcel(parcel); parcel.recycle()
        fun set(name: String, value: Any?) {
            var type: Class<*>? = copy.javaClass
            while (type != null) {
                val field = runCatching { type!!.getDeclaredField(name) }.getOrNull()
                if (field != null) { field.isAccessible = true; field.set(copy, value); return }
                type = type!!.superclass
            }
        }
        set("mJobId", id); set("jobId", id); set("mService", component); set("service", component)
        set("mExtras", android.os.Bundle().apply { putString("m12_guest_package", key.packageName); putString("m12_instance", key.instanceId); putInt("m12_guest_job_id", key.guestJobId); putString("m12_guest_service", guest?.flattenToString()) })
        return copy
    }
}
