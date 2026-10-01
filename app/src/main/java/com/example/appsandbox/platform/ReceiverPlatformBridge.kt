package com.example.appsandbox.platform

import android.os.Build

data class ReceiverPlatformProbe(
    val api: Int,
    val appThreadClass: String?,
    val appThreadHierarchy: List<String>,
    val appThreadInterfaces: List<String>,
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
    fun probe(activityThread: Any): ReceiverPlatformProbe {
        val field = generateSequence(activityThread.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mAppThread") }.getOrNull() }.firstOrNull()?.apply { isAccessible = true }
        val appThreadObject = runCatching { field?.get(activityThread) }.getOrNull()
        val appThread = appThreadObject?.javaClass
        val activityThreadClass = runCatching { Class.forName("android.app.ActivityThread") }.getOrNull()
        val hierarchy = generateSequence(appThread) { it.superclass }.toList()
        val allMethods = hierarchy.flatMap { it.declaredMethods.asList() } + appThread?.methods.orEmpty()
        fun methods(prefix: String) = allMethods.filter { it.name == prefix }.map { it.toGenericString() }.distinct()
        fun names(type: Class<*>?, prefix: String) = type?.declaredClasses.orEmpty().filter { it.simpleName.contains(prefix, true) }.map { it.name }
        return ReceiverPlatformProbe(Build.VERSION.SDK_INT, appThread?.name, hierarchy.map { it.name },
            hierarchy.flatMap { it.interfaces.asList() }.map { it.name }.distinct(),
            methods("scheduleReceiver"), methods("scheduleRegisteredReceiver"), methods("scheduleReceiverList"),
            names(activityThreadClass, "ReceiverData"), activityThreadClass?.declaredMethods.orEmpty().filter { it.name.contains("Receiver") }.map { it.toGenericString() })
    }

    fun inspectReceiverData(data: Any): Map<String, String> = generateSequence(data.javaClass) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }.associate { field ->
            field.isAccessible = true
            field.name to runCatching { field.get(data)?.toString() ?: "null" }.getOrElse { "<${it.javaClass.simpleName}>" }
        }
}
