param(
    [string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$sha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = Join-Path $root "docs/experiments/evidence/task20/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($api -ne '36') { throw "Require API36, got API $api" }
$product = (& $adb -s $Serial shell getprop ro.product.name).Trim()
$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
$emuPid = (Get-Content (Join-Path $root 'docs/experiments/evidence/task20/api36-avd/avd-start.txt') | Select-String '^pid=').ToString()
"serial=$Serial`napi=$api`nproduct=$product`nmodel=$model`nabi=$abi`npageSize=$page`n$emuPid" | Set-Content (Join-Path $evidence 'device.txt')
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task20-input.apk')
A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task20-input.apk','files/task18-input.apk')
$before = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
if ($LASTEXITCODE -notin @(0,1) -or $before) { throw 'Guest unexpectedly installed before run' }
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f','files/task18-act001.txt','files/task19-recreation.txt')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act001','--ez','import','true','--ez','recreate','true')
Start-Sleep -Seconds 3
$report = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task18-act001.txt
$reportText = ($report -join "`n")
$reportText | Set-Content (Join-Path $evidence 'act001-api36.txt')
$recreation = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task19-recreation.txt
($recreation -join "`n") | Set-Content (Join-Path $evidence 'recreation.txt')
$logcat = & $adb -s $Serial logcat -d -s Task15:I '*:S'
($logcat -join "`n") | Set-Content (Join-Path $evidence 'act001-api36-logcat.txt')
$after = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
($after -join "`n") | Set-Content (Join-Path $evidence 'act001-api36-pm-path.txt')
if ($LASTEXITCODE -notin @(0,1) -or $after) { throw 'Guest unexpectedly installed after run' }
if ($reportText -notmatch "record.sha256=$sha" -or $reportText -notmatch 'verification=VALID' -or
    $reportText -notmatch 'guest.markerPresent=true' -or $reportText -notmatch 'guest.view.contextIsControlled=true' -or
    $reportText -notmatch 'host.package=com.example.appsandbox' -or $reportText -notmatch 'guest.systemInstalled=false' -or
    $reportText -match 'directGuestLaunch=UNEXPECTED_SUCCESS' -or $reportText -notmatch 'hostSurvived=true' -or
    ($recreation -join "`n") -notmatch 'stateRestored=true') { throw 'Task-20 evidence incomplete' }
