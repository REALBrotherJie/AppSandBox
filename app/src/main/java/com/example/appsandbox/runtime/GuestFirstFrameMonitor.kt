package com.example.appsandbox.runtime

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import java.util.Collections
import java.util.WeakHashMap

/** Logs the first drawn frame of each Guest Activity once, whichever launch path created it. */
internal object GuestFirstFrameMonitor {
    private const val TAG = "AppSandbox.M2"
    private val observed: MutableSet<Activity> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    fun observe(activity: Activity, instanceId: String, slot: Int) {
        if (!observed.add(activity)) return
        val decor = activity.window?.decorView ?: return
        decor.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var recorded = false

            override fun onDraw() {
                if (recorded) return
                recorded = true
                decor.post {
                    if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(this)
                    val viewRoot = runCatching {
                        View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }.invoke(decor)
                    }.getOrNull()
                    Log.i(TAG, "first-frame timestamp=${android.os.SystemClock.elapsedRealtime()} actual=${activity.javaClass.name} " +
                        "window=${activity.window.javaClass.name} decor=${decor.javaClass.name} viewRoot=${viewRoot?.javaClass?.name} " +
                        "instance=$instanceId slot=$slot")
                }
            }
        })
    }
}
