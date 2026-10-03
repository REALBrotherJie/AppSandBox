package com.example.appsandbox.runtime

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.os.Process
import android.util.Log
import com.example.appsandbox.platform.ActivityThreadBridge
import com.example.appsandbox.platform.ClientTransactionBridge
import com.example.appsandbox.platform.PlatformProbe
import com.example.appsandbox.platform.ReceiverPlatformBridge
import com.example.appsandbox.platform.ProviderPlatformBridge
import com.example.appsandbox.virtual.LaunchEnvelope
import com.example.appsandbox.service.VirtualServiceRuntime
import java.lang.reflect.Field
import com.example.appsandbox.receiver.VirtualReceiverManager
import com.example.appsandbox.receiver.BroadcastSessionClient
import com.example.appsandbox.receiver.BroadcastSessionProvider

class ActivityLaunchInterceptor(private val context: android.content.Context) {
    private val activityThreadBridge = ActivityThreadBridge()
    private val transactionBridge = ClientTransactionBridge()
    private val bootstrap = GuestProcessBootstrap(context)

    fun install(): PlatformProbe {
        val handles = activityThreadBridge.resolve().getOrElse { return failure(it) }
        Log.i("AppSandbox.M8", "VRECEIVER_TX probe=${ReceiverPlatformBridge.probe(handles.activityThread)}")
        Log.i("AppSandbox.M8", "VPROVIDER probe=${ProviderPlatformBridge.probe()}")
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
        if (message.what == RECEIVER && message.obj != null) {
            val data = message.obj
            val before = ReceiverPlatformBridge.inspectReceiverData(data)
            val intentField = findField(data.javaClass, "intent").apply { isAccessible = true }
            val infoField = findField(data.javaClass, "info").apply { isAccessible = true }
            val intent = intentField.get(data) as? android.content.Intent
            val physicalInfo = infoField.get(data) as? android.content.pm.ActivityInfo
            val physicalReceiverName = physicalInfo?.name
            val sessionId = intent?.getStringExtra(BroadcastSessionProvider.EXTRA_SESSION_ID)
            val sessionDelivery = if (sessionId != null && physicalReceiverName != null) {
                BroadcastSessionClient.consume(context, sessionId, physicalReceiverName)
            } else null
            if (sessionDelivery != null) {
                runCatching {
                    require(sessionDelivery.slot == processSlot()) { "receiver session slot mismatch expected=${sessionDelivery.slot} actual=${processSlot()}" }
                    val prepared = bootstrap.prepareReceiver(
                        sessionDelivery.packageName,
                        sessionDelivery.instanceId,
                        sessionDelivery.slot,
                        sessionDelivery.intent,
                        sessionDelivery.info,
                        sessionDelivery.sessionId
                    )
                    intentField.set(data, prepared.intent)
                    infoField.set(data, prepared.activityInfo)
                    Log.i("AppSandbox.M8", "VRECEIVER_TX event=SESSION_RESTORE session=${sessionDelivery.sessionId} index=${sessionDelivery.index} ordered=${sessionDelivery.ordered} stub=$physicalReceiverName guest=${prepared.activityInfo.name} before=$before after=${ReceiverPlatformBridge.inspectReceiverData(data)}")
                }.onFailure { Log.e("AppSandbox.M8", "VRECEIVER_TX event=SESSION_RESTORE_FAILED session=${sessionDelivery.sessionId}", it) }
                return
            }
            val deliveryId = intent?.getStringExtra("com.example.appsandbox.receiver.DELIVERY_ID")
            val delivery = deliveryId?.let(VirtualReceiverManager.GLOBAL::consume)
            if (delivery != null) {
                runCatching {
                    val intentField = findField(data.javaClass, "intent")
                    val infoField = findField(data.javaClass, "info")
                    intentField.isAccessible = true
                    infoField.isAccessible = true
                    intentField.set(data, android.content.Intent(delivery.guestIntent).apply {
                        component = android.content.ComponentName(delivery.guestInfo.packageName, delivery.guestInfo.name)
                        setExtrasClassLoader(Thread.currentThread().contextClassLoader)
                    })
                    infoField.set(data, android.content.pm.ActivityInfo(delivery.guestInfo))
                    Log.i("AppSandbox.M8", "VRECEIVER_TX event=RESTORE delivery=${delivery.id} guest=${android.content.ComponentName(delivery.guestInfo.packageName, delivery.guestInfo.name).flattenToShortString()} before=$before after=${ReceiverPlatformBridge.inspectReceiverData(data)}")
                }.onFailure { Log.e("AppSandbox.M8", "VRECEIVER_TX event=RESTORE_FAILED delivery=${delivery.id}", it) }
            } else {
                Log.i("AppSandbox.M8", "VRECEIVER_TX event=H_RECEIVER object=${data.javaClass.name} delivery=$deliveryId fields=$before")
            }
        }
        if (VirtualServiceRuntime.restore(message, context)) return
        if (message.what != EXECUTE_TRANSACTION || message.obj == null) return
        runCatching {
            transactionBridge.findNewIntentRecords(message.obj).forEach { record ->
                val count = record.restoreGuestIntents()
                if (count > 0) Log.i("AppSandbox.M4", "VACTIVITY new-intent restored count=$count api=${android.os.Build.VERSION.SDK_INT}")
            }
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

    private fun observeFirstFrame(activity: Activity, envelope: LaunchEnvelope) =
        GuestFirstFrameMonitor.observe(activity, envelope.instanceId, envelope.processSlot)

    private fun findField(type: Class<*>, name: String): Field =
        generateSequence(type) { it.superclass }.mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }.first()

    private fun processSlot(): Int = Regex(":p(\\d+)$").find(runCatching {
        java.io.File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
    }.getOrDefault(android.app.Application.getProcessName()))
        ?.groupValues?.get(1)?.toInt() ?: -1

    private fun failure(error: Throwable) = PlatformProbe(
        supported = false,
        failureReason = error.let { root -> "${root.javaClass.name}: ${root.message}" }
    )

    companion object {
        private const val TAG = "AppSandbox.M2"
        private const val EXECUTE_TRANSACTION = 159
        private const val RECEIVER = 113
    }
}
