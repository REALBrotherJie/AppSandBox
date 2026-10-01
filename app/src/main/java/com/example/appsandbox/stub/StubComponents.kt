package com.example.appsandbox.stub

import android.app.Activity
import android.app.Service
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.example.appsandbox.runtime.ActivityLaunchInterceptor

abstract class BaseStubActivity : Activity()
abstract class BaseStubService : Service() { override fun onBind(intent: Intent?): IBinder? = null }
abstract class BaseStubProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        com.example.appsandbox.runtime.NativeRuntimeBridge.preload()
        val appContext = requireNotNull(context).applicationContext
        val result = ActivityLaunchInterceptor(appContext).install()
        Log.i("AppSandbox.M2", "stub-provider process=${android.app.Application.getProcessName()} probe=$result")
        return result.supported
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

class P0StandardActivity : BaseStubActivity(); class P0SingleTopActivity : BaseStubActivity(); class P0SingleTaskActivity : BaseStubActivity(); class P0SingleInstanceActivity : BaseStubActivity(); class P0TranslucentActivity : BaseStubActivity(); class P0DialogActivity : BaseStubActivity(); class P0Service : BaseStubService(); class P0Provider : BaseStubProvider()
class P1StandardActivity : BaseStubActivity(); class P1SingleTopActivity : BaseStubActivity(); class P1SingleTaskActivity : BaseStubActivity(); class P1SingleInstanceActivity : BaseStubActivity(); class P1TranslucentActivity : BaseStubActivity(); class P1DialogActivity : BaseStubActivity(); class P1Service : BaseStubService(); class P1Provider : BaseStubProvider()
class P2StandardActivity : BaseStubActivity(); class P2SingleTopActivity : BaseStubActivity(); class P2SingleTaskActivity : BaseStubActivity(); class P2SingleInstanceActivity : BaseStubActivity(); class P2TranslucentActivity : BaseStubActivity(); class P2DialogActivity : BaseStubActivity(); class P2Service : BaseStubService(); class P2Provider : BaseStubProvider()
class P3StandardActivity : BaseStubActivity(); class P3SingleTopActivity : BaseStubActivity(); class P3SingleTaskActivity : BaseStubActivity(); class P3SingleInstanceActivity : BaseStubActivity(); class P3TranslucentActivity : BaseStubActivity(); class P3DialogActivity : BaseStubActivity(); class P3Service : BaseStubService(); class P3Provider : BaseStubProvider()
class P4StandardActivity : BaseStubActivity(); class P4SingleTopActivity : BaseStubActivity(); class P4SingleTaskActivity : BaseStubActivity(); class P4SingleInstanceActivity : BaseStubActivity(); class P4TranslucentActivity : BaseStubActivity(); class P4DialogActivity : BaseStubActivity(); class P4Service : BaseStubService(); class P4Provider : BaseStubProvider()
class P5StandardActivity : BaseStubActivity(); class P5SingleTopActivity : BaseStubActivity(); class P5SingleTaskActivity : BaseStubActivity(); class P5SingleInstanceActivity : BaseStubActivity(); class P5TranslucentActivity : BaseStubActivity(); class P5DialogActivity : BaseStubActivity(); class P5Service : BaseStubService(); class P5Provider : BaseStubProvider()
class P6StandardActivity : BaseStubActivity(); class P6SingleTopActivity : BaseStubActivity(); class P6SingleTaskActivity : BaseStubActivity(); class P6SingleInstanceActivity : BaseStubActivity(); class P6TranslucentActivity : BaseStubActivity(); class P6DialogActivity : BaseStubActivity(); class P6Service : BaseStubService(); class P6Provider : BaseStubProvider()
class P7StandardActivity : BaseStubActivity(); class P7SingleTopActivity : BaseStubActivity(); class P7SingleTaskActivity : BaseStubActivity(); class P7SingleInstanceActivity : BaseStubActivity(); class P7TranslucentActivity : BaseStubActivity(); class P7DialogActivity : BaseStubActivity(); class P7Service : BaseStubService(); class P7Provider : BaseStubProvider()
class P8StandardActivity : BaseStubActivity(); class P8SingleTopActivity : BaseStubActivity(); class P8SingleTaskActivity : BaseStubActivity(); class P8SingleInstanceActivity : BaseStubActivity(); class P8TranslucentActivity : BaseStubActivity(); class P8DialogActivity : BaseStubActivity(); class P8Service : BaseStubService(); class P8Provider : BaseStubProvider()

class P0Service1 : BaseStubService(); class P0Service2 : BaseStubService(); class P0Service3 : BaseStubService()
class P1Service1 : BaseStubService(); class P1Service2 : BaseStubService(); class P1Service3 : BaseStubService()
class P2Service1 : BaseStubService(); class P2Service2 : BaseStubService(); class P2Service3 : BaseStubService()
class P3Service1 : BaseStubService(); class P3Service2 : BaseStubService(); class P3Service3 : BaseStubService()
class P4Service1 : BaseStubService(); class P4Service2 : BaseStubService(); class P4Service3 : BaseStubService()
class P5Service1 : BaseStubService(); class P5Service2 : BaseStubService(); class P5Service3 : BaseStubService()
class P6Service1 : BaseStubService(); class P6Service2 : BaseStubService(); class P6Service3 : BaseStubService()
class P7Service1 : BaseStubService(); class P7Service2 : BaseStubService(); class P7Service3 : BaseStubService()
class P8Service1 : BaseStubService(); class P8Service2 : BaseStubService(); class P8Service3 : BaseStubService()

object StubServices {
    const val SLOT_COUNT = 9
    private val classes = arrayOf(P0Service::class.java, P1Service::class.java, P2Service::class.java, P3Service::class.java, P4Service::class.java, P5Service::class.java, P6Service::class.java, P7Service::class.java, P8Service::class.java)
    private val pools = arrayOf(
        arrayOf(P0Service::class.java, P0Service1::class.java, P0Service2::class.java, P0Service3::class.java),
        arrayOf(P1Service::class.java, P1Service1::class.java, P1Service2::class.java, P1Service3::class.java),
        arrayOf(P2Service::class.java, P2Service1::class.java, P2Service2::class.java, P2Service3::class.java),
        arrayOf(P3Service::class.java, P3Service1::class.java, P3Service2::class.java, P3Service3::class.java),
        arrayOf(P4Service::class.java, P4Service1::class.java, P4Service2::class.java, P4Service3::class.java),
        arrayOf(P5Service::class.java, P5Service1::class.java, P5Service2::class.java, P5Service3::class.java),
        arrayOf(P6Service::class.java, P6Service1::class.java, P6Service2::class.java, P6Service3::class.java),
        arrayOf(P7Service::class.java, P7Service1::class.java, P7Service2::class.java, P7Service3::class.java),
        arrayOf(P8Service::class.java, P8Service1::class.java, P8Service2::class.java, P8Service3::class.java)
    )
    fun intent(context: Context, slot: Int) = Intent(context, classes.getOrElse(slot) { error("stub slot p$slot is out of range") })
    fun intent(context: Context, slot: Int, serviceIndex: Int) = Intent(context,
        pools.getOrElse(slot) { error("stub slot p$slot is out of range") }.getOrElse(serviceIndex) { error("stub service $serviceIndex is out of range") })
}

object StubActivities {
    private val standard = arrayOf(P0StandardActivity::class.java, P1StandardActivity::class.java, P2StandardActivity::class.java,
        P3StandardActivity::class.java, P4StandardActivity::class.java, P5StandardActivity::class.java,
        P6StandardActivity::class.java, P7StandardActivity::class.java, P8StandardActivity::class.java)
    private val singleTop = arrayOf(P0SingleTopActivity::class.java, P1SingleTopActivity::class.java, P2SingleTopActivity::class.java,
        P3SingleTopActivity::class.java, P4SingleTopActivity::class.java, P5SingleTopActivity::class.java,
        P6SingleTopActivity::class.java, P7SingleTopActivity::class.java, P8SingleTopActivity::class.java)
    private val singleTask = arrayOf(P0SingleTaskActivity::class.java, P1SingleTaskActivity::class.java, P2SingleTaskActivity::class.java,
        P3SingleTaskActivity::class.java, P4SingleTaskActivity::class.java, P5SingleTaskActivity::class.java,
        P6SingleTaskActivity::class.java, P7SingleTaskActivity::class.java, P8SingleTaskActivity::class.java)
    fun standardIntent(context: Context, slot: Int) = Intent(context, standard.getOrElse(slot) { error("stub slot p$slot is out of range") })
    fun intent(context: Context, slot: Int, launchMode: Int) = Intent(context, when (launchMode) {
        android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP -> singleTop
        android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK -> singleTask
        else -> standard
    }.getOrElse(slot) { error("stub slot p$slot is out of range") })
}
