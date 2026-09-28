package com.example.appsandbox.automation
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.appsandbox.experiments.act007.api31.Act007ApplicationSessions
import com.example.appsandbox.storage.GuestInstanceStore
import java.io.File
import java.util.UUID
class Task55Api31Activity:Activity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);val action=intent.getStringExtra("action");if(action==null){ui();return};val id=intent.getStringExtra("runId").orEmpty();val out=runCatching{exec(action,id)}.getOrElse{"outcome=REJECTED\nreason=INTERNAL:${it.javaClass.simpleName}"};File(filesDir,"task55-$id.result").writeText("status=FINAL\nrunId=$id\naction=$action\n$out\nactivityLifecycleCalled=false\nactivityAttachCalled=false\n");finish()}
 private fun exec(a:String,id:String)=when(a){"start","restart"->Act007ApplicationSessions.format(Act007ApplicationSessions.start(this,intent.getStringExtra("instanceId").orEmpty(),id,intent.getStringExtra("operationId").orEmpty()));"stop"->Act007ApplicationSessions.format(Act007ApplicationSessions.stop(this,intent.getStringExtra("sessionRunId").orEmpty(),intent.getStringExtra("operationId").orEmpty()));"status"->"outcome=STATUS\nreason=NONE\ncount="+Act007ApplicationSessions.status(this,intent.getStringExtra("instanceId").orEmpty());"delete"->"outcome=DELETED\nreason=NONE\nremoved="+GuestInstanceStore(this).delete(intent.getStringExtra("instanceId").orEmpty());else->"outcome=REJECTED\nreason=INVALID_ACTION"}
 private fun ui(){val i=intent.getStringExtra("instanceId").orEmpty();val s=TextView(this);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)};root.addView(TextView(this).apply{text="Controlled Guest Application session\ninstance=$i"});root.addView(s);var run="";fun refresh(){s.text=Act007ApplicationSessions.status(this,i).toString()};root.addView(Button(this).apply{text="Start";setOnClickListener{run=UUID.randomUUID().toString();s.text=Act007ApplicationSessions.format(Act007ApplicationSessions.start(this@Task55Api31Activity,i,run,UUID.randomUUID().toString()))}});root.addView(Button(this).apply{text="Stop";setOnClickListener{if(run.isNotBlank())s.text=Act007ApplicationSessions.format(Act007ApplicationSessions.stop(this@Task55Api31Activity,run,UUID.randomUUID().toString()))}});root.addView(Button(this).apply{text="Restart";setOnClickListener{if(run.isNotBlank())runCatching{Act007ApplicationSessions.stop(this@Task55Api31Activity,run,UUID.randomUUID().toString())};run=UUID.randomUUID().toString();s.text=Act007ApplicationSessions.format(Act007ApplicationSessions.start(this@Task55Api31Activity,i,run,UUID.randomUUID().toString()))}});setContentView(root);refresh()}
}
