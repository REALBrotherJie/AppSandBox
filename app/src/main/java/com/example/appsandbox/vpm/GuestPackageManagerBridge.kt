package com.example.appsandbox.vpm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import com.example.appsandbox.identity.RuntimeIdentity
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class GuestPackageManagerBridge(
    private val identity: RuntimeIdentity,
    private val service: VirtualPackageManagerService
) {
    private val visibility = VirtualPackageVisibilityPolicy(identity)

    fun install(activityThread: Any) {
        val threadClass = activityThread.javaClass
        val field = generateSequence(threadClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("sPackageManager") }.getOrNull() }.first()
            .apply { isAccessible = true }
        val getter = threadClass.getDeclaredMethod("getPackageManager").apply { isAccessible = true }
        val original = field.get(null) ?: getter.invoke(null)
        if (Proxy.isProxyClass(original.javaClass)) return
        val iface = Class.forName("android.content.pm.IPackageManager")
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
            val values = args ?: emptyArray()
            val component = values.filterIsInstance<ComponentName>().firstOrNull()
            val intent = values.filterIsInstance<Intent>().firstOrNull()
            val strings = values.filterIsInstance<String>()
            val packageName = component?.packageName ?: intent?.component?.packageName ?: intent?.`package` ?: when (method.name) {
                "checkPermission" -> strings.getOrNull(1)
                "getPackageInfo", "getApplicationInfo" -> strings.firstOrNull()
                else -> strings.firstOrNull { it == identity.guestPackageName }
            }
            val virtualRoute = packageName == identity.guestPackageName
            val queryRoute = visibility.route(packageName)
            try {
                val result: Any? = when {
                    method.name == "getPackageInfo" && virtualRoute -> service.getPackageInfo(identity.guestPackageName)
                    method.name == "getApplicationInfo" && virtualRoute -> service.getApplicationInfo(identity.guestPackageName)
                    method.name == "getActivityInfo" && component != null && virtualRoute -> service.getActivityInfo(component)
                    method.name == "getServiceInfo" && component != null && virtualRoute -> service.getServiceInfo(component)
                    method.name == "getReceiverInfo" && component != null && virtualRoute -> service.getReceiverInfo(component)
                    method.name == "getProviderInfo" && component != null && virtualRoute -> service.getProviderInfo(component)
                    method.name == "getPackagesForUid" && values.firstOrNull() == identity.virtualUidNumber -> service.getPackagesForUid(identity.virtualUidNumber)
                    method.name == "checkPermission" && virtualRoute -> service.checkPermission(values.filterIsInstance<String>().first(), identity.guestPackageName)
                    method.name == "getInstalledPackages" -> createSlice(method.returnType, listOfNotNull(service.getPackageInfo(identity.guestPackageName)))
                    method.name == "getInstalledApplications" -> createSlice(method.returnType, listOfNotNull(service.getApplicationInfo(identity.guestPackageName)))
                    (method.name == "resolveIntent" || method.name == "queryIntentActivities") && virtualRoute -> {
                        sanitizeResolution(method.invoke(original, *values), method.returnType)
                    }
                    queryRoute == PackageQueryRoute.HOST || queryRoute == PackageQueryRoute.DENY -> null
                    else -> method.invoke(original, *values)
                }
                val loggedRoute = if (method.name == "getPackagesForUid" && values.firstOrNull() == identity.virtualUidNumber) {
                    PackageQueryRoute.VIRTUAL
                } else queryRoute
                log(method.name, packageName, loggedRoute.name, result)
                result
            } catch (error: InvocationTargetException) {
                logFailure(method.name, packageName, error.targetException)
                throw error.targetException
            }
        }
        field.set(null, proxy)
        Log.i(TAG, "VPM_INSTALL api=${Build.VERSION.SDK_INT} instance=${identity.instanceId} virtualUid=${identity.virtualUidNumber} interface=${iface.name}")
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

    companion object { private const val TAG = "AppSandbox.M3" }
}
