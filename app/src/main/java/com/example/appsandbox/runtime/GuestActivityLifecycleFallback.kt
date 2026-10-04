package com.example.appsandbox.runtime

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import com.example.appsandbox.activity.VirtualActivityLifecycle
import com.example.appsandbox.activity.VirtualActivityManager

/**
 * Apps (routers, plugin and hotfix frameworks) may replace ActivityThread.mInstrumentation with their own
 * without delegating to ours. Their Instrumentation is left as the App made it; for the Activities it
 * attaches, the Guest Application's lifecycle callbacks report to VirtualActivityManager and the first-frame
 * monitor instead. Activities whose Instrumentation still reaches ours are reported by it, not here.
 */
internal class GuestActivityLifecycleFallback(
    private val activityManager: VirtualActivityManager,
    private val instanceId: String,
    private val slot: Int
) : Application.ActivityLifecycleCallbacks {

    private fun bypassed(activity: Activity): Boolean {
        val instrumentation = runCatching {
            Activity::class.java.getDeclaredField("mInstrumentation").apply { isAccessible = true }.get(activity) as? Instrumentation
        }.getOrNull() ?: return false
        return !GuestRuntimeClassLoader.reachesGuestInstrumentation(instrumentation)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (bypassed(activity)) activityManager.created(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // API 28 has no pre-created callback.
        if (Build.VERSION.SDK_INT < 29 && bypassed(activity)) activityManager.created(activity)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        // After onCreate, so touching the decor cannot preempt the App's requestWindowFeature().
        if (bypassed(activity)) GuestFirstFrameMonitor.observe(activity, instanceId, slot)
    }

    override fun onActivityStarted(activity: Activity) {
        if (!bypassed(activity)) return
        if (Build.VERSION.SDK_INT < 29) GuestFirstFrameMonitor.observe(activity, instanceId, slot)
        activityManager.event(activity, VirtualActivityLifecycle.START)
    }

    override fun onActivityResumed(activity: Activity) {
        if (bypassed(activity)) activityManager.event(activity, VirtualActivityLifecycle.RESUME)
    }

    override fun onActivityPaused(activity: Activity) {
        if (bypassed(activity)) activityManager.event(activity, VirtualActivityLifecycle.PAUSE)
    }

    override fun onActivityStopped(activity: Activity) {
        if (bypassed(activity)) activityManager.event(activity, VirtualActivityLifecycle.STOP)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (bypassed(activity)) activityManager.event(activity, VirtualActivityLifecycle.DESTROY)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
