package com.example.appsandbox.platform

import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Build

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
        return requireNotNull(method.invoke(activityThread, guestContext, null, info, true, true, true))
    }
}
