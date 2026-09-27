param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'
$adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root=Split-Path $PSScriptRoot -Parent
$report=Join-Path $root "build/reports/task49/$Serial"
New-Item -ItemType Directory -Force $report|Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1')
function A([string[]]$argv){Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $argv -TimeoutSec 30 -ReportDir $report}
function ReadInternal([string]$path,[string]$runId,[string]$terminal='status=PASS|status=FAIL'){
  for($i=0;$i -lt 60;$i++){try{$t=A @('shell','run-as','com.example.appsandbox','cat',$path);if($t -match [regex]::Escape($runId) -and $t -match $terminal){return $t}}catch{};Start-Sleep -Milliseconds 250};throw "report timeout: $path"
}
function Launch([string]$scenario,[string]$launchId,[string]$expected){
  $path="files/task49-$launchId.result"
  A @('shell','run-as','com.example.appsandbox','rm','-f',$path)|Out-Null
  A @('shell','am','start','-W','--activity-clear-task','-n','com.example.appsandbox/.automation.Task49SetupActivity','--es','runId',$launchId,'--es','instanceId',$script:instanceId,'--es','scenario',$scenario)|Out-Null
  $t=ReadInternal $path $launchId 'hostSurvived=true'
  $t|Set-Content (Join-Path $report "$scenario-$launchId.txt")
  foreach($pattern in @($expected,'act005.hostFallback=true','hostFallback=ACT003_STUB','guestObjectConstructed=false','guestAttached=false','guestLifecycle=false','lifecycle=onCreate','lifecycle=onStart','lifecycle=onResume','decor.windowToken.nonNull=true','decor.applicationWindowToken.nonNull=true')){if($t -notmatch $pattern){throw "$scenario missing $pattern`n$t"}}
  $t
}
$api=(A @('shell','getprop','ro.build.version.sdk')).Trim();if($api -notin @('31','36')){throw "unsupported API $api"}
$hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk';$guest=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install','-r',$hostApk)|Out-Null;A @('shell','pm','clear','com.example.appsandbox')|Out-Null
A @('push',$guest,'/data/local/tmp/task49-guest.apk')|Out-Null;A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')|Out-Null;A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task49-guest.apk','files/task49-guest.apk')|Out-Null
$setup="setup-$([guid]::NewGuid().ToString('N'))";A @('shell','am','start','-W','-n','com.example.appsandbox/.automation.Task49SetupActivity','--es','runId',$setup,'--es','stagedApk','/data/data/com.example.appsandbox/files/task49-guest.apk')|Out-Null
$setupText=ReadInternal "files/task49-setup-$setup.result" $setup;$script:instanceId=[regex]::Match($setupText,'instanceId=([0-9a-f-]{36})').Groups[1].Value;if(!$script:instanceId){throw 'setup instance missing'}
$cases=@(
 @('valid','CLASS_SELECTED->ROLLED_BACK_TO_HOST'),@('missing-class','reason=MISSING_CLASS'),@('non-activity','reason=NON_ACTIVITY_CLASS'),
 @('stale-revision','reason=STALE_MAPPING'),@('component-stale','reason=COMPONENT_MISMATCH'),@('artifact-mismatch','reason=ARTIFACT_MISMATCH'),@('api-mismatch','reason=API_MISMATCH'),@('access-denied','reason=ACCESS_DENIED'))
foreach($c in $cases){Launch $c[0] "$($c[0])-$([guid]::NewGuid().ToString('N'))" $c[1]|Out-Null}
$duplicate="duplicate-$([guid]::NewGuid().ToString('N'))";Launch 'valid' $duplicate 'reason=NONE'|Out-Null;Launch 'access-denied' $duplicate 'reason=DUPLICATE_LAUNCH'|Out-Null
A @('shell','am','force-stop','com.example.appsandbox')|Out-Null;Launch 'valid' $duplicate 'reason=NONE'|Out-Null
$installed=try{(A @('shell','pm','path','com.example.appsandbox.testguest')).Trim()}catch{''};if($installed){throw 'Guest package installed'}
$activities=A @('shell','dumpsys','activity','activities');if($activities -match 'com\.example\.appsandbox\.testguest'){throw 'Guest ActivityRecord observed'}
"status=PASS`nserial=$Serial`napi=$api`ncases=11`ninstanceId=$script:instanceId`nguestInstalled=false`nguestActivityRecord=false`nguestObjectConstructed=false`nguestAttached=false`nguestLifecycle=false`nhostFallback=true"|Set-Content (Join-Path $report 'result.txt')
