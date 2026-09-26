param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task28/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv 2>&1 | Tee-Object -FilePath (Join-Path $report 'adb-last.txt'); if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
function DumpUi { A @('shell','uiautomator','dump','/sdcard/task28-ui.xml') | Out-Null; A @('pull','/sdcard/task28-ui.xml',(Join-Path $report 'ui.xml')) | Out-Null }
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }
"serial=$Serial`napi=$api" | Set-Content (Join-Path $report 'device.txt')
$apk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$guest = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install','-r',$apk); A @('shell','pm','clear','com.example.appsandbox'); A @('push',$guest,'/sdcard/Download/task28-guest.apk')
A @('shell','am','start','-W','-n','com.example.appsandbox/.MainActivity'); Start-Sleep -Seconds 1
DumpUi
"Debug-signed build used for device workflow because this project emits an unsigned release APK. Product source path is the release implementation. Use the supported import flow, then create two instances, open each, increment independently, force-stop/relaunch, delete A, and verify B remains." | Set-Content (Join-Path $report 'workflow.txt')
if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '') { throw 'Guest package must not be installed' }
"guestPackageInstalled=false`nreleaseWorkspace=launched`nmanualImportRequired=true" | Add-Content (Join-Path $report 'workflow.txt')
