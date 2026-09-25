param(
    [Parameter(Mandatory=$true)]
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$hostSha = (Get-FileHash $hostApk -Algorithm SHA256).Hash.ToLowerInvariant()
$guestSha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = Join-Path $root "docs/experiments/evidence/task22/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
$product = (& $adb -s $Serial shell getprop ro.product.name).Trim()
$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
"serial=$Serial`napi=$api`nproduct=$product`nmodel=$model`nabi=$abi`npageSize=$page`nhostApkSha256=$hostSha`nguestApkSha256=$guestSha" | Set-Content (Join-Path $evidence 'device.txt')
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task22-input.apk')
A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task22-input.apk','files/task22-input.apk')
$before = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
if ($LASTEXITCODE -notin @(0,1) -or $before) { throw 'Guest unexpectedly installed before run' }
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f','files/task22-act003.txt','files/task22-stub-valid.txt','files/task22-stub-invalid.txt')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act003','--ez','import','true')
Start-Sleep -Seconds 2
$runner = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task22-act003.txt
$stub = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task22-stub-valid.txt
$reportText = (($runner + $stub) -join "`n")
$reportText | Set-Content (Join-Path $evidence 'act003.txt')
$activityDump = & $adb -s $Serial shell dumpsys activity activities
($activityDump -join "`n") | Set-Content (Join-Path $evidence 'act003-dumpsys-activity.txt')
$windowDump = & $adb -s $Serial shell dumpsys window windows
($windowDump -join "`n") | Set-Content (Join-Path $evidence 'act003-dumpsys-window.txt')
$logcat = & $adb -s $Serial logcat -d -s AppSandbox.Act003:I Task15:I '*:S'
($logcat -join "`n") | Set-Content (Join-Path $evidence 'act003-logcat.txt')
$afterValid = & $adb -s $Serial shell pm path com.example.appsandbox.testguest

A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act003-invalid','--ez','import','false')
Start-Sleep -Seconds 1
$invalidRunner = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task22-act003-invalid.txt
$invalidStub = & $adb -s $Serial shell run-as com.example.appsandbox cat files/task22-stub-invalid.txt
$invalidText = (($invalidRunner + $invalidStub) -join "`n")
$invalidText | Set-Content (Join-Path $evidence 'invalid-launch-id.txt')
$after = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
"before=$($before -join ' ')`nafterValid=$($afterValid -join ' ')`nafterInvalid=$($after -join ' ')" | Set-Content (Join-Path $evidence 'act003-pm-path.txt')
($reportText -split "`n" | Where-Object { $_ -match 'directGuest|guest.systemInstalled|hostSurvived' }) -join "`n" | Set-Content (Join-Path $evidence 'direct-guest-launch.txt')
if ($LASTEXITCODE -notin @(0,1) -or $afterValid -or $after) { throw 'Guest unexpectedly installed' }
if ($reportText -notmatch "record.sha256=$guestSha" -or $reportText -notmatch 'verification=VALID' -or
    $reportText -notmatch 'componentName=com.example.appsandbox/.experiments.act003.Act003StubActivity' -or
    $reportText -notmatch 'decor.windowToken.nonNull=true' -or $reportText -notmatch 'decor.applicationWindowToken.nonNull=true' -or
    ($activityDump -join "`n") -notmatch 'com.example.appsandbox/.experiments.act003.Act003StubActivity' -or
    $reportText -match 'directGuestLaunch=UNEXPECTED_SUCCESS' -or $reportText -notmatch 'guestActivityInstantiated=false' -or
    $invalidText -notmatch 'launchResult=INVALID_LAUNCH_ID' -or $invalidText -notmatch 'hostSurvived=true') { throw 'ACT-003 evidence incomplete' }
