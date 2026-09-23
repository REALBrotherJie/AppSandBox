param(
    [string]$Serial = 'emulator-5554',
    [string[]]$Modes = @('v1', 'exp001', 'exp002', 'exp003a', 'exp003b0'),
    [switch]$Install,
    [switch]$ImportGuest
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $root "docs/experiments/evidence/task15/$Serial"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
function Adb {
    & $adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $args" }
}
if ($Install) { Adb install -r "$root/app/build/outputs/apk/debug/app-debug.apk" }
if ($ImportGuest) {
    Adb push "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk" /data/local/tmp/task15-input.apk
    Adb shell run-as com.example.appsandbox mkdir -p files
    Adb shell run-as com.example.appsandbox cp /data/local/tmp/task15-input.apk files/task15-input.apk
}
@(
    Adb shell getprop ro.product.model
    Adb shell getprop ro.build.version.sdk
    Adb shell getprop ro.build.version.release
    Adb shell getprop ro.product.cpu.abilist
    Adb shell getconf PAGE_SIZE
) | Set-Content "$evidence/device.txt"
$first = $true
foreach ($mode in $Modes) {
    $before = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
    if ($LASTEXITCODE -notin @(0, 1)) { throw 'pm path failed' }
    if ($before) { throw 'Guest must remain uninstalled' }
    Adb shell am force-stop com.example.appsandbox
    Adb logcat -c
    $arguments = @('shell', 'am', 'start', '-W', '-n', 'com.example.appsandbox/.experiments.v1.ExperimentActivity', '--es', 'mode', $mode)
    if ($ImportGuest -and $first) { $arguments += @('--ez', 'import', 'true') }
    Adb @arguments
    Start-Sleep -Seconds 2
    Adb shell run-as com.example.appsandbox cat "files/task15-$mode.txt" | Set-Content "$evidence/$mode.txt"
    Adb logcat -d -s Task15:I AppSandbox.Exp003B0P:I AndroidRuntime:E | Set-Content "$evidence/$mode-logcat.txt"
    $after = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
    if ($LASTEXITCODE -notin @(0, 1)) { throw 'pm path failed' }
    if ($after) { throw 'Guest unexpectedly installed' }
    "before=empty; after=empty" | Set-Content "$evidence/$mode-pm-path.txt"
    $first = $false
}
