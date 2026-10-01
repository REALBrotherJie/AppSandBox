package com.example.appsandbox.vpm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import com.example.appsandbox.binder.AdapterInstallResult
import com.example.appsandbox.binder.BinderCallResult
import com.example.appsandbox.binder.BinderRoute
import com.example.appsandbox.binder.BinderServiceAdapter
import com.example.appsandbox.binder.IdentityDecision
import com.example.appsandbox.binder.MethodPolicyRegistry
import com.example.appsandbox.binder.ProxySupport
import com.example.appsandbox.identity.RuntimeIdentity
import java.lang.reflect.Proxy

class GuestPackageManagerBridge(
    private val identity: RuntimeIdentity,
    private val service: VirtualPackageManagerService,
    private val activityThread: Any
) : BinderServiceAdapter {
    override val serviceName = "package"
    override val interfaceName = "android.content.pm.IPackageManager"
    private val visibility = VirtualPackageVisibilityPolicy(identity)

    override fun install(): AdapterInstallResult = runCatching {
        val threadClass = activityThread.javaClass
        val field = generateSequence(threadClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("sPackageManager") }.getOrNull() }.first()
            .apply { isAccessible = true }
        val getter = threadClass.getDeclaredMethod("getPackageManager").apply { isAccessible = true }
        val original = field.get(null) ?: getter.invoke(null)
        if (Proxy.isProxyClass(original.javaClass) && original.toString().startsWith("VirtualBinderProxy(")) {
            return AdapterInstallResult(serviceName, true, true)
        }
        val iface = Class.forName(interfaceName)
        val registry = MethodPolicyRegistry()
        val handled = setOf("getPackageInfo", "getApplicationInfo", "getActivityInfo", "getServiceInfo", "getReceiverInfo",
            "getProviderInfo", "getPackagesForUid", "checkPermission", "getInstalledPackages", "getInstalledApplications",
            "resolveIntent", "queryIntentActivities")
        handled.forEach { registry.register(it) { call, physical ->
            val values = call.args
            val component = values.filterIsInstance<ComponentName>().firstOrNull()
            val intent = values.filterIsInstance<Intent>().firstOrNull()
            val strings = values.filterIsInstance<String>()
            val versionedPackage = values.firstOrNull { it?.javaClass?.name == "android.content.pm.VersionedPackage" }
                ?.let { runCatching { it.javaClass.getMethod("getPackageName").invoke(it) as? String }.getOrNull() }
            val packageName = component?.packageName ?: intent?.component?.packageName ?: intent?.`package` ?: versionedPackage ?: when (call.methodName) {
                "checkPermission" -> strings.getOrNull(1)
                "getPackageInfo", "getApplicationInfo" -> strings.firstOrNull()
                else -> strings.firstOrNull { it == identity.guestPackageName }
            }
            val virtualRoute = packageName == identity.guestPackageName
            val queryRoute = visibility.route(packageName)
            val result: Any? = when {
                    isWebViewRuntimePackage(packageName) || (packageName == identity.hostPackageName && isWebViewCall()) -> physical(values)
                    call.methodName == "getPackageInfo" && virtualRoute -> service.getPackageInfo(identity.guestPackageName)
                    call.methodName == "getApplicationInfo" && virtualRoute -> service.getApplicationInfo(identity.guestPackageName)
                    call.methodName == "getActivityInfo" && component != null && virtualRoute -> service.getActivityInfo(component)
                    call.methodName == "getServiceInfo" && component != null && virtualRoute -> service.getServiceInfo(component)
                    call.methodName == "getReceiverInfo" && component != null && virtualRoute -> service.getReceiverInfo(component)
                    call.methodName == "getProviderInfo" && component != null && virtualRoute -> service.getProviderInfo(component)
                    call.methodName == "getPackagesForUid" && (values.firstOrNull() == identity.virtualUidNumber || values.firstOrNull() == identity.hostUid) -> service.getPackagesForUid(identity.virtualUidNumber)
                    call.methodName == "checkPermission" && virtualRoute -> service.checkPermission(values.filterIsInstance<String>().first(), identity.guestPackageName)
                    call.methodName == "getInstalledPackages" -> createSlice(call.method.returnType, listOfNotNull(service.getPackageInfo(identity.guestPackageName)))
                    call.methodName == "getInstalledApplications" -> createSlice(call.method.returnType, listOfNotNull(service.getApplicationInfo(identity.guestPackageName)))
                    (call.methodName == "resolveIntent" || call.methodName == "queryIntentActivities") && virtualRoute -> {
                        sanitizeResolution(physical(values), call.method.returnType)
                    }
                    queryRoute == PackageQueryRoute.HOST || queryRoute == PackageQueryRoute.DENY -> null
                    else -> physical(values)
                }
                val loggedRoute = if (call.methodName == "getPackagesForUid" &&
                    (values.firstOrNull() == identity.virtualUidNumber || values.firstOrNull() == identity.hostUid)) {
                    PackageQueryRoute.VIRTUAL
                } else queryRoute
                log(call.methodName, packageName, loggedRoute.name, result)
                BinderCallResult(result, if (loggedRoute == PackageQueryRoute.VIRTUAL) BinderRoute.VIRTUAL else BinderRoute.PHYSICAL,
                    if (loggedRoute == PackageQueryRoute.VIRTUAL) IdentityDecision.VIRTUALIZE else IdentityDecision.PASSTHROUGH)
        } }
        val proxy = ProxySupport.create(original, iface, serviceName, identity, registry)
        field.set(null, proxy)
        Log.i(TAG, "VPM_INSTALL api=${Build.VERSION.SDK_INT} instance=${identity.instanceId} virtualUid=${identity.virtualUidNumber} interface=${iface.name}")
        AdapterInstallResult(serviceName, true)
    }.getOrElse { error ->
        logFailure("install", identity.guestPackageName, error)
        AdapterInstallResult(serviceName, false, failureReason = error.toString())
    }

    private fun sanitizeResolution(result: Any?, returnType: Class<*>): Any? {
        if (result is ResolveInfo) return service.sanitizeResolveInfo(result)
        if (result == null) return null
        val list = runCatching { result.javaClass.getMethod("getList").invoke(result) as? List<*> }.getOrNull() ?: return result
        val sanitized = list.filterIsInstance<ResolveInfo>().mapNotNull(service::sanitizeResolveInfo)
        return createSlice(returnType, sanitized, result.javaClass)
    }

    private fun createSlice(returnType: Class<*>, values: List<*>, fallbackType: Class<*> = returnType): Any {
        val constructor = returnType.declaredConstructors.firstOrNull { it.parameterTypes.contentEquals(arrayOf(List::class.java)) }
            ?: fallbackType.declaredConstructors.first { it.parameterTypes.contentEquals(arrayOf(List::class.java)) }
        return constructor.apply { isAccessible = true }.newInstance(values)
    }

    private fun log(method: String, target: String?, route: String, result: Any?) {
        Log.i(TAG, "VPM_QUERY api=${Build.VERSION.SDK_INT} instance=${identity.instanceId} virtualUid=${identity.virtualUidNumber} " +
            "method=$method targetPackage=$target route=$route result=${result?.javaClass?.simpleName ?: "null"}")
    }

    private fun logFailure(method: String, target: String?, error: Throwable) {
        Log.e(TAG, "VPM_QUERY api=${Build.VERSION.SDK_INT} instance=${identity.instanceId} virtualUid=${identity.virtualUidNumber} " +
            "method=$method targetPackage=$target route=ERROR result=${error.javaClass.name}:${error.message}", error)
    }

    private fun isWebViewRuntimePackage(packageName: String?): Boolean = packageName == "com.google.android.webview" ||
        packageName == "com.android.webview" || packageName == "com.google.android.trichromelibrary"

    private fun isWebViewCall(): Boolean = Thread.currentThread().stackTrace.any {
        it.className.startsWith("android.webkit.") || it.className.startsWith("org.chromium.") ||
            it.className.startsWith("com.android.webview.")
    }

    companion object { private const val TAG = "AppSandbox.M3" }
}
