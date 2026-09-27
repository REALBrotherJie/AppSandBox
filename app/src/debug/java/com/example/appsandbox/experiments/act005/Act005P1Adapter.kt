package com.example.appsandbox.experiments.act005

import android.app.Activity
import android.os.Build
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class Act005Phase {
    RECEIVED, MAPPED, CAPABILITY_CHECKED, CLASS_SELECTION_PENDING, CLASS_SELECTED, ROLLED_BACK_TO_HOST, FALLBACK_HOST, REJECTED
}

enum class Act005Reason {
    NONE, INVALID_INPUT, STALE_MAPPING, ARTIFACT_MISMATCH, COMPONENT_MISMATCH, NON_ACTIVITY_CLASS,
    MISSING_CLASS, API_MISMATCH, ACCESS_DENIED, UNSUPPORTED, DUPLICATE_LAUNCH
}

data class Act005Request(
    val launchId: String,
    val instanceId: String,
    val revisionId: String,
    val artifactSha256: String,
    val guestActivityClass: String,
    val hostStubComponent: String,
    val forcedApi: Int? = null,
    val denyAccess: Boolean = false
)

data class Act005Capability(
    val api: Int,
    val adapter: String,
    val supported: Boolean,
    val access: String,
    val fingerprint: String
)

data class Act005Result(
    val phases: List<Act005Phase>,
    val reason: Act005Reason,
    val capability: Act005Capability,
    val selectedClass: String? = null,
    val guestObjectConstructed: Boolean = false,
    val guestAttached: Boolean = false,
    val guestLifecycle: Boolean = false
) {
    val hostFallback: Boolean get() = phases.lastOrNull() in setOf(Act005Phase.ROLLED_BACK_TO_HOST, Act005Phase.FALLBACK_HOST, Act005Phase.REJECTED)
}

interface Act005P1Adapter {
    fun capability(request: Act005Request): Act005Capability
}

class Act005Api31Adapter : Act005P1Adapter {
    override fun capability(request: Act005Request): Act005Capability = probe(
        request, 31, "API31", "android.app.ActivityThread\$ActivityClientRecord",
        listOf("activityInfo", "intent", "token")
    )
}

class Act005Api36Adapter : Act005P1Adapter {
    override fun capability(request: Act005Request): Act005Capability = probe(
        request, 36, "API36", "android.app.servertransaction.LaunchActivityItem",
        listOf("mInfo", "mIntent")
    )
}

private fun probe(request: Act005Request, expectedApi: Int, name: String, className: String, members: List<String>): Act005Capability {
    val actual = request.forcedApi ?: Build.VERSION.SDK_INT
    if (actual != expectedApi) return Act005Capability(actual, name, false, "API_MISMATCH", "$className|expected=$expectedApi")
    if (request.denyAccess) return Act005Capability(actual, name, false, "ACCESS_DENIED", "$className|forced-denial")
    return runCatching {
        val type = Class.forName(className, false, Activity::class.java.classLoader)
        val declared = (type.declaredFields.map { it.name } + type.declaredMethods.map { it.name }).toSet()
        val present = members.filter { it in declared }
        Act005Capability(actual, name, present.isNotEmpty(), if (present.isEmpty()) "UNSUPPORTED" else "DECLARATION_ONLY",
            "$className|members=${present.sorted().joinToString(",")}|fields=${type.declaredFields.size}|methods=${type.declaredMethods.size}")
    }.getOrElse { Act005Capability(actual, name, false, "ACCESS_DENIED", "$className|${it.javaClass.name}") }
}

class Act005P1Selector(private val codeCacheDir: File) {
    fun select(request: Act005Request, instance: GuestInstanceRecord?, revision: GuestPackageRecord?): Act005Result {
        val phases = mutableListOf(Act005Phase.RECEIVED)
        fun rejected(reason: Act005Reason, capability: Act005Capability = unavailable(request)) =
            Act005Result(phases + Act005Phase.REJECTED, reason, capability)
        if (listOf(request.launchId, request.instanceId, request.revisionId, request.artifactSha256,
                request.guestActivityClass, request.hostStubComponent).any { it.isBlank() }) return rejected(Act005Reason.INVALID_INPUT)
        val prior = launches.putIfAbsent(request.launchId, request)
        if (prior != null && prior != request) return rejected(Act005Reason.DUPLICATE_LAUNCH)
        if (instance == null || revision == null || instance.instanceId != request.instanceId ||
            instance.guestRevisionId != request.revisionId || revision.revisionId != request.revisionId ||
            instance.guestPackageName != revision.packageName) return rejected(Act005Reason.STALE_MAPPING)
        phases += Act005Phase.MAPPED
        if (revision.sha256 != request.artifactSha256 || instance.guestSha256 != request.artifactSha256 ||
            GuestArtifactVerifier.verify(revision).state != ArtifactState.VALID || sha256(File(revision.apkPath)) != request.artifactSha256) {
            return rejected(Act005Reason.ARTIFACT_MISMATCH)
        }
        val component = revision.components.singleOrNull { it.className == request.guestActivityClass && it.type.code == "activity" }
            ?: return rejected(Act005Reason.COMPONENT_MISMATCH)
        val adapter = when (Build.VERSION.SDK_INT) { 31 -> Act005Api31Adapter(); 36 -> Act005Api36Adapter(); else -> return rejected(Act005Reason.UNSUPPORTED) }
        val capability = adapter.capability(request)
        phases += Act005Phase.CAPABILITY_CHECKED
        if (!capability.supported) return Act005Result(phases + Act005Phase.FALLBACK_HOST,
            when (capability.access) { "API_MISMATCH" -> Act005Reason.API_MISMATCH; "ACCESS_DENIED" -> Act005Reason.ACCESS_DENIED; else -> Act005Reason.UNSUPPORTED }, capability)
        phases += Act005Phase.CLASS_SELECTION_PENDING
        val selected = try {
            DexClassLoader(revision.apkPath, codeCacheDir.path, null, javaClass.classLoader).loadClass(component.className)
        } catch (_: ClassNotFoundException) { return Act005Result(phases + Act005Phase.FALLBACK_HOST, Act005Reason.MISSING_CLASS, capability) }
        if (!Activity::class.java.isAssignableFrom(selected)) return Act005Result(phases + Act005Phase.FALLBACK_HOST, Act005Reason.NON_ACTIVITY_CLASS, capability)
        phases += Act005Phase.CLASS_SELECTED
        return Act005Result(phases + Act005Phase.ROLLED_BACK_TO_HOST, Act005Reason.NONE, capability, selected.name)
    }

    fun rollbackAgain(result: Act005Result): Act005Result = if (result.hostFallback) result else result.copy(phases = result.phases + Act005Phase.ROLLED_BACK_TO_HOST)

    private fun unavailable(request: Act005Request) = Act005Capability(request.forcedApi ?: Build.VERSION.SDK_INT, "NONE", false, "NOT_CHECKED", "")
    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    companion object { private val launches = ConcurrentHashMap<String, Act005Request>() }
}
