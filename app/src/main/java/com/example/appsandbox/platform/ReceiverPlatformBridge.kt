package com.example.appsandbox.platform

import android.os.Build

data class ReceiverPlatformProbe(
    val api: Int,
    val scheduleReceiver: List<String>,
    val scheduleRegisteredReceiver: List<String>,
    val scheduleReceiverList: List<String>,
    val receiverData: List<String>,
    val activityThreadHandlers: List<String>
) {
    val supportsManifestReceiverRestore get() = scheduleReceiver.isNotEmpty() && receiverData.isNotEmpty()
    val supportsRegisteredReceiverPath get() = scheduleRegisteredReceiver.isNotEmpty() || scheduleReceiverList.isNotEmpty()
}

object ReceiverPlatformBridge {
    fun probe(): ReceiverPlatformProbe {
        val appThread = runCatching { Class.forName("android.app.IApplicationThread") }.getOrNull()
        val activityThread = runCatching { Class.forName("android.app.ActivityThread") }.getOrNull()
        fun methods(type: Class<*>?, prefix: String) = type?.declaredMethods.orEmpty().filter { it.name == prefix }.map { it.toGenericString() }
        fun names(type: Class<*>?, prefix: String) = type?.declaredClasses.orEmpty().filter { it.simpleName.contains(prefix, true) }.map { it.name }
        return ReceiverPlatformProbe(Build.VERSION.SDK_INT, methods(appThread, "scheduleReceiver"), methods(appThread, "scheduleRegisteredReceiver"), methods(appThread, "scheduleReceiverList"), names(activityThread, "ReceiverData"), activityThread?.declaredMethods.orEmpty().filter { it.name.contains("Receiver") }.map { it.toGenericString() })
    }
}
