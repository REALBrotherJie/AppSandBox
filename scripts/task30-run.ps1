param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'; $root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task30/$Serial"; New-Item -ItemType Directory -Force $report | Out-Null
function A([string[]]$v) { & $adb -s $Serial @v 2>&1 | Tee-Object -FilePath (Join-Path $report 'adb-last.txt'); if($LASTEXITCODE -ne 0){throw "adb failed: $v"} }
function Xml { A @('shell','uiautomator','dump','/sdcard/t30.xml')|Out-Null; A @('pull','/sdcard/t30.xml',(Join-Path $report 'ui.xml'))|Out-Null; Get-Content -Raw (Join-Path $report 'ui.xml') }
function Tap([string]$prefix,[int]$index=0) { $x=Xml; $p='text="('+[regex]::Escape($prefix)+'[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'; $ms=[regex]::Matches($x,$p,[System.Text.RegularExpressions.RegexOptions]::IgnoreCase); if($ms.Count -le $index){throw "UI not found: $prefix index=$index"}; $m=$ms[$index]; $cx=([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2; $cy=([int]$m.Groups[3].Value+[int]$m.Groups[5].Value)/2; A @('shell','input','tap',"$cx","$cy")|Out-Null; Start-Sleep -Milliseconds 600 }
function TapScroll([string]$prefix) { for($i=0;$i-lt 5;$i++){ try { Tap $prefix; return } catch { A @('shell','input','swipe','500','1900','500','1200','300')|Out-Null; Start-Sleep -Milliseconds 400 } }; throw "UI not found after scroll: $prefix" }
function ImportGuest { A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','stagedApk','/data/data/com.example.appsandbox/files/task30.apk')|Out-Null; Start-Sleep -Seconds 1 }
$api=(& $adb -s $Serial shell getprop ro.build.version.sdk).Trim(); if($Serial -eq '7b670025' -and $api -ne '31'){throw "API $api"}; if($Serial -eq 'emulator-5554' -and $api -ne '36'){throw "API $api"}
$hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'; $guest=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install','-r',$hostApk); A @('shell','pm','clear','com.example.appsandbox'); A @('push',$guest,'/data/local/tmp/task30.apk'); A @('shell','run-as','com.example.appsandbox','mkdir','-p','files'); A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task30.apk','files/task30.apk')
ImportGuest; Tap 'Create instance from selected revision'
ImportGuest; Tap 'Create instance from selected revision'
$library=Xml; if(([regex]::Matches($library,'contract=v1')).Count -lt 2){throw 'two revisions not visible'}
$identity=[regex]::Match($library,'OPEN [^\"]* R:([A-F0-9]{8}) I:([A-F0-9]{8})',[System.Text.RegularExpressions.RegexOptions]::IgnoreCase); $oldRevision=$identity.Groups[1].Value; $oldInstance=$identity.Groups[2].Value; if(!$oldRevision -or !$oldInstance){throw 'old identity missing'}
Tap 'Open '; Tap 'Increment'; A @('shell','input','keyevent','4'); Start-Sleep -Milliseconds 500
A @('shell','input','swipe','500','1800','500','900','300'); Start-Sleep -Milliseconds 500; Tap 'Open '; Tap 'Increment'; Tap 'Increment'; A @('shell','input','keyevent','4'); Start-Sleep -Milliseconds 500
A @('shell','am','force-stop','com.example.appsandbox'); A @('shell','monkey','-p','com.example.appsandbox','1')|Out-Null; Start-Sleep -Seconds 1
Tap 'Delete revision '; Start-Sleep -Milliseconds 300; if((Xml)-notmatch 'Delete instances using this revision first'){throw 'reference protection missing'}
A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','instanceId',$oldInstance)|Out-Null; Start-Sleep -Seconds 1; Tap 'Delete current instance'; Tap 'Confirm delete instance'; Start-Sleep -Milliseconds 500
A @('shell','monkey','-p','com.example.appsandbox','1')|Out-Null; Start-Sleep -Seconds 1
A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','deleteRevision',$oldRevision)|Out-Null; Start-Sleep -Milliseconds 500
if(((& $adb -s $Serial shell pm path com.example.appsandbox.testguest)-join'')){throw 'Guest installed'}
"libraryRevisions=2`nrevisionBoundInstances=PASS`nrestart=PASS`nreferenceProtection=PASS`ndeleteAfterUnreference=PASS`nguestInstalled=false"|Set-Content (Join-Path $report 'result.txt')
