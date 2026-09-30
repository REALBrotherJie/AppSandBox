package com.example.appsandbox.activity

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import com.example.appsandbox.virtual.LaunchEnvelope
import java.util.UUID
import java.lang.reflect.InvocationTargetException

enum class VirtualActivityLifecycle { REQUESTED, CREATE, START, RESUME, PAUSE, STOP, FINISH, DESTROY }

data class VirtualActivityRecord(
    val recordId: String,
    val launchRunId: String,
    val virtualTaskId: String,
    val instanceId: String,
    val packageName: String,
    val componentName: ComponentName,
    val stubComponent: ComponentName,
    val processSlot: Int,
    val launchMode: Int,
    val taskAffinity: String?,
    var intent: Intent,
    val callerToken: String?,
    val requestCode: Int,
    var runtimeToken: String? = null,
    var lifecycle: VirtualActivityLifecycle = VirtualActivityLifecycle.REQUESTED
)

data class VirtualTask(
    val taskId: String,
    val instanceId: String,
    val affinity: String?,
    val stack: MutableList<VirtualActivityRecord> = mutableListOf(),
    var foreground: Boolean = true
)

class VirtualActivityManager {
    private val tasks = linkedMapOf<String, VirtualTask>()
    private val activityRecords = mutableMapOf<Int, VirtualActivityRecord>()
    private val activities = mutableMapOf<String, Activity>()
    private val pending = ArrayDeque<VirtualActivityRecord>()

    @Synchronized fun requested(envelope: LaunchEnvelope, stub: ComponentName, callerToken: String?, requestCode: Int): VirtualActivityRecord {
        tasks.values.flatMap { it.stack }.firstOrNull { it.launchRunId == envelope.runId }?.let { return it }
        val taskId = "${envelope.instanceId}:${envelope.activityInfo.taskAffinity ?: envelope.packageName}"
        val record = VirtualActivityRecord(UUID.randomUUID().toString(), envelope.runId, taskId, envelope.instanceId, envelope.packageName,
            envelope.target, stub, envelope.processSlot, envelope.activityInfo.launchMode, envelope.activityInfo.taskAffinity,
            Intent(envelope.originalIntent), callerToken, requestCode)
        tasks.getOrPut(taskId) { VirtualTask(taskId, envelope.instanceId, envelope.activityInfo.taskAffinity) }.stack += record
        pending += record
        log(record, "REQUEST")
        return record
    }

    @Synchronized fun created(activity: Activity): VirtualActivityRecord? {
        val record = pending.indexOfLast { it.componentName.className == activity.javaClass.name }.takeIf { it >= 0 }
            ?.let { pending.removeAt(it) } ?: return null
        record.runtimeToken = token(activity)
        activityRecords[System.identityHashCode(activity)] = record
        activities[record.recordId] = activity
        event(activity, VirtualActivityLifecycle.CREATE)
        return record
    }

    @Synchronized fun event(activity: Activity, state: VirtualActivityLifecycle) {
        val record = activityRecords[System.identityHashCode(activity)] ?: return
        record.lifecycle = state
        if (state == VirtualActivityLifecycle.DESTROY) {
            tasks[record.virtualTaskId]?.stack?.remove(record)
            activityRecords.remove(System.identityHashCode(activity))
            activities.remove(record.recordId)
        }
        log(record, state.name)
    }

    @Synchronized fun newIntent(activity: Activity, intent: Intent) {
        val record = activityRecords[System.identityHashCode(activity)] ?: return
        record.intent = Intent(intent)
        val superseded = pending.filter { it.componentName == record.componentName }.toSet()
        pending.removeAll(superseded)
        tasks.values.forEach { it.stack.removeAll(superseded) }
        log(record, "NEW_INTENT flags=0x${intent.flags.toString(16)} intent=${intent.toUri(0)}")
    }

    @Synchronized fun deliverToReusable(envelope: LaunchEnvelope): Boolean {
        val taskId = "${envelope.instanceId}:${envelope.activityInfo.taskAffinity ?: envelope.packageName}"
        val stack = tasks[taskId]?.stack ?: return false
        val targetIndex = stack.indexOfLast { it.componentName == envelope.target && activities.containsKey(it.recordId) }
        if (targetIndex < 0) return false
        val isTop = targetIndex == stack.lastIndex
        val reusable = (isTop && (envelope.activityInfo.launchMode == android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP ||
            envelope.originalIntent.hasFlag(Intent.FLAG_ACTIVITY_SINGLE_TOP))) ||
            envelope.activityInfo.launchMode == android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK ||
            envelope.originalIntent.hasFlag(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!reusable) return false
        val targetRecord = stack[targetIndex]
        val target = activities[targetRecord.recordId] ?: return false
        val above = stack.drop(targetIndex + 1).mapNotNull { activities[it.recordId] }.asReversed()
        val delivered = Intent(envelope.originalIntent).apply { setExtrasClassLoader(target.classLoader) }
        target.window.decorView.post {
            finishSequentially(above, target) {
                target.intent = delivered
                runCatching {
                    val callback = findOnNewIntent(target.javaClass).apply { isAccessible = true }
                    callback.invoke(target, delivered)
                }.onFailure { error ->
                    val cause = (error as? InvocationTargetException)?.targetException ?: error
                    Log.e(TAG, "VACTIVITY local NEW_INTENT delivery failed component=${envelope.target}", cause)
                }
                newIntent(target, delivered)
            }
        }
        Log.i(TAG, "VACTIVITY_DECISION instance=${envelope.instanceId} component=${envelope.target.flattenToShortString()} " +
            "decision=REUSE clearAbove=${above.size} flags=0x${envelope.originalIntent.flags.toString(16)} launchMode=${envelope.activityInfo.launchMode}")
        return true
    }

    @Synchronized fun snapshot(): List<VirtualTask> = tasks.values.map { it.copy(stack = it.stack.toMutableList()) }

    private fun findOnNewIntent(start: Class<*>): java.lang.reflect.Method {
        var type: Class<*>? = start
        while (type != null) {
            runCatching { type.getDeclaredMethod("onNewIntent", Intent::class.java) }.getOrNull()?.let { return it }
            type = type.superclass
        }
        error("Activity.onNewIntent not found for ${start.name}")
    }

    private fun finishSequentially(above: List<Activity>, target: Activity, done: () -> Unit) {
        val next = above.firstOrNull()
        if (next == null) {
            target.window.decorView.postDelayed(done, 50)
            return
        }
        next.finish()
        target.window.decorView.postDelayed({ finishSequentially(above.drop(1), target, done) }, 100)
    }

    private fun token(activity: Activity): String? = runCatching {
        Activity::class.java.getDeclaredField("mToken").apply { isAccessible = true }.get(activity)?.toString()
    }.getOrNull()

    private fun log(record: VirtualActivityRecord, event: String) {
        Log.i(TAG, "VACTIVITY instance=${record.instanceId} virtualTask=${record.virtualTaskId} record=${record.recordId} " +
            "component=${record.componentName.flattenToShortString()} stub=${record.stubComponent.flattenToShortString()} " +
            "token=${record.runtimeToken} process=p${record.processSlot} event=$event caller=${record.callerToken} requestCode=${record.requestCode}")
    }

    companion object { private const val TAG = "AppSandbox.M4" }
}

private fun Intent.hasFlag(flag: Int): Boolean = flags and flag != 0
