package com.example.appsandbox.platform

import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Build
import android.os.Process
import android.util.Log

data class ProviderPlatformProbe(val api: Int, val installProvider: List<String>, val acquireProvider: List<String>, val providerMaps: List<String>) {
    val supportsLocalInstall get() = installProvider.isNotEmpty() && providerMaps.isNotEmpty()
}

object ProviderPlatformBridge {
    fun probe(): ProviderPlatformProbe {
        val type = runCatching { Class.forName("android.app.ActivityThread") }.getOrNull()
        val methods = type?.declaredMethods.orEmpty()
        val fields = generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }
        return ProviderPlatformProbe(Build.VERSION.SDK_INT,
            methods.filter { it.name == "installProvider" }.map { it.toGenericString() },
            methods.filter { it.name.contains("acquireProvider") }.map { it.toGenericString() },
            fields.filter { it.name.contains("ProviderMap") || it.name.contains("LocalProviders") }.map { it.name }.toList())
    }

    fun installLocalProvider(activityThread: Any, guestContext: Context, info: ProviderInfo): Any {
        require(guestContext.packageName == info.packageName) {
            "installProvider context/package mismatch context=${guestContext.packageName} info=${info.packageName}"
        }
        require(guestContext.classLoader.loadClass(info.name).name == info.name) {
            "Guest Context ClassLoader cannot load ${info.name}"
        }
        val method = activityThread.javaClass.declaredMethods.singleOrNull {
            it.name == "installProvider" && it.parameterTypes.size == 6 && it.parameterTypes[0] == Context::class.java
        } ?: error("ActivityThread.installProvider(Context,holder,ProviderInfo,boolean,boolean,boolean) unavailable")
        method.isAccessible = true
        val holder = requireNotNull(method.invoke(activityThread, guestContext, null, info, true, true, true))
        val maps = listOf("mLocalProvidersByName", "mProviderMap").associateWith { name ->
            runCatching {
                val field = generateSequence(activityThread.javaClass) { it.superclass }
                    .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }.first().apply { isAccessible = true }
                val value = field.get(activityThread)
                when (value) {
                    is Map<*, *> -> value.keys.map { it.toString() }
                    else -> value.toString()
                }
            }.getOrElse { listOf("ERROR:${it.javaClass.simpleName}") }
        }
        val userId = Process.myUid() / 100000
        val existing = activityThread.javaClass.declaredMethods.firstOrNull {
            it.name == "acquireExistingProvider" && it.parameterTypes.size == 4
        }?.let { acquire ->
            acquire.isAccessible = true
            acquire.invoke(activityThread, guestContext, info.authority.substringBefore(';'), userId, true)
        }
        Log.i("AppSandbox.M8", "VPROVIDER event=LOCAL_INSTALL authority=${info.authority} userId=$userId maps=$maps acquireExisting=${existing != null}")
        require(existing != null) { "ActivityThread local provider map miss authority=${info.authority} userId=$userId" }
        return holder
    }
}
