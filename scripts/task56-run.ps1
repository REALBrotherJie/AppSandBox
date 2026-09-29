param(
  [string]$Serial='emulator-5554',[int]$ExpectedApi=36,
  [Parameter(Mandatory)][string]$HostApkPath,
  [Parameter(Mandatory)][string]$NormalApkPath,
  [Parameter(Mandatory)][string]$ThrowingApkPath,
  [Parameter(Mandatory)][string]$ConstructorCrashApkPath,
  [Parameter(Mandatory)][string]$BlockingApkPath,
  [Parameter(Mandatory)][string]$ExpectedHostSha256,
  [Parameter(Mandatory)][string]$ExpectedNormalSha256,
  [Parameter(Mandatory)][string]$ExpectedThrowingSha256,
  [Parameter(Mandatory)][string]$ExpectedConstructorCrashSha256,
  [Parameter(Mandatory)][string]$ExpectedBlockingSha256
)
$ErrorActionPreference='Stop'
if($ExpectedApi -notin 31,36){throw 'ExpectedApi must be 31 or 36'}
if($ExpectedApi -eq 36 -and $Serial-ne'emulator-5554'){throw 'API36 requires emulator-5554'}
$root=Split-Path $PSScriptRoot -Parent;$adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe';$matrixRun=[guid]::NewGuid().ToString('N')
$out=Join-Path $root "build/reports/task56/$Serial/$matrixRun";New-Item -ItemType Directory -Force $out|Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1');function A([string[]]$x,[int]$timeout=30){Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $x -TimeoutSec $timeout -ReportDir $out}
function Sha([string]$p){(Get-FileHash -Algorithm SHA256 -LiteralPath (Resolve-Path $p)).Hash.ToLowerInvariant()}
$artifacts=@(
  @($HostApkPath,$ExpectedHostSha256,'host'),@($NormalApkPath,$ExpectedNormalSha256,'normal'),
  @($ThrowingApkPath,$ExpectedThrowingSha256,'throwing'),@($ConstructorCrashApkPath,$ExpectedConstructorCrashSha256,'constructorCrash'),
  @($BlockingApkPath,$ExpectedBlockingSha256,'blocking'))
foreach($a in $artifacts){if((Sha $a[0])-ne$a[1].ToLowerInvariant()){throw "$($a[2]) SHA-256 mismatch"}}
if((A @('shell','getprop','ro.build.version.sdk')).Trim()-ne[string]$ExpectedApi){throw 'unexpected API'}
function Read-Report([string]$id){A @('shell','run-as','com.example.appsandbox','sh','-c',"'if [ -f files/task56-$id.result ]; then cat files/task56-$id.result; else echo NOT_READY; fi'")|Out-String}
function Value([string]$text,[string]$key){$m=[regex]::Match($text,"(?m)^"+[regex]::Escape($key)+"=(.*)$");if(!$m.Success){throw "missing $key"};$m.Groups[1].Value.Trim()}
function Wait-Report([string]$id,[int]$seconds=20){$deadline=(Get-Date).AddSeconds($seconds);do{$t=Read-Report $id;if($t-match'(?m)^FINAL=1\s*$'){if((Value $t 'commandId')-ne$id){throw 'commandId mismatch'};return $t};if($t-notmatch'(?m)^NOT_READY\s*$'){throw "unexpected report $t"};Start-Sleep -Milliseconds 200}while((Get-Date)-lt$deadline);throw "report timeout $id"}
function Launch([string]$action,[hashtable]$extras=@{},[switch]$NoWait,[switch]$Async){$id=[guid]::NewGuid().ToString('N');$args=@('shell','am','start');if(!$Async){$args+='-W'};$args+=@('--activity-clear-task','-n','com.example.appsandbox/.experiments.act008.Act008MatrixActivity','--es','commandId',$id,'--es','action',$action);foreach($key in $extras.Keys){$args+=@('--es',$key,[string]$extras[$key])};($args -join "`n")|Set-Content (Join-Path $out "$id-args.txt");$launch=A $args;$launch|Set-Content (Join-Path $out "$id-launch.txt");if($Async){if($launch-notmatch'(?m)^Starting: Intent'){throw "launch failed $launch"}}elseif($launch-notmatch'(?m)^Status: ok\s*$'){throw "launch failed $launch"};if($NoWait){return $id};$report=Wait-Report $id;$report|Set-Content (Join-Path $out "$id.result");$report}
function Assert-Pass([string]$t){if((Value $t 'status')-ne'PASS'){throw "case failed $t"}}
function Command([string]$action,[string]$instance,[string]$sessionRun,[string]$operation,[string]$request=([guid]::NewGuid().ToString('N'))){Launch $action @{requestId=$request;instanceId=$instance;sessionRunId=$sessionRun;operationId=$operation}}
function Host-Pid(){(A @('shell','pidof','com.example.appsandbox')).Trim().Split(' ')[0]}
function Runtime-Pid(){
  (A @('shell','sh','-c',"'pidof com.example.appsandbox:guest_runtime 2>/dev/null || true'")).Trim()
}
function Wait-RuntimeExit([string]$runtimeProcessId){
  if(!$runtimeProcessId -or $runtimeProcessId-eq(Host-Pid)){throw 'invalid runtime PID'}
  $deadline=(Get-Date).AddSeconds(5)
  do{$current=Runtime-Pid;if($current-ne$runtimeProcessId){return};Start-Sleep -Milliseconds 100}while((Get-Date)-lt$deadline)
  throw "runtime PID $runtimeProcessId did not self-terminate"
}
function Assert-Probe([string]$instance,[string]$expected){$file=(A @('shell','run-as','com.example.appsandbox','cat',"files/guest-instances/$instance/files/guest-probe.txt")).Trim();if($file-ne$expected){throw 'probe file mismatch'};$prefs=A @('shell','run-as','com.example.appsandbox','cat',"files/guest-instances/$instance/shared_prefs/guest_probe.xml");if($prefs-notmatch[regex]::Escape($expected)){throw 'probe preference mismatch'};$seed=(A @('shell','run-as','com.example.appsandbox','sh','-c',"'if [ -e files/guest-instances/$instance/files/probe-seed ]; then echo PRESENT; else echo ABSENT; fi'")).Trim();if($seed-ne'ABSENT'){throw 'probe seed not consumed'}}

A @('install','-r',(Resolve-Path $HostApkPath).Path)|Out-Null
$null = A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')
$remote=@{normal='task56-normal.apk';throwing='task56-throwing.apk';constructorCrash='task56-constructor.apk';blocking='task56-blocking.apk'}
foreach($entry in @(@($NormalApkPath,'normal'),@($ThrowingApkPath,'throwing'),@($ConstructorCrashApkPath,'constructorCrash'),@($BlockingApkPath,'blocking'))){A @('push',(Resolve-Path $entry[0]).Path,"/data/local/tmp/$($remote[$entry[1]])")|Out-Null;A @('shell','run-as','com.example.appsandbox','cp',"/data/local/tmp/$($remote[$entry[1]])","files/$($remote[$entry[1]])")|Out-Null}
$setup=Launch 'setup' @{normalApk='/data/data/com.example.appsandbox/files/task56-normal.apk';throwingApk='/data/data/com.example.appsandbox/files/task56-throwing.apk';constructorCrashApk='/data/data/com.example.appsandbox/files/task56-constructor.apk';blockingApk='/data/data/com.example.appsandbox/files/task56-blocking.apk'};Assert-Pass $setup
$a=Value $setup 'instanceA';$b=Value $setup 'instanceB';$badOnCreate=Value $setup 'onCreateCrash';$badConstructor=Value $setup 'constructorCrash';$blocked=Value $setup 'blocking';$probeA=Value $setup 'probeA';$probeB=Value $setup 'probeB';$hostPid=Value $setup 'hostPid'
$runA="${matrixRun}-a1";$runB="${matrixRun}-b1";$startA=Command start $a $runA "${matrixRun}-op-a1";Assert-Pass $startA;$startB=Command start $b $runB "${matrixRun}-op-b1";Assert-Pass $startB
$runtimePid=Value $startA 'ownerPid';if($runtimePid-ne(Value $startB 'ownerPid') -or $runtimePid-eq$hostPid){throw 'runtime PID boundary invalid'};Assert-Probe $a $probeA;Assert-Probe $b $probeB
$restartA=Command restart $a "${matrixRun}-a2" "${matrixRun}-op-a2";Assert-Pass $restartA;if((Value $restartA 'state')-ne'RUNNING'){throw 'A restart not running'};Assert-Probe $a $probeA
$throwing=Command start $badOnCreate "${matrixRun}-throw" "${matrixRun}-op-throw";Assert-Pass $throwing;if((Value $throwing 'failure')-ne'ON_CREATE_FAILED'){throw 'onCreate failure not reported'}
$constructor=Command start $badConstructor "${matrixRun}-constructor" "${matrixRun}-op-constructor";Assert-Pass $constructor;if((Value $constructor 'failure')-ne'CONSTRUCTION_FAILED'){throw 'constructor failure not reported'}
$terminate=Launch terminate @{requestId=([guid]::NewGuid().ToString('N'))};Assert-Pass $terminate;Start-Sleep -Milliseconds 500;if((Host-Pid)-ne$hostPid){throw 'Host PID changed after runtime termination'}
$recoverB=Command restart $b "${matrixRun}-b2" "${matrixRun}-op-b2";Assert-Pass $recoverB;$runtimePid2=Value $recoverB 'ownerPid';if($runtimePid2-eq$runtimePid){throw 'runtime PID did not change'};Assert-Probe $b $probeB
A @('shell','run-as','com.example.appsandbox','mkdir','-p',"files/guest-instances/$blocked/files")|Out-Null
A @('shell','run-as','com.example.appsandbox','mkdir','-p',"files/guest-instances/$blocked/files")|Out-Null
A @('shell','run-as','com.example.appsandbox','touch',"files/guest-instances/$blocked/files/exp008-self-terminate")|Out-Null
$blockedRun="${matrixRun}-block";$startingRuntime=$runtimePid2;$blockingCommand=Launch start @{timeoutMs='30000';requestId=([guid]::NewGuid().ToString('N'));instanceId=$blocked;sessionRunId=$blockedRun;operationId="${matrixRun}-op-block"} -NoWait -Async
$deadline=(Get-Date).AddSeconds(5);do{$activities=A @('shell','dumpsys','activity','activities');if($activities-match'(topResumedActivity|mResumedActivity).*Act008MatrixActivity'){break};Start-Sleep -Milliseconds 200}while((Get-Date)-lt$deadline);if($activities-notmatch'(topResumedActivity|mResumedActivity).*Act008MatrixActivity'){throw 'Host runner not resumed during STARTING'}
$deadline=(Get-Date).AddSeconds(15);do{$marker=(A @('shell','run-as','com.example.appsandbox','sh','-c',"'if [ -f files/guest-instances/$blocked/files/exp008-oncreate-entered ]; then echo ENTERED; else echo WAIT; fi'")).Trim();if($marker-eq'ENTERED'){break};Start-Sleep -Milliseconds 200}while((Get-Date)-lt$deadline);if($marker-ne'ENTERED'){throw 'blocking onCreate not entered'}
Wait-RuntimeExit $startingRuntime
$blockedReport=Wait-Report $blockingCommand 20;Assert-Pass $blockedReport;if((Value $blockedReport 'failure')-ne'CRASH_RECOVERY' -or (Value $blockedReport 'state')-ne'FAILED'){throw 'STARTING kill reply was not crash recovery'};if((Host-Pid)-ne$hostPid){throw 'Host PID changed after STARTING kill'}
$recoverBlocked=Launch read @{requestId=([guid]::NewGuid().ToString('N'));instanceId=$blocked;sessionRunId=$blockedRun};Assert-Pass $recoverBlocked;if((Value $recoverBlocked 'failure')-ne'CRASH_RECOVERY'){throw 'STARTING session not recovered'};$recoveredRuntime=Value $recoverBlocked 'ownerPid'
A @('shell','run-as','com.example.appsandbox','rm','-f',"files/guest-instances/$blocked/files/exp008-oncreate-entered")|Out-Null;$blockedNextRun="${matrixRun}-block-recovered";$blockedNextCommand=Launch start @{timeoutMs='30000';requestId=([guid]::NewGuid().ToString('N'));instanceId=$blocked;sessionRunId=$blockedNextRun;operationId="${matrixRun}-op-block-recovered"} -NoWait -Async
$deadline=(Get-Date).AddSeconds(15);do{$marker=(A @('shell','run-as','com.example.appsandbox','sh','-c',"'if [ -f files/guest-instances/$blocked/files/exp008-oncreate-entered ]; then echo ENTERED; else echo WAIT; fi'")).Trim();if($marker-eq'ENTERED'){break};Start-Sleep -Milliseconds 200}while((Get-Date)-lt$deadline);if($marker-ne'ENTERED'){throw 'new STARTING run did not enter onCreate'};Wait-RuntimeExit $recoveredRuntime;$blockedNext=Wait-Report $blockedNextCommand 20;Assert-Pass $blockedNext;if((Value $blockedNext 'sessionRunId')-ne$blockedNextRun -or (Value $blockedNext 'failure')-ne'CRASH_RECOVERY'){throw 'new STARTING runId was not isolated'}
$stopA=Command stop $a "${matrixRun}-a2" "${matrixRun}-stop-a";Assert-Pass $stopA;$deleteA=Launch delete @{requestId=([guid]::NewGuid().ToString('N'));instanceId=$a};Assert-Pass $deleteA;Assert-Probe $b $probeB
$packages=A @('shell','pm','list','packages','com.example.appsandbox.testguest');if($packages-match'package:'){throw 'Guest installed'};$ui=A @('shell','dumpsys','activity','activities');if($ui-match'ActivityRecord\{[^\r\n]*com\.example\.appsandbox\.testguest/'){throw 'Guest ActivityRecord exists'}
"FINAL=1`nstatus=PASS`nrunId=$matrixRun`nserial=$Serial`napi=$ExpectedApi`nhostPid=$hostPid`nruntimePid1=$runtimePid`nruntimePid2=$runtimePid2`ninstanceA=$a`ninstanceB=$b`nrestartExact=true`nruntimeDeathRecovery=true`nstartingDeathRecovery=true`nconstructorFailure=true`nonCreateFailure=true`ndeleteIsolation=true`nguestInstalled=false`nguestActivityRecord=false`nactivityAttach=0`nactivityLifecycle=0"|Set-Content (Join-Path $out 'result.txt')
Write-Output (Join-Path $out 'result.txt')
