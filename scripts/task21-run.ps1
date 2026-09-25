param(
    [Parameter(Mandatory=$true)]
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$sha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = Join-Path $root "docs/experiments/evidence/task21/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
$product = (& $adb -s $Serial shell getprop ro.product.name).Trim()
$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
"serial=$Serial`napi=$api`nproduct=$product`nmodel=$model`nabi=$abi`npageSize=$page" | Set-Content (Join-Path $evidence 'device.txt')
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task21-input.apk')
A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task21-input.apk','files/task21-input.apk')
$before = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
if ($LASTEXITCODE -notin @(0,1) -or $before) { throw 'Guest unexpectedly installed before run' }
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f','files/task21-act002.txt')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act002','--ez','import','true')
Start-Sleep -Seconds 2
$report = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task21-act002.txt
$reportText = ($report -join "`n")
$reportText | Set-Content (Join-Path $evidence 'act002.txt')
$logcat = & $adb -s $Serial logcat -d -s Task15:I '*:S'
($logcat -join "`n") | Set-Content (Join-Path $evidence 'act002-logcat.txt')
$after = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
"before=$($before -join ' ')`nafter=$($after -join ' ')" | Set-Content (Join-Path $evidence 'act002-pm-path.txt')
if ($LASTEXITCODE -notin @(0,1) -or $after) { throw 'Guest unexpectedly installed after run' }
$negative = $reportText -split "`n" | Where-Object { $_ -match 'negative\.|directGuest|system\.(pre|post)|hostSurvived' }
($negative -join "`n") | Set-Content (Join-Path $evidence 'negative-control.txt')
if ($reportText -notmatch "record.sha256=$sha" -or $reportText -notmatch 'verification=VALID' -or
    $reportText -notmatch 'classLoad=SUCCESS' -or $reportText -notmatch 'assignableToActivity=true' -or
    $reportText -notmatch 'constructor=SUCCESS' -or $reportText -notmatch 'instrumentation.newActivity=SUCCESS' -or
    $reportText -notmatch 'system.post.guestInstalled=false' -or $reportText -match 'directGuestLaunch=UNEXPECTED_SUCCESS' -or
    $reportText -notmatch 'hostSurvived=true') { throw 'ACT-002 evidence incomplete' }
