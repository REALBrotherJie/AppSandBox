param([string]$Serial = '7b670025')
$ErrorActionPreference='Stop'
if($Serial -ne '7b670025'){throw 'Task-51 permits only serial 7b670025'}
$adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe';$root=Split-Path $PSScriptRoot -Parent
$report=Join-Path $root "build/reports/task51/$Serial";New-Item -ItemType Directory -Force $report|Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1')
function A([string[]]$argv){Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $argv -TimeoutSec 30 -ReportDir $report}
function ReadFinal([string]$path,[string]$runId){for($i=0;$i-lt 80;$i++){try{$t=A @('shell','run-as','com.example.appsandbox','cat',$path);if($t-match '^status=FINAL' -and $t-match "runId=$runId"){return $t}}catch{};Start-Sleep -Milliseconds 250};throw "report timeout $path"}
if((A @('get-state')).Trim()-ne'device'){throw 'device unavailable'};if((A @('shell','getprop','ro.build.version.sdk')).Trim()-ne'31'){throw 'wrong API'}
$hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk';$guest=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install','-r',$hostApk)|Out-Null;A @('shell','pm','clear','com.example.appsandbox')|Out-Null
A @('push',$guest,'/data/local/tmp/task51-guest.apk')|Out-Null;A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')|Out-Null;A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task51-guest.apk','files/task51-guest.apk')|Out-Null
$setup="setup-$([guid]::NewGuid().ToString('N'))";A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.act006.api31.Act006SetupActivity','--es','runId',$setup,'--es','stagedApk','/data/data/com.example.appsandbox/files/task51-guest.apk')|Out-Null
$s=ReadFinal "files/task51-setup-$setup.result" $setup;if($s-notmatch'outcome=IMPORTED'){throw "Guest import failed`n$s"};$instance=[regex]::Match($s,'instanceId=([0-9a-f-]{36})').Groups[1].Value
function Run([string]$case,[string]$id,[string]$reason){$path="files/task51-$id.result";A @('shell','run-as','com.example.appsandbox','rm','-f',$path)|Out-Null;try{A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.act006.api31.Act006Runner','--es','runId',$id,'--es','caseId',$case,'--es','instanceId',$instance)|Out-Null}catch{};$t=ReadFinal $path $id;$t|Set-Content (Join-Path $report "$case-$id.txt");if($t-notmatch "caseId=$case"-or$t-notmatch "reason=$reason"-or$t-notmatch'guestLifecycle=false'){throw "$case assertion failed`n$t"};return $t}
$negative=@{'stale-revision'='STALE_REVISION';'artifact-mismatch'='ARTIFACT_MISMATCH';'missing-class'='MISSING_CLASS';'non-activity'='NON_ACTIVITY_CLASS';'wrong-fingerprint'='WRONG_ADAPTER_FINGERPRINT';'access-denied'='ACCESS_DENIED'}
foreach($case in $negative.Keys){$t=Run $case "$case-$([guid]::NewGuid().ToString('N'))" $negative[$case];if($t-notmatch'attachInvokeAttempted=false'-or$t-notmatch'guestConstructed=false'){throw "$case crossed attach boundary"}}
$validId="valid-$([guid]::NewGuid().ToString('N'))";$valid=Run 'valid' $validId 'ACCESS_DENIED'
$firstPid=(A @('shell','pidof','com.example.appsandbox')).Trim();A @('shell','am','force-stop','com.example.appsandbox')|Out-Null
$repeatId="valid-repeat-$([guid]::NewGuid().ToString('N'))";$validRepeat=Run 'valid' $repeatId 'ACCESS_DENIED';$repeatPid=(A @('shell','pidof','com.example.appsandbox')).Trim();if(!$repeatPid-or$repeatPid-eq$firstPid){throw "valid repeat did not use a new process old=$firstPid new=$repeatPid"}
$duplicateId="duplicate-$([guid]::NewGuid().ToString('N'))";Run 'access-denied' $duplicateId 'ACCESS_DENIED'|Out-Null;$dup=Run 'access-denied' $duplicateId 'DUPLICATE_LAUNCH';if($dup-notmatch'attachInvokeAttempted=false'){throw 'duplicate crossed attach boundary'}
$oldPid=(A @('shell','pidof','com.example.appsandbox')).Trim();A @('shell','am','force-stop','com.example.appsandbox')|Out-Null;A @('shell','monkey','-p','com.example.appsandbox','1')|Out-Null;Start-Sleep -Seconds 1;$newPid=(A @('shell','pidof','com.example.appsandbox')).Trim();if(!$newPid-or$newPid-eq$oldPid){throw "process recovery failed old=$oldPid new=$newPid"}
$activities=A @('shell','dumpsys','activity','activities');if($activities-notmatch'(mResumedActivity|topResumedActivity).*com\.example\.appsandbox/.MainActivity'){throw 'Host not resumed after restart'}
$guestInstalled=try{(A @('shell','pm','path','com.example.appsandbox.testguest')).Trim()}catch{''};if($guestInstalled){throw 'Guest package installed'};if($activities-match'com\.example\.appsandbox\.testguest'){throw 'Guest ActivityRecord observed'}
"status=PASS`nserial=$Serial`napi=31`nnegativePassed=$($negative.Count + 1)`nvalidRuns=2`nvalidOutcome=$([regex]::Match($valid,'outcome=([^\r\n]+)').Groups[1].Value)`nvalidReason=$([regex]::Match($valid,'reason=([^\r\n]+)').Groups[1].Value)`nfirstPid=$firstPid`nrepeatPid=$repeatPid`noldPid=$oldPid`nnewPid=$newPid`nguestInstalled=false`nguestActivityRecord=false"|Set-Content (Join-Path $report result.txt)
