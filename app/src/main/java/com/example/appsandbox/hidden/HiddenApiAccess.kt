package com.example.appsandbox.hidden

import android.os.Handler

data class HiddenApiHandles(
    val activityThread: Any,
    val mainHandler: Handler,
    val activityTaskManager: Any
)

class HiddenApiAccessException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object HiddenApiAccess {
    @Volatile private var exemptionsInstalled = false

    fun probe(): Result<HiddenApiHandles> = runCatching {
        runCatching { installExemptions() }
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").apply { isAccessible = true }.invoke(null)
            ?: throw HiddenApiAccessException("ActivityThread.currentActivityThread() returned null")
        val handler = activityThreadClass.getDeclaredField("mH").apply { isAccessible = true }.get(activityThread) as? Handler
            ?: throw HiddenApiAccessException("ActivityThread.mH is not a Handler")
        val managerClass = Class.forName("android.app.ActivityTaskManager")
        val singleton = managerClass.getDeclaredField("IActivityTaskManagerSingleton").apply { isAccessible = true }.get(null)
            ?: throw HiddenApiAccessException("IActivityTaskManager singleton is null")
        val manager = Class.forName("android.util.Singleton").getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
            ?: throw HiddenApiAccessException("IActivityTaskManager singleton returned null")
        HiddenApiHandles(activityThread, handler, manager)
    }.recoverCatching { error ->
        if (error is HiddenApiAccessException) throw error
        throw HiddenApiAccessException("hidden API access failed at ${error.javaClass.simpleName}: ${error.message}", error)
    }

    @Synchronized
    private fun installExemptions() {
        if (exemptionsInstalled) return
        try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.declaredMethods.firstOrNull { it.name == "getRuntime" && it.parameterCount == 0 }
                ?: throw NoSuchMethodException("VMRuntime.getRuntime")
            val setExemptions = vmRuntimeClass.declaredMethods.firstOrNull { it.name == "setHiddenApiExemptions" }
                ?: throw NoSuchMethodException("VMRuntime.setHiddenApiExemptions")
            val runtime = getRuntime.invoke(null)
            val prefixes = arrayOf("Landroid/app/ActivityThread;", "Landroid/app/ActivityTaskManager;", "Landroid/util/Singleton;")
            setExemptions.invoke(runtime, arrayOf<Any>(prefixes))
            exemptionsInstalled = true
        } catch (error: Throwable) {
            throw HiddenApiAccessException("unable to install hidden API exemptions: ${error.javaClass.simpleName}: ${error.message}", error)
        }
    }
}
