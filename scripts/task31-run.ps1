param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'; $adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe'; $root=Split-Path $PSScriptRoot -Parent
$report=Join-Path $root "build/reports/task31/$Serial"; New-Item -ItemType Directory -Force $report|Out-Null
function A([string[]]$v){& $adb -s $Serial @v 2>&1|Tee-Object -FilePath (Join-Path $report 'adb-last.txt');if($LASTEXITCODE -ne 0){throw "adb failed: $v"}}
function Xml{A @('shell','uiautomator','dump','/sdcard/t31.xml')|Out-Null;A @('pull','/sdcard/t31.xml',(Join-Path $report 'ui.xml'))|Out-Null;Get-Content -Raw (Join-Path $report 'ui.xml')}
function Tap([string]$prefix){$x=Xml;$p='text="('+[regex]::Escape($prefix)+'[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"';$m=[regex]::Match($x,$p,[System.Text.RegularExpressions.RegexOptions]::IgnoreCase);if(!$m.Success){throw "UI not found: $prefix"};$cx=([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2;$cy=([int]$m.Groups[3].Value+[int]$m.Groups[5].Value)/2;A @('shell','input','tap',"$cx","$cy")|Out-Null;Start-Sleep -Milliseconds 500}
function Import([string]$path){A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','stagedApk',$path)|Out-Null;Start-Sleep -Seconds 1}
function OpenPackage([string]$packageName){A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','packageName',$packageName)|Out-Null;Start-Sleep -Seconds 1}
$api=(& $adb -s $Serial shell getprop ro.build.version.sdk).Trim();if($Serial -eq '7b670025' -and $api -ne '31'){throw "API $api"};if($Serial -eq 'emulator-5554' -and $api -ne '36'){throw "API $api"}
$hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk';$a=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk';$b=Join-Path $root 'test-guests/IndependentGuest/build/outputs/apk/debug/IndependentGuest-debug.apk'
if((Get-FileHash $a).Hash -eq (Get-FileHash $b).Hash){throw 'Guest APK SHA must differ'}
A @('install','-r',$hostApk);A @('shell','pm','clear','com.example.appsandbox');A @('push',$a,'/data/local/tmp/a.apk');A @('push',$b,'/data/local/tmp/b.apk');A @('shell','run-as','com.example.appsandbox','mkdir','-p','files');A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/a.apk','files/a.apk');A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/b.apk','files/b.apk')
Import '/data/data/com.example.appsandbox/files/a.apk';Tap 'Create instance from selected revision'
Import '/data/data/com.example.appsandbox/files/b.apk';Tap 'Create instance from selected revision'
$x=Xml;if($x -notmatch 'com.example.appsandbox.testguest' -or $x -notmatch 'com.example.appsandbox.independentguest'){throw 'two packages missing'}
$ma=[regex]::Match($x,'GuestTestApp[^\"]*revision=([A-F0-9]{8}) contract=v2','IgnoreCase');if(!$ma.Success){throw 'Guest A revision missing'}
OpenPackage 'com.example.appsandbox.testguest';if((Xml)-notmatch'GUEST_A_MARKER_1D1C4B6A'){throw 'Guest A marker missing'};Tap 'Guest Increment'
OpenPackage 'com.example.appsandbox.independentguest';if((Xml)-notmatch'INDEPENDENT_GUEST_MARKER_B'){throw 'Guest B marker missing'};Tap 'Increment';Tap 'Increment'
A @('shell','am','force-stop','com.example.appsandbox');OpenPackage 'com.example.appsandbox.testguest';if((Xml)-notmatch'Counter: 1'){throw 'A counter lost'}
A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','tryDeleteRevision',$ma.Groups[1].Value)|Out-Null;Start-Sleep -Seconds 1;if((Xml)-notmatch'BLOCKED: Delete instances using this revision first'){throw 'reference protection missing'}
OpenPackage 'com.example.appsandbox.testguest';Tap 'Delete current instance';Tap 'Confirm delete instance';A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','deleteRevision',$ma.Groups[1].Value)|Out-Null
OpenPackage 'com.example.appsandbox.independentguest';$final=Xml;if($final -notmatch 'INDEPENDENT_GUEST_MARKER_B' -or $final -notmatch 'Counter: 2'){throw 'Guest B changed'}
if(((& $adb -s $Serial shell pm path com.example.appsandbox.testguest)-join'') -or ((& $adb -s $Serial shell pm path com.example.appsandbox.independentguest)-join'')){throw 'Guest installed'}
"packagesDistinct=PASS`napkShaDistinct=PASS`nmarkersDistinct=PASS`ncountersIsolated=PASS`nrestart=PASS`nreferenceDeletion=PASS`nguestInstalled=false"|Set-Content (Join-Path $report 'result.txt')
