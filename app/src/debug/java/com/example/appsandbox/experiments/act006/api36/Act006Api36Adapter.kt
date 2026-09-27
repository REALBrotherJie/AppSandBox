package com.example.appsandbox.experiments.act006.api36

import android.app.Activity
import android.os.Build
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import com.example.appsandbox.experiments.act006.core.Act006AttachExecutor
import com.example.appsandbox.experiments.act006.core.Act006Expected
import com.example.appsandbox.experiments.act006.core.Act006InputSnapshot
import com.example.appsandbox.experiments.act006.core.Act006StateMachine

enum class Act006Reason { NONE, INVALID_INPUT, STALE_REVISION, SHA_MISMATCH, MISSING_CLASS, NON_ACTIVITY, API_MISMATCH, ACCESS_DENIED, DUPLICATE_LAUNCH, PROCESS_RECOVERY_REQUIRED }
data class Act006Request(val launchId: String, val className: String, val host: Activity, val forcedApi: Int? = null, val denyAccess: Boolean = false, val injectAttachFailure: Boolean = false)
data class Act006Result(val reason: Act006Reason, val constructorAttempted: Boolean = false, val constructorCompleted: Boolean = false, val attachInvokeAttempted: Boolean = false, val attachCompleted: Boolean = false, val attachResult: String = "NOT_CALLED", val fingerprint: String = "", val guestState: String = "NOT_CONSTRUCTED", val lifecycleCalls: Int = 0)

/** API36-only debug experiment. It never invokes a Guest lifecycle method. */
class Act006Api36Adapter {
    fun execute(request: Act006Request, instance: GuestInstanceRecord?, revision: GuestPackageRecord?): Act006Result {
        if (request.launchId.isBlank() || request.className.isBlank()) return result(Act006Reason.INVALID_INPUT)
        if ((request.forcedApi ?: Build.VERSION.SDK_INT) != 36 || Build.VERSION.SDK_INT != 36) return result(Act006Reason.API_MISMATCH)
        if (launches.putIfAbsent(request.launchId, request.className) != null) return result(Act006Reason.DUPLICATE_LAUNCH)
        if (instance == null || revision == null || instance.guestRevisionId != revision.revisionId || instance.guestPackageName != revision.packageName) return result(Act006Reason.STALE_REVISION)
        val apk = File(revision.apkPath)
        if (revision.sha256 != instance.guestSha256 || GuestArtifactVerifier.verify(revision).state != ArtifactState.VALID || sha256(apk) != revision.sha256 || apk.canWrite()) return result(Act006Reason.SHA_MISMATCH)
        val component = revision.components.singleOrNull { it.className == request.className && it.type.code == "activity" } ?: return result(Act006Reason.MISSING_CLASS)
        val loader = DexClassLoader(apk.path, request.host.codeCacheDir.path, null, javaClass.classLoader)
        val type = runCatching { loader.loadClass(component.className) }.getOrElse { return result(Act006Reason.MISSING_CLASS, fingerprint = "class:${root(it)}") }
        if (!Activity::class.java.isAssignableFrom(type)) return result(Act006Reason.NON_ACTIVITY, fingerprint = "classLoader=${type.classLoader.javaClass.name}")
        val prepared = runCatching { prepare(request.host, request.denyAccess) }.getOrElse { return result(Act006Reason.ACCESS_DENIED, fingerprint = "prepare:${root(it)}") }
        val input = Act006InputSnapshot(request.launchId, request.launchId, instance.instanceId, revision.revisionId,
            revision.sha256, component.className, request.host.componentName.flattenToShortString(), prepared.fingerprint)
        val expected = Act006Expected(input.instanceId, input.revisionId, input.artifactSha256, input.guestClass, input.hostCarrier, input.apiAdapterFingerprint)
        var guest: Activity? = null
        val core = Act006StateMachine(request.launchId, object : Act006AttachExecutor {
            override fun construct(input: Act006InputSnapshot): Any = (type.getDeclaredConstructor().newInstance() as Activity).also { guest = it }
            override fun attach(instance: Any, input: Act006InputSnapshot) {
                val args = prepared.args.copyOf(); if (request.injectAttachFailure) args[3] = null
                prepared.method.invoke(instance as Activity, *args)
            }
        }).execute(input, expected)
        val c = core.counters
        return Act006Result(if (core.attachedNoLifecycle) Act006Reason.NONE else Act006Reason.PROCESS_RECOVERY_REQUIRED,
            c.constructorAttempted == 1, c.constructorCompleted == 1, c.attachAttempted == 1, c.attachCompleted == 1,
            if (c.attachCompleted == 1) "RETURNED" else "THREW:${core.error.orEmpty()}", prepared.fingerprint,
            guest?.let { "base=${it.baseContext != null},app=${it.application != null},intent=${it.intent != null},window=${it.window != null},token=${it.window?.decorView?.windowToken != null}" } ?: "NOT_CONSTRUCTED")
    }

    private data class Prepared(val method: java.lang.reflect.Method, val args: Array<Any?>, val fingerprint: String)
    private fun prepare(host: Activity, deny: Boolean): Prepared {
        if (deny) error("forced access denial")
        val method = Activity::class.java.declaredMethods.filter { it.name == "attach" && it.parameterCount in 19..20 }.maxBy { it.parameterCount }.apply { isAccessible = true }
        fun field(name: String): Any? = Activity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(host)
        val values = mutableListOf<Any?>(host, field("mMainThread"), field("mInstrumentation"), field("mToken"), field("mIdent"), host.application,
            host.intent, field("mActivityInfo"), host.title, field("mParent"), field("mEmbeddedID"), field("mLastNonConfigurationInstances"),
            host.resources.configuration, field("mReferrer"), null, host.window, null, field("mAssistToken"), field("mShareableActivityToken"))
        if (method.parameterCount == 20) values += field("mInitialCallerInfoAccessToken")
        val args = values.toTypedArray()
        require(args[1] != null && args[2] != null && args[3] != null && args[4] is Int && args[7] != null && args[17] != null) { "required host attach value unavailable" }
        val names = listOf("context","activityThread","instrumentation","activityToken","ident","application","intent","activityInfo","title","parent","embeddedId","lastNonConfiguration","configuration","referrer","voiceInteractor","window","activityConfigCallback","assistToken","shareableActivityToken","initialCallerInfoAccessToken")
        return Prepared(method, args, method.parameterTypes.mapIndexed { i, c -> "${names[i]}:${c.name}" }.joinToString("|"))
    }
    private fun result(reason: Act006Reason, constructorAttempted: Boolean = false, fingerprint: String = "") = Act006Result(reason, constructorAttempted = constructorAttempted, fingerprint = fingerprint)
    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun root(t: Throwable): String { var x=t; while(x.cause != null) x=x.cause!!; return x.javaClass.name + ":" + (x.message ?: "") }
    companion object { private val launches = ConcurrentHashMap<String, String>() }
}
