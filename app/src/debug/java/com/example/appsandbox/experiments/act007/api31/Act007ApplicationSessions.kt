package com.example.appsandbox.experiments.act007.api31

import android.app.Instrumentation
import android.content.Context
import com.example.appsandbox.experiments.exp003c1.C1Experiment
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.util.concurrent.ConcurrentHashMap

data class Act007SessionResult(val outcome: String, val reason: String, val instanceId: String,
    val constructed: Int = 0, val onCreateAttempted: Int = 0, val onCreateCompleted: Int = 0,
    val loader: String = "none", val dataRoot: String = "none")

object Act007ApplicationSessions {
    private val sessions = ConcurrentHashMap<String, Any>()

    fun start(context: Context, instanceId: String, throwing: Boolean = false): Act007SessionResult {
        if (sessions.containsKey(instanceId)) return Act007SessionResult("REJECTED", "ALREADY_RUNNING", instanceId)
        val instance = GuestInstanceStore(context).get(instanceId) ?: return Act007SessionResult("REJECTED", "MISSING_INSTANCE", instanceId)
        val record = GuestStore(context).findRevision(instance.guestRevisionId) ?: return Act007SessionResult("REJECTED", "MISSING_REVISION", instanceId)
        val setup = try { C1Experiment.create(context, record, instanceId) } catch (error: Throwable) {
            return Act007SessionResult("REJECTED", "CONSTRUCTION_FAILED:${error.javaClass.simpleName}", instanceId)
        }
        val app = if (!throwing) setup.app else try {
            Instrumentation().newApplication(setup.loader, "com.example.appsandbox.testguest.runtime.Exp003OnCreateThrowingApplication", setup.context)
        } catch (error: Throwable) { return Act007SessionResult("REJECTED", "CONSTRUCTION_FAILED:${error.javaClass.simpleName}", instanceId, constructed = 1) }
        return try {
            Instrumentation().callApplicationOnCreate(app)
            sessions[instanceId] = app
            Act007SessionResult("RUNNING", "NONE", instanceId, 1, 1, 1, app.javaClass.classLoader!!.javaClass.name, instance.dataRoot)
        } catch (error: Throwable) {
            Act007SessionResult("REJECTED", "ON_CREATE_FAILED:${error.javaClass.simpleName}", instanceId, 1, 1, 0,
                app.javaClass.classLoader!!.javaClass.name, instance.dataRoot)
        }
    }

    fun stop(instanceId: String) = if (sessions.remove(instanceId) != null)
        Act007SessionResult("STOPPED", "NONE", instanceId) else Act007SessionResult("REJECTED", "NOT_RUNNING", instanceId)
    fun status(instanceId: String) = if (sessions.containsKey(instanceId)) "RUNNING" else "STOPPED"
}
