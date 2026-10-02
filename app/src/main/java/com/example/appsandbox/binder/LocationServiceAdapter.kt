package com.example.appsandbox.binder

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.location.GuestProcessLocationBinding
import com.example.appsandbox.location.GuestProcessLocationBindings
import com.example.appsandbox.location.VirtualLocationCoordinatorClient
import com.example.appsandbox.location.VirtualLocationMode
import com.example.appsandbox.location.VirtualLocationProfile
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LocationServiceAdapter(
    private val context: Context,
    private val identity: RuntimeIdentity,
    private val processBinding: GuestProcessLocationBinding
) : BinderServiceAdapter {
    override val serviceName = Context.LOCATION_SERVICE
    override val interfaceName = "android.location.ILocationManager"
    private val scheduler = HandlerThread("VirtualLocation-${identity.processSlot}").apply { start() }
    private val handler = Handler(scheduler.looper)
    private val registrations = ConcurrentHashMap<Any, Registration>()

    override fun install(): AdapterInstallResult = runCatching {
        val iface = Class.forName(interfaceName)
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java).apply { isAccessible = true }
        val originalBinder = requireNotNull(getService.invoke(null, serviceName) as? IBinder) { "location Binder unavailable" }
        val stub = Class.forName("android.location.ILocationManager\$Stub")
        val original = requireNotNull(stub.getDeclaredMethod("asInterface", IBinder::class.java).apply { isAccessible = true }.invoke(null, originalBinder))
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, raw ->
            val args = raw ?: emptyArray()
            Log.i(TAG, "VLOCATION_CALL method=${method.name} instance=${identity.instanceId} params=${method.parameterTypes.map { it.name }} args=${args.map { it?.javaClass?.name }}")
            when (method.name) {
                "toString" -> "VirtualLocationProxy(${identity.instanceId})"
                "asBinder" -> originalBinder
                "getLastLocation", "getLastKnownLocation" -> virtualLocation(provider(args), profile()) ?: invokePhysical(original, method, physicalArgs(args))
                "getCurrentLocation" -> currentLocation(method, args)
                "registerLocationListener", "requestLocationUpdates" -> register(method, args)
                "unregisterLocationListener", "removeUpdates" -> unregister(method, args)
                "getAllProviders" -> providers(profile()).toList()
                "getProviders" -> providers(profile()).toList()
                "hasProvider" -> providers(profile()).contains(args.filterIsInstance<String>().firstOrNull())
                "isProviderEnabledForUser", "isProviderEnabled" -> providers(profile()).contains(args.filterIsInstance<String>().firstOrNull())
                "isLocationEnabledForUser", "isLocationEnabled" -> profile()?.mode != VirtualLocationMode.UNAVAILABLE
                "registerGnssStatusCallback" -> false
                "unregisterGnssStatusCallback" -> null
                else -> invokePhysical(original, method, physicalArgs(args))
            }
        }
        val bridge = Binder().apply { attachInterface(proxy as IInterface, interfaceName) }
        @Suppress("UNCHECKED_CAST")
        val cache = serviceManager.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<String, IBinder>
        cache[serviceName] = bridge
        Log.i(TAG, "VLOCATION_BINDER_CACHE bridge=${getService.invoke(null, serviceName) === bridge} local=${bridge.queryLocalInterface(interfaceName)?.javaClass?.name}")
        clearCachedLocationManager()
        Log.i(TAG, "VLOCATION_INSTALL api=${android.os.Build.VERSION.SDK_INT} instance=${identity.instanceId} key=${processBinding.key} generation=${processBinding.generation}")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { AdapterInstallResult(serviceName, false, failureReason = it.toString()) }

    private fun clearCachedLocationManager() {
        val old = context.getSystemService(LocationManager::class.java) ?: return
        val field = generateSequence<Class<*>>(context.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name == "mServiceCache" && it.type.isArray } ?: return
        field.isAccessible = true
        val cache = field.get(context) as? Array<Any?> ?: return
        cache.indices.filter { cache[it] === old }.forEach { cache[it] = null }
        Log.i(TAG, "VLOCATION_CONTEXT_CACHE cleared=${cache.none { it === old }} context=${context.javaClass.name}")
    }

    private fun profile() = VirtualLocationCoordinatorClient.get(context, identity.instanceId)

    private fun providers(profile: VirtualLocationProfile?): Set<String> = when (profile?.mode) {
        VirtualLocationMode.UNAVAILABLE -> emptySet()
        VirtualLocationMode.FIXED, VirtualLocationMode.ROUTE -> profile.providers
        else -> setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    }

    private fun provider(args: Array<Any?>) = args.filterIsInstance<String>().firstOrNull { it in setOf("gps", "network", "passive", "fused") }
        ?: LocationManager.GPS_PROVIDER

    private fun virtualLocation(provider: String, profile: VirtualLocationProfile?): Location? = when (profile?.mode) {
        VirtualLocationMode.FIXED, VirtualLocationMode.ROUTE -> profile.points.firstOrNull()?.location(provider)
        VirtualLocationMode.UNAVAILABLE -> null
        else -> null
    }

    private fun currentLocation(method: Method, args: Array<Any?>): Any? {
        val profile = profile()
        if (profile == null || profile.mode == VirtualLocationMode.REAL_PASSTHROUGH) return null
        val callbackIndex = method.parameterTypes.indexOfFirst { it.name.endsWith("ILocationCallback") }
        val callback = args.getOrNull(callbackIndex) ?: return null
        val cancelled = AtomicBoolean(false)
        handler.postDelayed({
            if (!cancelled.get() && GuestProcessLocationBindings.isCurrent(processBinding)) {
                val location = virtualLocation(provider(args), profile)
                deliverCurrent(callback, location)
                Log.i(TAG, "VLOCATION_CURRENT instance=${identity.instanceId} generation=${processBinding.generation} delivered=true coordinate=${location?.latitude},${location?.longitude} mock=${location?.isMock}")
            }
        }, 50L)
        return cancellation(method.returnType, cancelled)
    }

    private fun cancellation(type: Class<*>, cancelled: AtomicBoolean): Any? {
        if (!type.isInterface) return null
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            if (method.name == "cancel") cancelled.set(true)
            null
        }
    }

    private fun register(method: Method, args: Array<Any?>): Any? {
        val profile = profile()
        if (profile == null || profile.mode == VirtualLocationMode.REAL_PASSTHROUGH) return null
        val callbackIndex = method.parameterTypes.indexOfFirst { it.name.endsWith("ILocationListener") }
        val callback = args.getOrNull(callbackIndex) ?: return null
        registrations.remove(callback)?.cancelled?.set(true)
        val request = args.firstOrNull { it?.javaClass?.name?.contains("LocationRequest") == true }
        val interval = longProperty(request, "getIntervalMillis", "getInterval", default = 1_000L).coerceAtLeast(100L)
        val minDistance = floatProperty(request, "getMinUpdateDistanceMeters", "getSmallestDisplacement", default = 0f)
        val maxUpdates = intProperty(request, "getMaxUpdates", "getNumUpdates", default = Int.MAX_VALUE)
        val duration = longProperty(request, "getDurationMillis", "getExpireAt", default = Long.MAX_VALUE)
        val registration = Registration(UUID.randomUUID().toString(), callback, provider(args), profile.generation,
            interval, minDistance, maxUpdates, duration, SystemClock.elapsedRealtime())
        registrations[callback] = registration
        schedule(registration, 0L)
        Log.i(TAG, "VLOCATION_REGISTER id=${registration.id} instance=${identity.instanceId} key=${processBinding.key} generation=${processBinding.generation} interval=$interval distance=$minDistance max=$maxUpdates")
        return null
    }

    private fun schedule(registration: Registration, delay: Long): Unit {
        handler.postDelayed({
        val latest = profile()
        if (registration.cancelled.get() || registrations[registration.callback] !== registration ||
            !GuestProcessLocationBindings.isCurrent(processBinding) || latest == null || latest.generation != registration.profileGeneration ||
            SystemClock.elapsedRealtime() - registration.started >= registration.duration || registration.delivered.get() >= registration.maxUpdates) return@postDelayed
        val pointIndex = if (latest.mode == VirtualLocationMode.ROUTE) {
            registration.routeCursor.getAndIncrement().coerceAtMost(latest.points.lastIndex)
        } else 0
        val location = latest.points.getOrNull(pointIndex)?.location(registration.provider) ?: return@postDelayed
        val previous = registration.last
        if (previous == null || previous.distanceTo(location) >= registration.minDistance) {
            registration.last = Location(location)
            registration.delivered.incrementAndGet()
            deliverLocations(registration.callback, listOf(Location(location)))
            Log.i(TAG, "VLOCATION_UPDATE id=${registration.id} instance=${identity.instanceId} generation=${processBinding.generation} count=${registration.delivered.get()} point=$pointIndex coordinate=${location.latitude},${location.longitude}")
        }
        schedule(registration, registration.interval)
        }, delay)
    }

    private fun unregister(method: Method, args: Array<Any?>): Any? {
        val callbackIndex = method.parameterTypes.indexOfFirst { it.name.endsWith("ILocationListener") }
        args.getOrNull(callbackIndex)?.let { callback -> registrations.remove(callback)?.also { it.cancelled.set(true); Log.i(TAG, "VLOCATION_REMOVE id=${it.id} count=${it.delivered.get()}") } }
        return null
    }

    private fun deliverCurrent(target: Any, location: Location?) {
        transact(target, 1) { parcel ->
            if (android.os.Build.VERSION.SDK_INT >= 33) parcel.writeTypedObject(location, 0)
            else { if (location == null) parcel.writeInt(0) else { parcel.writeInt(1); location.writeToParcel(parcel, 0) } }
        }
    }

    private fun deliverLocations(target: Any, locations: List<Location>) {
        transact(target, 1) { parcel ->
            parcel.writeTypedList(locations)
            parcel.writeStrongBinder(null)
        }
    }

    private fun transact(target: Any, code: Int, writer: (Parcel) -> Unit) {
        val binder = (target as IInterface).asBinder()
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken(requireNotNull(binder.interfaceDescriptor))
            writer(parcel)
            check(binder.transact(code, parcel, null, IBinder.FLAG_ONEWAY)) { "location callback transact rejected" }
        } finally { parcel.recycle() }
    }

    private fun physicalArgs(args: Array<Any?>) = args.copyOf().also { values ->
        values.indices.filter { values[it] == identity.guestPackageName }.forEach { values[it] = identity.hostPackageName }
    }
    private fun invokePhysical(original: Any, method: Method, args: Array<Any?>): Any? = method.invoke(original, *args)
    private fun longProperty(value: Any?, vararg names: String, default: Long) = names.firstNotNullOfOrNull { n -> runCatching { value?.javaClass?.getMethod(n)?.invoke(value) as? Number }.getOrNull()?.toLong() } ?: default
    private fun floatProperty(value: Any?, vararg names: String, default: Float) = names.firstNotNullOfOrNull { n -> runCatching { value?.javaClass?.getMethod(n)?.invoke(value) as? Number }.getOrNull()?.toFloat() } ?: default
    private fun intProperty(value: Any?, vararg names: String, default: Int) = names.firstNotNullOfOrNull { n -> runCatching { value?.javaClass?.getMethod(n)?.invoke(value) as? Number }.getOrNull()?.toInt() } ?: default

    private data class Registration(val id: String, val callback: Any, val provider: String, val profileGeneration: Long,
        val interval: Long, val minDistance: Float, val maxUpdates: Int, val duration: Long, val started: Long,
        val delivered: AtomicInteger = AtomicInteger(), val routeCursor: AtomicInteger = AtomicInteger(),
        val cancelled: AtomicBoolean = AtomicBoolean(), @Volatile var last: Location? = null)

    companion object { private const val TAG = "AppSandbox.M11" }
}
