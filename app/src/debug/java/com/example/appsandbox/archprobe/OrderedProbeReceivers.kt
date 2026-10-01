package com.example.appsandbox.archprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

private const val TAG = "AppSandbox.ArchProbe"

class OrderedProbeReceiverA : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getStringExtra("mode") ?: "result"
        Log.i(TAG, "A_ENTER mode=$mode ordered=$isOrderedBroadcast code=$resultCode data=$resultData pending=${pendingSnapshot(this)} t=${SystemClock.elapsedRealtime()}")
        when (mode) {
            "abort" -> {
                setResultCode(42)
                setResultData("set-by-A")
                abortBroadcast()
                Log.i(TAG, "A_ABORT t=${SystemClock.elapsedRealtime()}")
            }
            "async" -> {
                val pending = goAsync()
                Log.i(TAG, "A_GOASYNC_RETURN t=${SystemClock.elapsedRealtime()} pending=${System.identityHashCode(pending)}")
                Thread {
                    Thread.sleep(800)
                    pending.setResultCode(42)
                    pending.setResultData("set-by-A-async")
                    pending.setResultExtras(Bundle().apply { putString("source", "A-async") })
                    Log.i(TAG, "A_ASYNC_FINISH t=${SystemClock.elapsedRealtime()} pending=${System.identityHashCode(pending)}")
                    pending.finish()
                }.start()
            }
            else -> {
                setResultCode(42)
                setResultData("set-by-A")
                setResultExtras(Bundle().apply { putString("source", "A") })
                Log.i(TAG, "A_RESULT_SET t=${SystemClock.elapsedRealtime()}")
            }
        }
    }
}

class OrderedProbeReceiverB : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "B_ENTER mode=${intent.getStringExtra("mode")} ordered=$isOrderedBroadcast code=$resultCode data=$resultData source=${getResultExtras(false)?.getString("source")} pending=${pendingSnapshot(this)} t=${SystemClock.elapsedRealtime()}")
        setResultCode(43)
        setResultData("final-by-B")
    }
}

private fun pendingSnapshot(receiver: BroadcastReceiver): String = runCatching {
    val pendingField = BroadcastReceiver::class.java.getDeclaredField("mPendingResult").apply { isAccessible = true }
    val pending = pendingField.get(receiver) ?: return@runCatching "null"
    val values = listOf("mToken", "mType", "mOrderedHint", "mResultCode", "mResultData", "mAbortBroadcast", "mFinished")
        .associateWith { name ->
            runCatching {
                generateSequence<Class<*>>(pending.javaClass) { it.superclass }
                    .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
                    .first().apply { isAccessible = true }.get(pending)
            }.getOrNull()
        }
    "id=${System.identityHashCode(pending)} $values"
}.getOrElse { "error=${it.javaClass.simpleName}" }
