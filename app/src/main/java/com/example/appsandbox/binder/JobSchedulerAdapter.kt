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
            if (method.name.contains("cancel", ignoreCase = true))
                Log.i("AppSandbox.M12.Job", "JOB_CANCEL_CALL method=${method.name} instance=${identity.instanceId}")
            when (method.name) {
                "schedule", "enqueue" -> {
                    val index = args.indexOfFirst { it is JobInfo }
                    if (index >= 0) {
                        Log.i("AppSandbox.M12.Job", "R3_JOB_ADAPTER_ENTER method=${method.name} instance=${identity.instanceId}")
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
                "cancelAll", "cancelAllInNamespace", "cancelAllInAllNamespaces" -> {
                    val records = M12RuntimeRegistries.jobs.cancelAllForInstance(identity.guestPackageName, identity.instanceId)
                    // JobScheduler.cancelAll() has no namespace. Cancel individually to preserve isolation.
                    val cancel = original.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterTypes.any { p -> p == Int::class.javaPrimitiveType } }
                    records.forEach { record ->
                        val cancelArgs = cancel?.parameterTypes?.map { type ->
                            when (type) {
                                Int::class.javaPrimitiveType -> record.hostJobId
                                String::class.java -> null
                                else -> error("unsupported IJobScheduler.cancel parameter ${type.name}")
                            }
                        }?.toTypedArray()
                        runCatching { cancel?.invoke(original, *(cancelArgs ?: emptyArray())) }
                            .onFailure { Log.e("AppSandbox.M12.Job", "JOB_CANCEL_ALL_FAILED hostJobId=${record.hostJobId}", it) }
                    }
                    Log.i("AppSandbox.M12.Job", "JOB_CANCEL_ALL instance=${identity.instanceId} count=${records.size}")
                    return@newProxyInstance if (method.returnType == Void.TYPE) null else 0
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
        val guestExtras = source.extras?.let { android.os.PersistableBundle(it) } ?: android.os.PersistableBundle()
        val physicalExtras = android.os.PersistableBundle().apply {
            putString("m12_guest_package", key.packageName)
            putString("m12_instance", key.instanceId)
            putInt("m12_guest_job_id", key.guestJobId)
            putString("m12_guest_service", guest?.flattenToString())
            putLong("m12_generation", GuestProcessLocationBindings.current()?.generation ?: 0L)
            putPersistableBundle("m12_guest_extras", guestExtras)
        }
        val builder = JobInfo.Builder(id, component)
            .setExtras(physicalExtras)
            .setRequiresCharging(source.isRequireCharging)
            .setRequiresDeviceIdle(source.isRequireDeviceIdle)
            .setRequiresBatteryNotLow(source.isRequireBatteryNotLow)
            .setRequiresStorageNotLow(source.isRequireStorageNotLow)
        if (!source.isPersisted && !source.transientExtras.isEmpty) builder.setTransientExtras(source.transientExtras)
        source.clipData?.let { builder.setClipData(it, source.clipGrantFlags) }
        source.requiredNetwork?.let(builder::setRequiredNetwork)
        source.triggerContentUris?.forEach(builder::addTriggerContentUri)
        if (source.triggerContentUpdateDelay >= 0) builder.setTriggerContentUpdateDelay(source.triggerContentUpdateDelay)
        if (source.triggerContentMaxDelay >= 0) builder.setTriggerContentMaxDelay(source.triggerContentMaxDelay)
        if (source.isPeriodic) builder.setPeriodic(source.intervalMillis, source.flexMillis)
        else {
            if (source.minLatencyMillis > 0) builder.setMinimumLatency(source.minLatencyMillis)
            if (source.maxExecutionDelayMillis > 0) builder.setOverrideDeadline(source.maxExecutionDelayMillis)
        }
        if (!source.isRequireDeviceIdle) builder.setBackoffCriteria(source.initialBackoffMillis, source.backoffPolicy)
        if (source.estimatedNetworkDownloadBytes >= 0 || source.estimatedNetworkUploadBytes >= 0)
            builder.setEstimatedNetworkBytes(source.estimatedNetworkDownloadBytes, source.estimatedNetworkUploadBytes)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (source.minimumNetworkChunkBytes > 0) builder.setMinimumNetworkChunkBytes(source.minimumNetworkChunkBytes)
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            builder.setExpedited(source.isExpedited)
        }
        val copy = builder.build()
        Log.i("AppSandbox.M12.Job", "R3_PHYSICAL_JOB_READY hostJobId=$id extras=${copy.extras.keySet().sorted()}")
        return copy
    }
}
