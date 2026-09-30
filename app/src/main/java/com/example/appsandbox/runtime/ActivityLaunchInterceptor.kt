package com.example.appsandbox.runtime

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.os.Process
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.example.appsandbox.platform.ActivityThreadBridge
import com.example.appsandbox.platform.ClientTransactionBridge
import com.example.appsandbox.platform.PlatformProbe
import com.example.appsandbox.virtual.LaunchEnvelope
import java.lang.reflect.Field

class ActivityLaunchInterceptor(private val context: android.content.Context) {
    private val activityThreadBridge = ActivityThreadBridge()
    private val transactionBridge = ClientTransactionBridge()
    private val bootstrap = GuestProcessBootstrap(context)

    fun install(): PlatformProbe {
        val handles = activityThreadBridge.resolve().getOrElse { return failure(it) }
        val transactionProbe = transactionBridge.probe()
        if (!transactionProbe.supported) return transactionProbe
        return runCatching {
            val callbackField = Handler::class.java.getDeclaredField("mCallback").apply { isAccessible = true }
            val previous = callbackField.get(handles.handler) as? Handler.Callback
            callbackField.set(handles.handler, Handler.Callback { message ->
                intercept(message, handles.handler, handles.activityThread)
                previous?.handleMessage(message) ?: false
            })
            Log.i(TAG, "interceptor-installed api=${android.os.Build.VERSION.SDK_INT} pid=${Process.myPid()} members=${transactionProbe.resolvedMembers}")
            PlatformProbe(supported = true, resolvedMembers = activityThreadBridge.probe().resolvedMembers + transactionProbe.resolvedMembers)
        }.getOrElse(::failure)
    }

    private fun intercept(message: Message, handler: Handler, activityThread: Any) {
        if (message.what != EXECUTE_TRANSACTION || message.obj == null) return
        runCatching {
            transactionBridge.findLaunchRecords(message.obj).forEach { record ->
                val envelope = LaunchEnvelope.from(record.intent) ?: return@forEach
                val prepared = bootstrap.prepare(envelope)
                record.replace(prepared.intent, prepared.activityInfo)
                Log.i(TAG, "restore original=${envelope.target.flattenToShortString()} stub=${record.intent.component?.flattenToShortString()} " +
                    "restored=${prepared.intent.component?.flattenToShortString()} instance=${envelope.instanceId} slot=${envelope.processSlot} " +
                    "flags=0x${prepared.intent.flags.toString(16)} launchMode=${prepared.activityInfo.launchMode}")
                handler.post { logLaunchedActivity(activityThread, envelope) }
            }
        }.onFailure { Log.e(TAG, "launch interception failed api=${android.os.Build.VERSION.SDK_INT}", it) }
    }

    private fun logLaunchedActivity(activityThread: Any, envelope: LaunchEnvelope) {
        runCatching {
            val activitiesField = findField(activityThread.javaClass, "mActivities").apply { isAccessible = true }
            val activities = activitiesField.get(activityThread) as? Map<*, *> ?: return
            val activity = activities.values.asSequence().mapNotNull { record ->
                if (record == null) return@mapNotNull null
                val field = findField(record.javaClass, "activity").apply { isAccessible = true }
                field.get(record) as? Activity
            }.firstOrNull { it.javaClass.name == envelope.target.className } ?: return
            Log.i(TAG, "launched actual=${activity.javaClass.name} base=${activity.baseContext.javaClass.name} " +
                "application=${activity.application.javaClass.name} loader=${activity.classLoader} resources=${activity.resources.javaClass.name} " +
                "window=${activity.window.javaClass.name} decor=${activity.window.decorView.javaClass.name} instance=${envelope.instanceId}")
            observeFirstFrame(activity, envelope)
        }.onFailure { Log.e(TAG, "post-launch observation failed", it) }
    }

    private fun observeFirstFrame(activity: Activity, envelope: LaunchEnvelope) {
        val decor = activity.window.decorView
        val observer = decor.viewTreeObserver
        observer.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
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
                        "instance=${envelope.instanceId} slot=${envelope.processSlot}")
                }
            }
        })
    }

    private fun findField(type: Class<*>, name: String): Field =
        generateSequence(type) { it.superclass }.mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }.first()

    private fun failure(error: Throwable) = PlatformProbe(
        supported = false,
        failureReason = error.let { root -> "${root.javaClass.name}: ${root.message}" }
    )

    companion object {
        private const val TAG = "AppSandbox.M2"
        private const val EXECUTE_TRANSACTION = 159
    }
}
