package com.example.appsandbox.experiments.act007.api36

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.appsandbox.experiments.exp003c1.Exp003c1ControlledContext
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.GuestArtifactVerifier
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class Act007SessionResult(val outcome: String, val constructor: Int, val onCreate: Int, val loader: String, val error: String = "")

object Act007ApplicationSessions {
    private data class Active(val app: Application, val loader: DexClassLoader)
    private val active = ConcurrentHashMap<String, Active>()

    fun start(host: Context, instance: GuestInstanceRecord, revision: GuestPackageRecord, className: String? = null): Act007SessionResult {
        require(android.os.Build.VERSION.SDK_INT == 36) { "API36 required" }
        require(GuestArtifactVerifier.sha256(File(revision.apkPath)).equals(revision.sha256, true)) { "artifact SHA mismatch" }
        require(instance.guestRevisionId == revision.revisionId && instance.guestSha256 == revision.sha256) { "instance binding mismatch" }
        check(active[instance.instanceId] == null) { "session already active" }
        val apk = File(revision.apkPath)
        val loader = DexClassLoader(apk.path, host.codeCacheDir.path, null, host.classLoader)
        @Suppress("DEPRECATION")
        val info = ApplicationInfo(requireNotNull(host.packageManager.getPackageArchiveInfo(apk.path, 0)?.applicationInfo)).apply {
            sourceDir = apk.path; publicSourceDir = apk.path; dataDir = instance.dataRoot
        }
        val context = Exp003c1ControlledContext(host.applicationContext, loader,
            host.packageManager.getResourcesForApplication(info), info, File(instance.dataRoot), instance.instanceId)
        val target = className ?: requireNotNull(info.className)
        var constructed = 0
        return try {
            val app = Instrumentation().newApplication(loader, target, context).also { constructed = 1; context.bindApplication(it) }
            Instrumentation().callApplicationOnCreate(app)
            active[instance.instanceId] = Active(app, loader)
            state(instance).writeText("status=RUNNING\nclass=$target\nloader=${app.javaClass.classLoader.javaClass.name}\nonCreate=1\n")
            Act007SessionResult("RUNNING", constructed, 1, app.javaClass.classLoader.javaClass.name)
        } catch (t: Throwable) {
            state(instance).writeText("status=FAILED\nclass=$target\nconstructor=$constructed\nonCreateAttempted=1\nerror=${root(t)}\n")
            Act007SessionResult("FAILED", constructed, 1, loader.javaClass.name, root(t))
        }
    }

    fun stop(instance: GuestInstanceRecord): Boolean {
        val removed = active.remove(instance.instanceId) != null
        state(instance).writeText("status=STOPPED\n")
        return removed
    }

    fun isActive(instanceId: String) = active.containsKey(instanceId)
    private fun state(instance: GuestInstanceRecord) = File(instance.dataRoot, "files/application-session.state").apply { parentFile!!.mkdirs() }
    private fun root(t: Throwable): String { var x=t; while(x.cause != null)x=x.cause!!; return x.javaClass.name + ":" + (x.message ?: "") }
}
