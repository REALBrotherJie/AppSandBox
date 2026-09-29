package com.example.appsandbox.automation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.appsandbox.GuestActivityCarrierActivity
import java.lang.ref.WeakReference

class Task57ProbeApplication : Application(), Application.ActivityLifecycleCallbacks {
    var carrier = WeakReference<GuestActivityCarrierActivity>(null)
        private set
    var creations = 0
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity is GuestActivityCarrierActivity) {
            carrier = WeakReference(activity)
            creations++
        }
    }
    override fun onActivityDestroyed(activity: Activity) {
        if (carrier.get() === activity) carrier.clear()
    }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
