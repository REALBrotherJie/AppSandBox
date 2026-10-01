package com.example.appsandbox.stub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
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

class P0ReceiverN2R0 : BaseStubReceiver(); class P0ReceiverN2R1 : BaseStubReceiver()
class P1ReceiverN2R0 : BaseStubReceiver(); class P1ReceiverN2R1 : BaseStubReceiver()
class P2ReceiverN2R0 : BaseStubReceiver(); class P2ReceiverN2R1 : BaseStubReceiver()
class P3ReceiverN2R0 : BaseStubReceiver(); class P3ReceiverN2R1 : BaseStubReceiver()
class P4ReceiverN2R0 : BaseStubReceiver(); class P4ReceiverN2R1 : BaseStubReceiver()
class P5ReceiverN2R0 : BaseStubReceiver(); class P5ReceiverN2R1 : BaseStubReceiver()
class P6ReceiverN2R0 : BaseStubReceiver(); class P6ReceiverN2R1 : BaseStubReceiver()
class P7ReceiverN2R0 : BaseStubReceiver(); class P7ReceiverN2R1 : BaseStubReceiver()
class P8ReceiverN2R0 : BaseStubReceiver(); class P8ReceiverN2R1 : BaseStubReceiver()

object StubReceivers {
    const val MAX_RANKS = 2
    private val classes = arrayOf(P0Receiver::class.java, P1Receiver::class.java, P2Receiver::class.java,
        P3Receiver::class.java, P4Receiver::class.java, P5Receiver::class.java,
        P6Receiver::class.java, P7Receiver::class.java, P8Receiver::class.java)
    fun component(slot: Int) = classes.getOrElse(slot) { error("stub receiver p$slot is out of range") }
    fun intent(context: Context, slot: Int) = Intent(context, component(slot))
    private val orderedTwo = arrayOf(
        arrayOf(P0ReceiverN2R0::class.java, P0ReceiverN2R1::class.java),
        arrayOf(P1ReceiverN2R0::class.java, P1ReceiverN2R1::class.java),
        arrayOf(P2ReceiverN2R0::class.java, P2ReceiverN2R1::class.java),
        arrayOf(P3ReceiverN2R0::class.java, P3ReceiverN2R1::class.java),
        arrayOf(P4ReceiverN2R0::class.java, P4ReceiverN2R1::class.java),
        arrayOf(P5ReceiverN2R0::class.java, P5ReceiverN2R1::class.java),
        arrayOf(P6ReceiverN2R0::class.java, P6ReceiverN2R1::class.java),
        arrayOf(P7ReceiverN2R0::class.java, P7ReceiverN2R1::class.java),
        arrayOf(P8ReceiverN2R0::class.java, P8ReceiverN2R1::class.java)
    )
    fun component(slot: Int, count: Int, index: Int): ComponentName = ComponentName(
        "com.example.appsandbox",
        when (count) {
            1 -> component(slot)
            2 -> orderedTwo.getOrElse(slot) { error("stub receiver p$slot is out of range") }
                .getOrElse(index) { error("stub receiver rank $index is out of range") }
            else -> error("unsupported receiver count $count")
        }.name
    )
    fun action(slot: Int, count: Int) = "com.example.appsandbox.receiver.ORDERED_P${slot}_N$count"
}
