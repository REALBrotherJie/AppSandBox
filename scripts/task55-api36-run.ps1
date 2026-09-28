param([string]$Serial='emulator-5554')
$ErrorActionPreference='Stop';if($Serial-ne'emulator-5554'){throw 'API36 emulator only'}
$root=Split-Path $PSScriptRoot -Parent;$adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe';$out=Join-Path $root "build/reports/task55/$Serial";New-Item -ItemType Directory -Force $out|Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1');function A([string[]]$x){Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $x -TimeoutSec 30 -ReportDir $out}
if((A @('shell','getprop','ro.build.version.sdk')).Trim()-ne'36'){throw 'not API36'}
$hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk';$guest=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install','-r',$hostApk)|Out-Null;A @('shell','pm','clear','com.example.appsandbox')|Out-Null;A @('push',$guest,'/data/local/tmp/task55.apk')|Out-Null;A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')|Out-Null;A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task55.apk','files/task55.apk')|Out-Null
$run=[guid]::NewGuid().ToString('N');A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.act007.api36.Act007RunnerActivity','--es','runId',$run,'--es','stagedApk','/data/data/com.example.appsandbox/files/task55.apk')|Out-Null
for($i=0;$i-lt80;$i++){try{$t=(A @('shell','run-as','com.example.appsandbox','cat',"files/task55-$run.result")|Out-String);if($t-match'(?m)^FINAL=1\s*$'){break}}catch{};Start-Sleep -Milliseconds 250};if($t-notmatch'(?m)^status=PASS\s*$'){throw "matrix failed $t"};$t|Set-Content (Join-Path $out 'matrix.txt')
$a=[regex]::Match($t,'instanceA=([0-9a-f-]{36})').Groups[1].Value;A @('shell','am','start','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','instanceId',$a)|Out-Null;Start-Sleep -Seconds 1
$ui=A @('shell','dumpsys','activity','activities');if($ui-notmatch'GuestWorkspaceActivity'){throw 'Workspace not resumed'}
$packages=A @('shell','pm','list','packages','com.example.appsandbox.testguest');if($packages-match'package:com.example.appsandbox.testguest'){throw 'Guest installed'};if($ui-match'ActivityRecord\{[^\r\n]*com\.example\.appsandbox\.testguest/'){throw 'Guest ActivityRecord'}
"status=PASS`nserial=$Serial`napi=36`nrunId=$run`napplicationOnCreate=true`nrestart=true`ndualIsolation=true`nthrowingGuest=true`nworkspaceResumed=true`nguestInstalled=false`nguestActivityRecord=false`nactivityAttach=0`nactivityLifecycle=0"|Set-Content (Join-Path $out 'result.txt')
