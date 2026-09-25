param(
    [string]$Serial = '7b670025',
    [switch]$RequireApi36
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$sha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = Join-Path $root "docs/experiments/evidence/task19/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
$product = (& $adb -s $Serial shell getprop ro.product.name).Trim()
$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
"serial=$Serial`napi=$api`nproduct=$product`nmodel=$model`nabi=$abi`npageSize=$page" | Set-Content (Join-Path $evidence 'device.txt')
if ($RequireApi36 -and $api -ne '36') {
    "api36Available=false`nblockingReason=connected device API $api, expected API36" | Set-Content (Join-Path $evidence 'api36-unavailable.txt')
    throw "API36 required but connected device is API $api"
}
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task19-input.apk')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task19-input.apk','files/task18-input.apk')
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
    ($recreation -join "`n") -notmatch 'stateRestored=true') { throw 'Task-19 evidence incomplete' }
