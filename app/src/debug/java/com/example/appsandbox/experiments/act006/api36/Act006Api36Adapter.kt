package com.example.appsandbox.experiments.act006.api36

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
import android.view.Window
import dalvik.system.DexClassLoader
import java.io.File

enum class Act006Reason { NONE, INVALID_INPUT, STALE_REVISION, SHA_MISMATCH, MISSING_CLASS, NON_ACTIVITY, API_MISMATCH, ACCESS_DENIED, ATTACH_FAILED, PROCESS_RECOVERY_REQUIRED }
data class Act006Request(val api: Int, val apk: File, val className: String, val sha256: String, val expectedSha256: String, val host: Activity, val injectBadToken: Boolean = false)
data class Act006Result(val reason: Act006Reason, val constructor: Boolean, val attachReturn: String, val baseContext: Boolean, val application: Boolean, val intent: Boolean, val activityInfo: Boolean, val window: Boolean, val token: Boolean, val lifecycle: Boolean = false, val fingerprint: String)

/** API36-only, debug-only carrier experiment. No lifecycle is invoked. */
class Act006Api36Adapter {
    fun attach(request: Act006Request): Act006Result {
        if (request.api != 36) return fail(Act006Reason.API_MISMATCH, "expected=36")
        if (request.sha256 != request.expectedSha256) return fail(Act006Reason.SHA_MISMATCH, "sha")
        val type = runCatching { DexClassLoader(request.apk.path, request.host.codeCacheDir.path, null, javaClass.classLoader).loadClass(request.className) }
            .getOrElse { return fail(Act006Reason.MISSING_CLASS, "${it.javaClass.simpleName}") }
        if (!Activity::class.java.isAssignableFrom(type)) return fail(Act006Reason.NON_ACTIVITY, type.name)
        val guest = runCatching { type.getDeclaredConstructor().newInstance() as Activity }
            .getOrElse { return fail(Act006Reason.MISSING_CLASS, "constructor:${it.javaClass.simpleName}") }
        val method = runCatching { Activity::class.java.declaredMethods.single { it.name == "attach" } }
            .getOrElse { return Act006Result(Act006Reason.ACCESS_DENIED, true, "NOT_CALLED", false, false, false, false, false, false, fingerprint()) }
        return try {
            method.isAccessible = true
            val info = ActivityInfo(request.host.packageManager.getActivityInfo(request.host.componentName, 0))
            val args = method.parameterTypes.mapIndexed { i, type -> when {
                i == 0 -> request.host
                type == Instrumentation::class.java -> Instrumentation()
                type == IBinder::class.java -> if (request.injectBadToken) null else request.host.window.decorView.applicationWindowToken
                type == Int::class.javaPrimitiveType -> request.host.taskId
                type == Application::class.java -> request.host.application
                type == Intent::class.java -> Intent(request.host.intent)
                type == ActivityInfo::class.java -> info
                type == CharSequence::class.java -> request.host.title
                type == android.content.res.Configuration::class.java -> request.host.resources.configuration
                type == Window::class.java -> request.host.window
                type == String::class.java -> null
                type.isPrimitive -> 0
                else -> null
            }}.toTypedArray()
            method.invoke(guest, *args)
            Act006Result(Act006Reason.NONE, true, "RETURNED", guest.baseContext != null, guest.application != null, guest.intent != null,
                true, guest.window != null, guest.window?.decorView?.windowToken != null, false, fingerprint(method.parameterTypes))
        } catch (t: Throwable) {
            Act006Result(Act006Reason.PROCESS_RECOVERY_REQUIRED, true, "THREW:${t.javaClass.simpleName}", false, false, false, false, false, false, false, fingerprint())
        }
    }
    private fun fail(reason: Act006Reason, detail: String) = Act006Result(reason, false, "NOT_CALLED", false, false, false, false, false, false, false, "$detail|${fingerprint()}")
    private fun fingerprint(types: Array<Class<*>>? = null) = "android.app.Activity.attach|params=${(types ?: API36_TYPES).joinToString(",") { it.name }}"
    companion object {
        private val API36_TYPES = arrayOf(Context::class.java, Class.forName("android.app.ActivityThread"), Instrumentation::class.java, IBinder::class.java, Int::class.javaPrimitiveType!!, Application::class.java, Intent::class.java, ActivityInfo::class.java, CharSequence::class.java, Activity::class.java, String::class.java, Class.forName("android.app.Activity\$NonConfigurationInstances"), android.content.res.Configuration::class.java, String::class.java, Class.forName("android.app.VoiceInteractor"), Window::class.java, Class.forName("android.app.Activity\$ActivityConfigCallback"), Any::class.java, IBinder::class.java, IBinder::class.java)
    }
}
