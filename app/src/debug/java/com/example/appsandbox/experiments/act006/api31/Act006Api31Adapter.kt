package com.example.appsandbox.experiments.act006.api31

import android.app.Activity
import android.os.Build
import com.example.appsandbox.experiments.act006.core.Act006AttachExecutor
import com.example.appsandbox.experiments.act006.core.Act006InputSnapshot
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

data class Act006AttachResult(val outcome: String, val reason: String, val classLoaded: Boolean = false,
    val constructorAttempted: Boolean = false, val constructed: Boolean = false,
    val attachExecutorAttempted: Boolean = false, val attachInvokeAttempted: Boolean = false, val attachCompleted: Boolean = false,
    val lifecycle: Boolean = false, val exceptionType: String = "none", val detail: String = "none")

class Act006Api31Adapter {
    val fingerprint = "android.app.Activity#attach(Context,ActivityThread,Instrumentation,IBinder,int,Application,Intent,ActivityInfo,CharSequence,Activity,String,NonConfigurationInstances,Configuration,String,IVoiceInteractor,Window,ActivityConfigCallback,IBinder,IBinder)@31"

    sealed class Preparation {
        data class Ready(val method: Method, val arguments: Array<Any?>) : Preparation()
        data class Rejected(val result: Act006AttachResult) : Preparation()
    }

    fun prepare(host: Activity, requestedFingerprint: String, denyAccess: Boolean): Preparation {
        if (Build.VERSION.SDK_INT != 31) return Preparation.Rejected(rejected("API_MISMATCH", true))
        if (requestedFingerprint != fingerprint) return Preparation.Rejected(rejected("WRONG_ADAPTER_FINGERPRINT", true))
        if (denyAccess) return Preparation.Rejected(rejected("ACCESS_DENIED", true))
        val method = findExactAttach() ?: return Act006AttachResult("REJECTED", "ACCESS_DENIED", true,
            detail = "Activity.attach not exposed by device reflection; invocation not attempted").let(Preparation::Rejected)
        val args = try { resolveHostArguments(host, method) } catch (error: Throwable) {
            return Preparation.Rejected(rejected("PARAMETER_SOURCE_UNAVAILABLE", true, error))
        }
        try { method.isAccessible = true } catch (error: Throwable) {
            return Preparation.Rejected(rejected("ACCESS_DENIED", true, error))
        }
        return Preparation.Ready(method, args)
    }

    private fun findExactAttach(): Method? = Activity::class.java.declaredMethods.singleOrNull {
        it.name == "attach" && it.parameterTypes.map(Class<*>::getName) == PARAMETER_TYPES
    }

    private fun resolveHostArguments(host: Activity, method: Method): Array<Any?> {
        check(method.parameterCount == HOST_FIELDS.size + 1)
        check(android.os.IInterface::class.java.isAssignableFrom(method.parameterTypes[14])) { "voice parameter is not IInterface" }
        check(method.parameterTypes[15].isAssignableFrom(android.view.Window::class.java)) { "window parameter mismatch" }
        return (listOf<Any?>(host.baseContext) + HOST_FIELDS.mapIndexed { fieldIndex, name ->
            val field = Activity::class.java.getDeclaredField(name); field.isAccessible = true
            field.get(host).also {
                if (method.parameterTypes[fieldIndex + 1].isPrimitive) check(it != null) { "$name is null" }
                if (name == "mVoiceInteractor") check(it == null || method.parameterTypes[fieldIndex + 1].isInstance(it)) { "voice source mismatch" }
                if (name == "mWindow") error("Host Window ownership is not safely transferable")
            }
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

class Act006Api31Executor(
    private val guestClass: Class<out Activity>, private val preparation: Act006Api31Adapter.Preparation.Ready
) : Act006AttachExecutor {
    val hiddenInvokeAttempted = AtomicBoolean()
    val hiddenInvokeCompleted = AtomicBoolean()
    override fun construct(input: Act006InputSnapshot): Any = guestClass.getDeclaredConstructor().newInstance()
    override fun attach(instance: Any, input: Act006InputSnapshot) {
        try {
            hiddenInvokeAttempted.set(true)
            preparation.method.invoke(instance, *preparation.arguments)
            hiddenInvokeCompleted.set(true)
        }
        catch (error: Throwable) { throw generateSequence(error) { it.cause }.last() }
    }
}
