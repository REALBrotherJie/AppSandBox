param(
    [Parameter(Mandatory=$true)]
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$evidence = Join-Path $root "docs/experiments/evidence/task26/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
function Read-AppFile([string]$name) { (& $adb -s $Serial shell run-as com.example.appsandbox cat "files/$name") -join "`n" }
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
$hostSha = (Get-FileHash $hostApk -Algorithm SHA256).Hash.ToLowerInvariant()
"serial=$Serial`napi=$api`nabi=$abi`npageSize=$page`nhostApkSha256=$hostSha" | Set-Content (Join-Path $evidence 'device.txt')
A @('install','-r',$hostApk)
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f','files/task26-act004b-p0.txt','files/task22-stub-valid.txt')
A @('logcat','-c')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act004b-p0')
Start-Sleep -Seconds 2
$report = Read-AppFile 'task26-act004b-p0.txt'
$stub = Read-AppFile 'task22-stub-valid.txt'
$report | Set-Content (Join-Path $evidence 'surface-inventory.txt')
$report | Set-Content (Join-Path $evidence 'runtime-observation.txt')
$stub | Set-Content (Join-Path $evidence 'host-external-lifecycle.txt')
(& $adb -s $Serial logcat -d -s AppSandbox.Act003:I Task15:I '*:S') -join "`n" | Set-Content (Join-Path $evidence 'act004b-p0-logcat.txt')
(& $adb -s $Serial shell dumpsys activity activities) -join "`n" | Set-Content (Join-Path $evidence 'act004b-p0-dumpsys-activity.txt')
(& $adb -s $Serial shell dumpsys window windows) -join "`n" | Set-Content (Join-Path $evidence 'act004b-p0-dumpsys-window.txt')
$pm = (& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join ' '
"guestPmPath=$pm" | Set-Content (Join-Path $evidence 'pm-path.txt')
"failureOrAbort=none`ninternalObservation=$($report | Select-String 'internalObservationOutcome')" | Set-Content (Join-Path $evidence 'failure-or-abort.txt')
$all = "$report`n$stub"
if ($pm -or $report -match 'guestActivityConstructed=true' -or $report -match 'guestLifecycleExecuted=true' -or
    $report -match 'transactionMutation=YES' -or $report -match 'internalInvocation=YES' -or
    $stub -notmatch 'componentName=com.example.appsandbox/.experiments.act003.Act003StubActivity' -or
    $stub -notmatch 'decor.windowToken.nonNull=true' -or $stub -notmatch 'decor.applicationWindowToken.nonNull=true' -or
    $stub -notmatch 'hostSurvived=true' -or $stub -notmatch 'lifecycle=onCreate' -or
    $report -notmatch 'internalObservationOutcome=(STATIC_SURFACE_ONLY|FAILED_CLOSED_OR_PARTIAL)') {
    throw 'ACT-004B-P0 evidence incomplete'
}
