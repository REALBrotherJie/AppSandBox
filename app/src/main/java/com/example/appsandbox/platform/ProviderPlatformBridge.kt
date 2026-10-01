package com.example.appsandbox.platform

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
}
