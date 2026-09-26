param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'; $adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe'; $root=Split-Path $PSScriptRoot -Parent
$report=Join-Path $root "build/reports/task34/$Serial"; New-Item -ItemType Directory -Force $report|Out-Null
function A([string[]]$v){& $adb -s $Serial @v 2>&1|Tee-Object -FilePath (Join-Path $report 'adb-last.txt');if($LASTEXITCODE -ne 0){throw "adb failed: $v"}}
function Xml{A @('shell','uiautomator','dump','/sdcard/t34.xml')|Out-Null;A @('pull','/sdcard/t34.xml',(Join-Path $report 'ui.xml'))|Out-Null;Get-Content -Raw (Join-Path $report 'ui.xml')}
function AssertXml([string]$pattern,[string]$message){if((Xml)-notmatch $pattern){throw $message}}
function Tap([string]$prefix){$x=Xml;$p='text="('+[regex]::Escape($prefix)+'[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"';$m=[regex]::Match($x,$p,[System.Text.RegularExpressions.RegexOptions]::IgnoreCase);if(!$m.Success){throw "UI not found: $prefix"};$cx=([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2;$cy=([int]$m.Groups[3].Value+[int]$m.Groups[5].Value)/2;A @('shell','input','tap',"$cx","$cy")|Out-Null;Start-Sleep -Milliseconds 500}
function Import([string]$path){A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','stagedApk',$path)|Out-Null;Start-Sleep -Seconds 1}
function CreateInstance{A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','createPackage','com.example.appsandbox.testguest')|Out-Null;Start-Sleep -Milliseconds 500}
function OpenPackage([int]$ordinal){A @('shell','am','start','-W','--activity-clear-task','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','packageName','com.example.appsandbox.testguest','--ei','instanceOrdinal',"$ordinal")|Out-Null;Start-Sleep -Seconds 1}
function OpenStale([string]$id){A @('shell','am','start','-W','--activity-clear-task','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','staleInstanceId',$id)|Out-Null;Start-Sleep -Seconds 1}
function CurrentId{$x=Xml;$m=[regex]::Match($x,'instance=([0-9a-f-]{36})','IgnoreCase');if(!$m.Success){throw 'workspace instance identity missing'};$m.Groups[1].Value.ToLowerInvariant()}
function DumpTasks([string]$name){$path=Join-Path $report $name;$out=& $adb -s $Serial shell dumpsys activity activities 2>&1;if($LASTEXITCODE -ne 0){throw 'dumpsys activity failed'};$out|Set-Content $path;($out-join"`n")}
function AssertTwoTasks([string]$dump,[string]$a,[string]$b){if($dump -notmatch [regex]::Escape("appsandbox://workspace/$a") -or $dump -notmatch [regex]::Escape("appsandbox://workspace/$b")){throw 'workspace document URI missing'};$ids=[regex]::Matches($dump,'GuestWorkspaceActivity[^\r\n]*\bt(\d+)\b')|ForEach-Object{$_.Groups[1].Value}|Select-Object -Unique;if($ids.Count -ne 2){throw "expected two distinct workspace taskIds, found $($ids.Count): $ids"}}
$api=(& $adb -s $Serial shell getprop ro.build.version.sdk).Trim();if($Serial -eq '7b670025' -and $api -ne '31'){throw "API $api"};if($Serial -eq 'emulator-5554' -and $api -ne '36'){throw "API $api"}
$hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk';$guest=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install','-r',$hostApk);A @('shell','pm','clear','com.example.appsandbox');A @('push',$guest,'/data/local/tmp/t34.apk');A @('shell','run-as','com.example.appsandbox','mkdir','-p','files');A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/t34.apk','files/t34.apk')
Import '/data/data/com.example.appsandbox/files/t34.apk';CreateInstance;CreateInstance
OpenPackage 0;$idA=CurrentId;Tap 'Guest Increment';AssertXml 'Counter: 1' 'A action failed';A @('shell','input','keyevent','3')|Out-Null
OpenPackage 1;$idB=CurrentId;if($idA -eq $idB){throw 'A/B identity collision'};AssertXml 'Counter: 0' 'B leaked A state';Tap 'Guest Increment';Tap 'Guest Increment';AssertXml 'Counter: 2' 'B action failed'
$dump=DumpTasks 'simultaneous.txt';AssertTwoTasks $dump $idA $idB
OpenPackage 0;AssertXml ([regex]::Escape("instance=$idA")) 'repeat A focused wrong instance';$repeat=DumpTasks 'repeat-a.txt';AssertTwoTasks $repeat $idA $idB
A @('shell','input','keyevent','3')|Out-Null;OpenPackage 1;AssertXml 'Counter: 2' 'recent-task style B focus lost state'
A @('shell','am','force-stop','com.example.appsandbox');OpenPackage 0;AssertXml 'Counter: 1' 'A restart state lost';OpenPackage 1;AssertXml 'Counter: 2' 'B restart state lost';AssertTwoTasks (DumpTasks 'relaunched.txt') $idA $idB
OpenPackage 0;Tap 'Close workspace';Start-Sleep -Seconds 1;$closed=DumpTasks 'closed-a.txt';if($closed -match [regex]::Escape("appsandbox://workspace/$idA") -or $closed -notmatch [regex]::Escape("appsandbox://workspace/$idB")){throw 'close isolation failed'}
OpenPackage 0;Tap 'Delete current instance';Tap 'Confirm delete instance';Start-Sleep -Seconds 1;OpenStale $idA;AssertXml 'Instance unavailable or registry is corrupted' 'stale A intent did not fail closed'
OpenPackage 0;AssertXml ([regex]::Escape("instance=$idB")) 'B unavailable after deleting A';AssertXml 'Counter: 2' 'B changed after deleting A';Tap 'Guest Toggle';AssertXml 'Counter: 0' 'B action failed after A deletion'
if(((& $adb -s $Serial shell pm path com.example.appsandbox.testguest)-join'')){throw 'Guest installed'}
$final=DumpTasks 'final.txt';if($final -match 'com.example.appsandbox.testguest/.runtime.GuestMainActivity'){throw 'Guest ActivityRecord detected'}
"simultaneousWorkspaces=PASS`ndistinctTaskIds=PASS`nrepeatLaunchReuse=PASS`ninstanceIsolation=PASS`nrestart=PASS`ncloseDeleteIsolation=PASS`nstaleIntent=PASS`nguestActivityRecord=false`nguestInstalled=false"|Set-Content (Join-Path $report 'result.txt')
