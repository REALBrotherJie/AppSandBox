package com.example.appsandbox.stub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.appsandbox.runtime.ActivityLaunchInterceptor

/** Physical shell only. Guest receiver creation remains an ActivityThread/framework concern. */
abstract class BaseStubReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val probe = ActivityLaunchInterceptor(context.applicationContext).install()
        Log.i("AppSandbox.M8", "VRECEIVER event=SCHEDULE stub=${javaClass.name} action=${intent.action} probe=$probe")
    }
}

class P0Receiver : BaseStubReceiver(); class P1Receiver : BaseStubReceiver(); class P2Receiver : BaseStubReceiver()
class P3Receiver : BaseStubReceiver(); class P4Receiver : BaseStubReceiver(); class P5Receiver : BaseStubReceiver()
class P6Receiver : BaseStubReceiver(); class P7Receiver : BaseStubReceiver(); class P8Receiver : BaseStubReceiver()

object StubReceivers {
    private val classes = arrayOf(P0Receiver::class.java, P1Receiver::class.java, P2Receiver::class.java,
        P3Receiver::class.java, P4Receiver::class.java, P5Receiver::class.java,
        P6Receiver::class.java, P7Receiver::class.java, P8Receiver::class.java)
    fun component(slot: Int) = classes.getOrElse(slot) { error("stub receiver p$slot is out of range") }
}
