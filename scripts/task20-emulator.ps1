param(
    [string]$Avd = 'Pixel_3a_API_36_extension_level_19_x86_64',
    [int]$TimeoutSeconds = 180
)
$ErrorActionPreference = 'Stop'
$sdk = 'D:/Company/Install/Android/SDK'
$adb = "$sdk/platform-tools/adb.exe"
$emulator = "$sdk/emulator/emulator.exe"
$root = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $root 'docs/experiments/evidence/task20/api36-avd'
$data = Join-Path $env:TEMP 'appsandbox-task20-api36-avd-v2'
New-Item -ItemType Directory -Force $evidence | Out-Null
New-Item -ItemType Directory -Force $data | Out-Null
$image = "$sdk/system-images/android-36/google_apis/x86_64"
if (-not (Test-Path "$image/encryptionkey.img")) { throw "Missing system image encryption key: $image" }
if (-not (Test-Path (Join-Path $data 'encryptionkey.img'))) { Copy-Item "$image/encryptionkey.img" (Join-Path $data 'encryptionkey.img') }
$stdout = Join-Path $evidence 'emulator.stdout.txt'
$stderr = Join-Path $evidence 'emulator.stderr.txt'
$args = "-avd $Avd -sysdir `"$image`" -datadir `"$data`" -data `"$data/userdata.img`" -encryption-key `"$data/encryptionkey.img`" -skin 1080x1920 -no-window -no-audio -no-snapshot -no-boot-anim -cores 2 -gpu swiftshader_indirect -feature -Vulkan"
$proc = Start-Process -FilePath $emulator -ArgumentList $args -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
"pid=$($proc.Id)`navd=$Avd`ndata=$data`nargs=$args" | Set-Content (Join-Path $evidence 'avd-start.txt')
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$serial = $null
while ((Get-Date) -lt $deadline) {
    $lines = & $adb devices
    $candidate = $lines | Where-Object { $_ -match '^emulator-\d+\s+(device|offline|unauthorized)' }
    if ($candidate -match '^(emulator-\d+)\s+device') { $serial = $Matches[1]; break }
    Start-Sleep -Seconds 5
}
(& $adb devices -l) | Set-Content (Join-Path $evidence 'device-inventory.txt')
if (-not $serial) {
    $state = (& $adb get-state 2>&1) -join "`n"
    $sdkProp = (& $adb shell getprop ro.build.version.sdk 2>&1) -join "`n"
    "api36Available=false`nblockingReason=AVD did not reach adb device before timeout`nadbState=$state`nro.build.version.sdk=$sdkProp`nprocessExited=$($proc.HasExited)" | Set-Content (Join-Path $evidence 'api36-blocked.txt')
    if ($proc.HasExited) { "exitCode=$($proc.ExitCode)" | Add-Content (Join-Path $evidence 'avd-start-failure.txt') }
    exit 2
}
$api = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
if ($api -ne '36') { throw "Require API36, got API $api on $serial" }
$product = (& $adb -s $serial shell getprop ro.product.name).Trim()
$model = (& $adb -s $serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $serial shell getconf PAGESIZE 2>$null).Trim()
"serial=$serial`napi=$api`nproduct=$product`nmodel=$model`nabi=$abi`npageSize=$page`nemulatorPid=$($proc.Id)" | Set-Content (Join-Path $evidence 'device.txt')
Write-Output $serial
