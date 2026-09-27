package com.example.appsandbox.experiments.act006.api31

import android.app.Activity
import android.os.Build
import java.lang.reflect.Method

data class Act006AttachResult(val outcome: String, val reason: String, val classLoaded: Boolean = false,
    val constructed: Boolean = false, val attachInvokeAttempted: Boolean = false, val attachCompleted: Boolean = false,
    val lifecycle: Boolean = false, val exceptionType: String = "none", val detail: String = "none")

class Act006Api31Adapter {
    val fingerprint = "android.app.Activity#attach(Context,ActivityThread,Instrumentation,IBinder,int,Application,Intent,ActivityInfo,CharSequence,Activity,String,NonConfigurationInstances,Configuration,String,IVoiceInteractor,Window,ActivityConfigCallback,IBinder,IBinder)@31"

    fun execute(host: Activity, guestClass: Class<out Activity>, requestedFingerprint: String, denyAccess: Boolean): Act006AttachResult {
        if (Build.VERSION.SDK_INT != 31) return rejected("API_MISMATCH", true)
        if (requestedFingerprint != fingerprint) return rejected("WRONG_ADAPTER_FINGERPRINT", true)
        if (denyAccess) return rejected("ACCESS_DENIED", true)
        val method = findExactAttach() ?: return Act006AttachResult("REJECTED", "ACCESS_DENIED", true,
            detail = "Activity.attach not exposed by device reflection; invocation not attempted")
        val args = try { resolveHostArguments(host, method) } catch (error: Throwable) { return rejected("PARAMETER_SOURCE_UNAVAILABLE", true, error) }
        try { method.isAccessible = true } catch (error: Throwable) { return rejected("ACCESS_DENIED", true, error) }
        val guest = try { guestClass.getDeclaredConstructor().newInstance() } catch (error: Throwable) {
            return rejected("CONSTRUCTOR_FAILED", true, error)
        }
        return try {
            method.invoke(guest, *args)
            Act006AttachResult("PROCESS_RECOVERY_REQUIRED", "ATTACH_COMPLETED", true, true, true, true)
        } catch (error: Throwable) {
            val cause = root(error)
            Act006AttachResult("PROCESS_RECOVERY_REQUIRED", "ATTACH_FAILED", true, true, true, false,
                exceptionType = cause.javaClass.name, detail = cause.message.orEmpty())
        }
    }

    private fun findExactAttach(): Method? = Activity::class.java.declaredMethods.singleOrNull {
        it.name == "attach" && it.parameterTypes.map(Class<*>::getName) == PARAMETER_TYPES
    }

    private fun resolveHostArguments(host: Activity, method: Method): Array<Any?> {
        check(method.parameterCount == HOST_FIELDS.size + 1)
        return (listOf<Any?>(host.baseContext) + HOST_FIELDS.mapIndexed { fieldIndex, name ->
            val field = Activity::class.java.getDeclaredField(name); field.isAccessible = true
            field.get(host).also { if (method.parameterTypes[fieldIndex + 1].isPrimitive) check(it != null) { "$name is null" } }
        }).toTypedArray()
    }

    private fun rejected(reason: String, loaded: Boolean = false, error: Throwable? = null): Act006AttachResult {
        val cause = error?.let(::root)
        return Act006AttachResult("REJECTED", reason, loaded, exceptionType = cause?.javaClass?.name ?: "none",
            detail = cause?.message.orEmpty().ifBlank { "none" })
    }
    private fun root(error: Throwable): Throwable = generateSequence(error) { it.cause }.last()

    companion object {
        private val PARAMETER_TYPES = listOf("android.content.Context", "android.app.ActivityThread", "android.app.Instrumentation",
            "android.os.IBinder", "int", "android.app.Application", "android.content.Intent", "android.content.pm.ActivityInfo",
            "java.lang.CharSequence", "android.app.Activity", "java.lang.String", "android.app.Activity${'$'}NonConfigurationInstances",
            "android.content.res.Configuration", "java.lang.String", "com.android.internal.app.IVoiceInteractor", "android.view.Window",
            "android.app.Activity${'$'}ActivityConfigCallback", "android.os.IBinder", "android.os.IBinder")
        private val HOST_FIELDS = listOf("mMainThread", "mInstrumentation", "mToken", "mIdent", "mApplication", "mIntent",
            "mActivityInfo", "mTitle", "mParent", "mEmbeddedID", "mLastNonConfigurationInstances", "mCurrentConfig", "mReferrer",
            "mVoiceInteractor", "mWindow", "mActivityConfigCallback", "mAssistToken", "mShareableActivityToken")
    }
}
