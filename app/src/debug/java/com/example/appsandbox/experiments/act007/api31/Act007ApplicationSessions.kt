package com.example.appsandbox.experiments.act007.api31

import android.content.Context
import com.example.appsandbox.experiments.act007.core.*
import com.example.appsandbox.storage.GuestInstanceStore
import com.example.appsandbox.storage.GuestStore
import java.io.File

data class Act007SessionResult(val outcome:String,val reason:String,val instanceId:String,val runId:String="",val detail:String="")
object Act007ApplicationSessions {
 private fun c(x:Context)=GuestApplicationSessionController(GuestApplicationSessionRegistry(File(x.filesDir,"act007/application-sessions.json")))
 fun start(x:Context,i:String,r:String,o:String):Act007SessionResult=try{val a=requireNotNull(GuestInstanceStore(x).get(i));val g=requireNotNull(GuestStore(x).findRevision(a.guestRevisionId));val n=requireNotNull(x.packageManager.getPackageArchiveInfo(g.apkPath,0)?.applicationInfo?.className);val q=GuestApplicationSessionRequest(r,o,a.instanceId,a.guestRevisionId,a.guestSha256,a.guestPackageName,n,a.dataRoot);val e=GuestApplicationSessionExpected(a.instanceId,a.guestRevisionId,a.guestSha256,a.guestPackageName,n,a.dataRoot,File(x.filesDir,"guest-instances").canonicalPath);val s=c(x).start(q,e,C1GuestApplicationSessionExecutor.create(x,a,g,n));Act007SessionResult(s.state.name,s.failure.name,i,r,s.detail.orEmpty())}catch(t:Throwable){Act007SessionResult("FAILED","INTERNAL",i,r,t.message.orEmpty())}
 fun stop(x:Context,r:String,o:String):Act007SessionResult{val s=c(x).stop(r,o);return Act007SessionResult(s.state.name,s.failure.name,s.request.instanceId,r,s.detail.orEmpty())}
 fun status(x:Context,i:String)=c(x).snapshots().count{it.request.instanceId==i}
 fun format(r:Act007SessionResult)="outcome=${r.outcome}\nreason=${r.reason}\ninstanceId=${r.instanceId}\nsessionRunId=${r.runId}\ndetail=${r.detail.take(240)}"
}
