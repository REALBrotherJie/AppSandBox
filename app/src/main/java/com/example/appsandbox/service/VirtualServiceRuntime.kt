package com.example.appsandbox.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Binder
import android.os.IInterface
import android.os.Parcel
import android.os.Message
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import com.example.appsandbox.stub.StubServices
import com.example.appsandbox.vpm.VirtualPackageManagerService
import com.example.appsandbox.runtime.VirtualProcessCoordinatorClient
import com.example.appsandbox.runtime.VirtualProcessKey
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

data class VirtualServiceKey(val packageName: String, val instanceId: String, val component: ComponentName)

data class VirtualServiceRecord(
    val key: VirtualServiceKey,
    val virtualUid: Int,
    val guestInfo: ServiceInfo,
    val stubComponent: ComponentName,
    var starts: Int = 0,
    var lastStartId: Int = 0,
    var bound: Boolean = false,
    var created: Boolean = false
)

object VirtualServiceRuntime {
    private const val EXTRA_MARKER = "com.example.appsandbox.VSERVICE"
    private const val EXTRA_ORIGINAL = "com.example.appsandbox.VSERVICE_ORIGINAL"
    private val byStub = ConcurrentHashMap<ComponentName, VirtualServiceRecord>()
    private val byToken = ConcurrentHashMap<IBinder, VirtualServiceRecord>()
    private val allocations = ConcurrentHashMap<VirtualServiceKey, Int>()
    private val connectionFacades = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<Any, Any>())

    fun registerAgentRoute(record: VirtualServiceRecord) {
        byStub[record.stubComponent] = record
        Log.i("AppSandbox.M10.Arch", "AGENT_SERVICE_ROUTE generation=${record.lastStartId} stub=${record.stubComponent.flattenToShortString()} guest=${record.key.component.flattenToShortString()}")
    }

    fun routedProbeIntent(stub: ComponentName, original: Intent): Intent = Intent(original).apply {
        component = stub
        putExtra(EXTRA_MARKER, true)
        putExtra(EXTRA_ORIGINAL, Intent(original))
    }

    fun route(context: Context, identity: RuntimeIdentity, vpm: VirtualPackageManagerService, original: Intent): Intent? {
        val component = original.component ?: return null
        if (component.packageName != identity.guestPackageName) return null
        val info = vpm.getServiceInfo(component) ?: return null
        val logicalProcess = VirtualProcessKey.canonicalProcessName(identity.guestPackageName, info.processName)
        if (logicalProcess != identity.guestPackageName) {
            val route = VirtualProcessCoordinatorClient.ensureService(context, identity.instanceId, info)
            val stub = requireNotNull(StubServices.intent(context, route.slot).component)
            Log.i("AppSandbox.M10", "REMOTE_SERVICE_ROUTE package=${identity.guestPackageName} instance=${identity.instanceId} " +
                "logicalProcess=$logicalProcess slot=${route.slot} pid=${route.pid} generation=${route.generation} transaction=${route.transactionId}")
            return routedProbeIntent(stub, original)
        }
        val key = VirtualServiceKey(identity.guestPackageName, identity.instanceId, component)
        val index = allocations.computeIfAbsent(key) {
            val used = allocations.filterKeys { existing -> existing.packageName == key.packageName && existing.instanceId == key.instanceId }.values.toSet()
            (0..3).firstOrNull { it !in used } ?: error("no StubService available for ${key.instanceId}")
        }
        val stub = requireNotNull(StubServices.intent(context, identity.processSlot, index).component)
        byStub.computeIfAbsent(stub) { VirtualServiceRecord(key, identity.virtualUidNumber, info, stub) }
        Log.i("AppSandbox.M7", "VSERVICE package=${key.packageName} instance=${key.instanceId} virtualUid=${identity.virtualUidNumber} " +
            "guestComponent=${component.flattenToShortString()} stubComponent=${stub.flattenToShortString()} event=RESOLVE")
        return Intent(original).apply {
            setComponent(stub)
            putExtra(EXTRA_MARKER, true)
            putExtra(EXTRA_ORIGINAL, Intent(original))
        }
    }

    fun logicalResult(physical: Any?, routed: Intent): Any? =
        if (physical is ComponentName && routed.getBooleanExtra(EXTRA_MARKER, false)) original(routed)?.component else physical

    fun restore(message: Message, hostContext: Context): Boolean {
        val data = message.obj ?: return false
        return when (message.what) {
            114 -> restoreCreate(data, hostContext)
            115, 121, 122 -> restoreIntent(message.what, data)
            116 -> logToken("STOP", data)
            else -> false
        }
    }

    private fun restoreCreate(data: Any, context: Context): Boolean {
        val field = field(data.javaClass, "info") ?: return false
        val physical = field.get(data) as? ServiceInfo ?: return false
        val record = byStub[ComponentName(physical.packageName, physical.name)] ?: return false
        (field(data.javaClass, "token")?.get(data) as? IBinder)?.let { byToken[it] = record }
        val info = ServiceInfo(record.guestInfo)
        val app = ApplicationInfo(requireNotNull(info.applicationInfo)).apply {
            packageName = record.key.packageName
            uid = context.applicationInfo.uid
            processName = context.packageName + ":p" + record.stubComponent.className.substringAfter(".P").substringBefore("Service")
        }
        info.applicationInfo = app
        info.packageName = record.key.packageName
        info.processName = app.processName
        field.set(data, info)
        record.created = true
        Log.i("AppSandbox.M7", "VSERVICE_TX api=${android.os.Build.VERSION.SDK_INT} transaction=CREATE_SERVICE " +
            "physicalComponent=${record.stubComponent.flattenToShortString()} guestComponent=${record.key.component.flattenToShortString()} route=RESTORE result=PASS")
        return true
    }

    private fun restoreIntent(what: Int, data: Any): Boolean {
        val intentField = field(data.javaClass, "intent") ?: field(data.javaClass, "args") ?: return false
        val routed = intentField.get(data) as? Intent ?: return false
        val guest = original(routed) ?: return false
        val record = routed.component?.let(byStub::get) ?: return false
        guest.setExtrasClassLoader(record.guestInfo.applicationInfo?.let { Thread.currentThread().contextClassLoader })
        intentField.set(data, guest)
        val event = when (what) { 115 -> "START_COMMAND"; 121 -> "BIND"; else -> "UNBIND" }
        if (what == 115) record.starts++ else record.bound = what == 121
        Log.i("AppSandbox.M7", "VSERVICE_TX api=${android.os.Build.VERSION.SDK_INT} transaction=$event " +
            "physicalComponent=${record.stubComponent.flattenToShortString()} guestComponent=${record.key.component.flattenToShortString()} route=RESTORE result=PASS")
        return true
    }

    fun wrapConnection(connection: Any?, guest: ComponentName, iface: Class<*>): Any? {
        if (connection == null) return null
        val originalBinder = (connection as IInterface).asBinder()
        val connected = iface.methods.first { it.name == "connected" }
        val facade = object : Binder() {
            init { attachInterface(null, "android.app.IServiceConnection") }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != FIRST_CALL_TRANSACTION) return originalBinder.transact(code, data, reply, flags)
                data.enforceInterface("android.app.IServiceConnection")
                val values = connected.parameterTypes.map { type -> when {
                    type == ComponentName::class.java -> data.readTypedObject(ComponentName.CREATOR)
                    type == IBinder::class.java -> data.readStrongBinder()
                    type == Boolean::class.javaPrimitiveType -> data.readBoolean()
                    type == Int::class.javaPrimitiveType -> data.readInt()
                    type == String::class.java -> data.readString()
                    else -> error("unsupported IServiceConnection parameter ${type.name}")
                } }.toTypedArray()
                values.indices.filter { connected.parameterTypes[it] == ComponentName::class.java }.forEach { values[it] = guest }
                connected.invoke(connection, *values)
                Log.i("AppSandbox.M7", "VSERVICE connection=${System.identityHashCode(connection)} guestComponent=${guest.flattenToShortString()} event=CONNECTED")
                return true
            }
        }
        val proxy = java.lang.reflect.Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
            if (method.name == "asBinder") facade else method.invoke(connection, *(args ?: emptyArray()))
        }
        connectionFacades[connection] = proxy
        return proxy
    }

    fun facadeFor(connection: Any?): Any? = if (connection == null) null else connectionFacades.remove(connection) ?: connection

    fun restoreCallbackArgs(method: String, args: Array<Any?>): Array<Any?> {
        val token = args.firstOrNull { it is IBinder } as? IBinder
        val record = token?.let(byToken::get) ?: return args
        return args.copyOf().also { copy ->
            if (method == "stopServiceToken" || method == "setServiceForeground" || method == "publishService" || method == "unbindFinished") {
                copy.indices.filter { copy[it] is ComponentName }.forEach { copy[it] = record.stubComponent }
                copy.indices.filter { copy[it] is String && copy[it] == record.key.packageName }.forEach {
                    copy[it] = record.stubComponent.packageName
                }
                copy.indices.filter { copy[it] is Intent }.forEach { index ->
                    copy[index] = Intent(copy[index] as Intent).setComponent(record.stubComponent)
                }
            }
        }
    }

    private fun original(intent: Intent): Intent? = intent.getParcelableExtra(EXTRA_ORIGINAL)
    private fun logToken(event: String, data: Any): Boolean {
        Log.i("AppSandbox.M7", "VSERVICE_TX api=${android.os.Build.VERSION.SDK_INT} transaction=$event token=${field(data.javaClass, "token")?.get(data)} route=PHYSICAL result=PASS")
        return false
    }
    private fun field(type: Class<*>, name: String): Field? = generateSequence(type) { it.superclass }
        .mapNotNull { runCatching { it.getDeclaredField(name).apply { isAccessible = true } }.getOrNull() }.firstOrNull()
}
