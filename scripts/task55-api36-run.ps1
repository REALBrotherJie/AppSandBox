param(
  [string]$Serial='emulator-5554',
  [string]$HostApkPath='',
  [string]$GuestApkPath='',
  [string]$ThrowingApkPath='',
  [string]$ExpectedHostSha256='',
  [string]$ExpectedGuestSha256='',
  [string]$ExpectedThrowingSha256=''
  ,[int]$ExpectedApi=36
)
$ErrorActionPreference='Stop';if($ExpectedApi -eq 36 -and $Serial-ne'emulator-5554'){throw 'API36 requires emulator-5554'};if($ExpectedApi -eq 31 -and $Serial-eq'emulator-5554'){throw 'API31 cannot use emulator-5554'};if($ExpectedApi -notin 31,36){throw 'ExpectedApi must be 31 or 36'}
$root=Split-Path $PSScriptRoot -Parent;$adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe';$out=Join-Path $root "build/reports/task55/$Serial";New-Item -ItemType Directory -Force $out|Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1');function A([string[]]$x){Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $x -TimeoutSec 30 -ReportDir $out}
if((A @('shell','getprop','ro.build.version.sdk')).Trim() -ne [string]$ExpectedApi){throw "unexpected API (expected $ExpectedApi)"}
$hostApk=if($HostApkPath){(Resolve-Path $HostApkPath).Path}else{Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'}
$guest=if($GuestApkPath){(Resolve-Path $GuestApkPath).Path}else{Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/normal/debug/GuestTestApp-normal-debug.apk'}
$throwing=if($ThrowingApkPath){(Resolve-Path $ThrowingApkPath).Path}else{Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/throwing/debug/GuestTestApp-throwing-debug.apk'}
foreach($p in @($hostApk,$guest,$throwing)){if(!(Test-Path -LiteralPath $p)){throw "missing artifact $p"}}
function Sha([string]$p){(Get-FileHash -Algorithm SHA256 -LiteralPath $p).Hash.ToLowerInvariant()}
$hostSha=Sha $hostApk;$guestSha=Sha $guest;$throwingSha=Sha $throwing
if(!$ExpectedHostSha256 -or !$ExpectedGuestSha256 -or !$ExpectedThrowingSha256){throw 'all three frozen SHA-256 values are required'}
if($ExpectedHostSha256 -and $hostSha -ne $ExpectedHostSha256.ToLowerInvariant()){throw 'host SHA-256 mismatch'}
if($ExpectedGuestSha256 -and $guestSha -ne $ExpectedGuestSha256.ToLowerInvariant()){throw 'guest SHA-256 mismatch'}
if($ExpectedThrowingSha256 -and $throwingSha -ne $ExpectedThrowingSha256.ToLowerInvariant()){throw 'throwing guest SHA-256 mismatch'}
A @('install','-r',$hostApk)|Out-Null;A @('shell','pm','clear','com.example.appsandbox')|Out-Null;A @('push',$guest,'/data/local/tmp/task55.apk')|Out-Null;A @('push',$throwing,'/data/local/tmp/task55-throwing.apk')|Out-Null;A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')|Out-Null;A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task55.apk','files/task55.apk')|Out-Null;A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task55-throwing.apk','files/task55-throwing.apk')|Out-Null
$hold=[guid]::NewGuid().ToString('N');A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.act007.api36.Act007RunnerActivity','--es','runId',$hold,'--ei','expectedApi',[string]$ExpectedApi,'--es','stagedApk','/data/data/com.example.appsandbox/files/task55.apk','--es','throwingApk','/data/data/com.example.appsandbox/files/task55-throwing.apk','--ez','holdAfterStart','true')|Out-Null
$deadline=(Get-Date).AddSeconds(30);$h='';while((Get-Date)-lt $deadline){$h=(A @('shell','run-as','com.example.appsandbox','cat',"files/task55-$hold.result")|Out-String);if($h-match'(?m)^READY=1\s*$'){break};Start-Sleep -Milliseconds 250};if($h-notmatch'(?m)^READY=1\s*$'){throw 'hold run did not reach READY'};$instance=[regex]::Match($h,'instanceA=([0-9a-f-]{36})').Groups[1].Value;if(!$instance){throw 'hold run missing instanceA'};A @('shell','am','force-stop','com.example.appsandbox')|Out-Null
$recovery=[guid]::NewGuid().ToString('N');$oldRunId="$hold-a1";A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.act007.api36.Act007RunnerActivity','--es','runId',$recovery,'--ei','expectedApi',[string]$ExpectedApi,'--es','mode','recover','--es','instanceId',$instance,'--es','oldRunId',$oldRunId)|Out-Null
$deadline=(Get-Date).AddSeconds(30);$rt='';while((Get-Date)-lt $deadline){$rt=(A @('shell','run-as','com.example.appsandbox','cat',"files/task55-$recovery.result")|Out-String);if($rt-match'(?m)^FINAL=1\s*$'){break};Start-Sleep -Milliseconds 250};if($rt-notmatch'(?m)^status=PASS\s*$'){throw "recovery failed $rt"}
$run=[guid]::NewGuid().ToString('N');A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.act007.api36.Act007RunnerActivity','--es','runId',$run,'--ei','expectedApi',[string]$ExpectedApi,'--es','stagedApk','/data/data/com.example.appsandbox/files/task55.apk','--es','throwingApk','/data/data/com.example.appsandbox/files/task55-throwing.apk')|Out-Null
$deadline=(Get-Date).AddSeconds(30);$t='';while((Get-Date)-lt $deadline){$t=(A @('shell','run-as','com.example.appsandbox','cat',"files/task55-$run.result")|Out-String);if($t-match'(?m)^FINAL=1\s*$'){break};Start-Sleep -Milliseconds 250};if($t-notmatch'(?m)^FINAL=1\s*$'){throw 'matrix report timeout'};if($t-notmatch'(?m)^status=PASS\s*$'){throw "matrix failed $t"};if($t-notmatch("(?m)^runId="+[regex]::Escape($run)+"\s*$")){throw 'matrix runId mismatch'};$t|Set-Content (Join-Path $out 'matrix.txt')
$a=[regex]::Match($t,'instanceA=([0-9a-f-]{36})').Groups[1].Value;A @('shell','am','start','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','instanceId',$a)|Out-Null;Start-Sleep -Seconds 1
$ui=A @('shell','dumpsys','activity','activities');if($ui-notmatch'GuestWorkspaceActivity'){throw 'Workspace not resumed'}
$packages=A @('shell','pm','list','packages','com.example.appsandbox.testguest');if($packages-match'package:com.example.appsandbox.testguest'){throw 'Guest installed'};if($ui-match'ActivityRecord\{[^\r\n]*com\.example\.appsandbox\.testguest/'){throw 'Guest ActivityRecord'}
"status=PASS`nserial=$Serial`napi=$ExpectedApi`nrunId=$run`nhostSha256=$hostSha`nguestSha256=$guestSha`nthrowingSha256=$throwingSha`napplicationOnCreate=true`nrestart=true`ndualIsolation=true`nthrowingGuest=true`nworkspaceResumed=true`nguestInstalled=false`nguestActivityRecord=false`nactivityAttach=0`nactivityLifecycle=0"|Set-Content (Join-Path $out 'result.txt')
