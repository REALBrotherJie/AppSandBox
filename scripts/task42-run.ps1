param([Parameter(Mandatory = $true)][string]$Serial)

$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task42/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
Remove-Item -LiteralPath (Join-Path $report 'result.txt') -ErrorAction SilentlyContinue
"status=RUNNING`nserial=$Serial" | Set-Content (Join-Path $report 'result.txt')

function A([string[]]$argv) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $adb -s $Serial @argv 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    $output | Tee-Object -FilePath (Join-Path $report 'adb-last.txt')
    if ($code -ne 0) { throw "adb failed: $($argv -join ' ')" }
    $output
}

function UiXml {
    $local = Join-Path $report 'ui.xml'
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        A @('shell', 'rm', '-f', '/sdcard/task42.xml') | Out-Null
        A @('shell', 'uiautomator', 'dump', '/sdcard/task42.xml') | Set-Content (Join-Path $report 'uiautomator-last.txt')
        A @('pull', '/sdcard/task42.xml', $local) | Out-Null
        if (Test-Path -LiteralPath $local) {
            $xml = Get-Content -Raw $local
            if ($xml -match '<hierarchy') { return $xml }
        }
        Start-Sleep -Milliseconds 400
    }
    throw 'uiautomator dump did not produce a hierarchy'
}

function AssertXml([string]$pattern, [string]$message) {
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        try { if ((UiXml) -match $pattern) { return } } catch {}
        Start-Sleep -Milliseconds 500
    }
    throw $message
}

function Tap([string]$prefix) {
    $pattern = 'text="(' + [regex]::Escape($prefix) + '[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        $xml = UiXml
        $match = [regex]::Match($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            $x = ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
            $y = ([int]$match.Groups[3].Value + [int]$match.Groups[5].Value) / 2
            A @('shell', 'input', 'tap', "$x", "$y") | Out-Null
            Start-Sleep -Milliseconds 700
            return
        }
        A @('shell', 'input', 'swipe', '540', '1700', '540', '500', '350') | Out-Null
        Start-Sleep -Milliseconds 400
    }
    throw "UI not found after bounded scroll: $prefix"
}

function RuntimePid {
    $processId = ((& $adb -s $Serial shell pidof com.example.appsandbox:guest_runtime 2>$null) -join '').Trim()
    if ($processId -match '^\d+$') { return $processId }
    return $null
}

function HostPid {
    $processId = ((& $adb -s $Serial shell pidof com.example.appsandbox 2>$null) -join '').Trim()
    if ($processId -match '^\d+$') { return $processId }
    return $null
}

function ProcessUid([string]$processId) {
    $status = A @('shell', 'cat', "/proc/$processId/status")
    $line = $status | Where-Object { $_ -match '^Uid:' } | Select-Object -First 1
    if ($line -match '^Uid:\s+(\d+)') { return $matches[1] }
    return $null
}

function RuntimeStatus([string]$name) {
    $path = Join-Path $report $name
    $status = (& $adb -s $Serial shell am start -W --activity-clear-task -n com.example.appsandbox/.automation.Task29AutomationActivity --ez runtimeStatus true 2>&1) -join "`n"
    $status | Set-Content $path
    Start-Sleep -Milliseconds 600
    UiXml | Set-Content (Join-Path $report "$name.ui.xml")
}

function KillRuntime([string]$processId) {
    A @('shell', 'run-as', 'com.example.appsandbox', 'kill', $processId) | Out-Null
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $current = RuntimePid
        if (!$current -or $current -ne $processId) { return }
        Start-Sleep -Milliseconds 300
    }
    throw "runtime process did not die: $processId"
}

function ImportGuest([string]$path) {
    A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'stagedApk', $path) | Out-Null
    AssertXml 'Imported successfully; supported Guest contract v2|SUCCESS: imported supported Guest contract v2' 'Guest import failed'
}

function CreateInstance {
    A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'createPackage', 'com.example.appsandbox.testguest') | Out-Null
    AssertXml 'SUCCESS: created|Guest Library' 'instance creation failed'
}

function OpenInstance([int]$ordinal) {
    A @('shell', 'am', 'start', '-W', '--activity-clear-task', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', 'com.example.appsandbox.testguest', '--ei', 'instanceOrdinal', "$ordinal") | Out-Null
    AssertXml 'Guest workspace.*contract=v2' "workspace $ordinal did not open"
}

function RuntimeDeletedTokenCheck([string]$instanceId) {
    A @('shell', 'am', 'start', '-W', '--activity-clear-task', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'runtimeDeletedTokenCheck', $instanceId) | Out-Null
    AssertXml 'SUCCESS: old token rejected deleted_instance' 'old runtime token was not rejected after instance deletion'
}

function CurrentId {
    $match = [regex]::Match((UiXml), 'instance=([0-9a-f-]{36})', 'IgnoreCase')
    if (!$match.Success) { throw 'workspace instance identity missing' }
    $match.Groups[1].Value.ToLowerInvariant()
}

try {
    $api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
    if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }

    $hostApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
    $guestApk = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
    A @('install', '-r', $hostApk) | Out-Null
    A @('shell', 'pm', 'clear', 'com.example.appsandbox') | Out-Null
    A @('push', $guestApk, '/data/local/tmp/task42-guest.apk') | Out-Null
    A @('shell', 'run-as', 'com.example.appsandbox', 'mkdir', '-p', 'files') | Out-Null
    A @('shell', 'run-as', 'com.example.appsandbox', 'cp', '/data/local/tmp/task42-guest.apk', 'files/task42-guest.apk') | Out-Null

    ImportGuest '/data/data/com.example.appsandbox/files/task42-guest.apk'
    CreateInstance
    CreateInstance

    OpenInstance 0
    $idA = CurrentId
    AssertXml 'Guest runtime connecting|Counter: 0' 'runtime initial state missing'
    AssertXml 'Counter: 0' 'runtime did not read initial A state'
    Tap 'Guest Increment'
    AssertXml 'Counter: 1' 'A increment did not commit through runtime'
    $hostBefore = HostPid
    $runtimeBefore = RuntimePid
    if (!$hostBefore -or !$runtimeBefore -or $hostBefore -eq $runtimeBefore) { throw 'host/runtime process boundary missing' }
    $hostUid = ProcessUid $hostBefore
    $runtimeUid = ProcessUid $runtimeBefore
    if (!$hostUid -or !$runtimeUid -or $hostUid -ne $runtimeUid) { throw "host/runtime UID boundary mismatch host=$hostUid runtime=$runtimeUid" }

    OpenInstance 1
    $idB = CurrentId
    if ($idA -eq $idB) { throw 'A/B identity collision' }
    AssertXml 'Counter: 0' 'B leaked A state'
    Tap 'Guest Increment'
    Tap 'Guest Increment'
    AssertXml 'Counter: 2' 'B did not commit through runtime'

    OpenInstance 0
    AssertXml 'Counter: 1' 'A state did not recover after runtime death'
    KillRuntime $runtimeBefore
    $afterKill = RuntimePid
    if ($afterKill -eq $runtimeBefore) { throw 'runtime process did not die' }
    $hostAfterKill = HostPid
    if ($hostAfterKill -ne $hostBefore) { throw "host process did not survive runtime death: before=$hostBefore after=$hostAfterKill" }
    AssertXml 'Guest runtime connecting|Counter: 1' 'workspace did not remain visible after runtime death'
    AssertXml 'Counter: 1' 'A state did not reconnect after runtime death'
    Tap 'Guest Increment'
    AssertXml 'Counter: 2' 'A could not continue after runtime restart'
    $runtimeAfter = RuntimePid
    if (!$runtimeAfter) { throw 'runtime did not restart' }
    if ($runtimeAfter -eq $runtimeBefore) { throw 'runtime pid did not change after kill' }

    OpenInstance 1
    AssertXml 'Counter: 2' 'B state changed after A runtime restart'
    RuntimeDeletedTokenCheck $idA
    OpenInstance 0
    $remaining = CurrentId
    if ($remaining -ne $idB) { throw "B was not the remaining instance after deleting A: $remaining" }
    AssertXml 'Counter: 2' 'B unavailable after deleting A'

    A @('shell', 'am', 'force-stop', 'com.example.appsandbox') | Out-Null
    OpenInstance 0
    AssertXml 'Counter: 2' 'force-stop did not restore B through new runtime'

    if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '') { throw 'Guest package must not be installed' }
    $final = (& $adb -s $Serial shell dumpsys activity activities) -join "`n"
    $final | Set-Content (Join-Path $report 'final-activities.txt')
    if ($final -match 'com.example.appsandbox.testguest/.runtime.GuestMainActivity') { throw 'Guest ActivityRecord detected' }

    "status=PASS`nserial=$Serial`napi=$api`ninstanceA=$idA`ninstanceB=$idB`nhostPid=$hostBefore`nhostUid=$hostUid`nruntimeUid=$runtimeUid`nruntimePidBefore=$runtimeBefore`nruntimePidAfter=$runtimeAfter`nsameUidSeparateProcess=PASS`nruntimeDeathRecovery=PASS`ndeletedInstanceOldToken=PASS`nguestInstalled=false" | Set-Content (Join-Path $report 'result.txt')
} catch {
    "status=FAIL`nserial=$Serial`nerror=$($_.Exception.Message)" | Set-Content (Join-Path $report 'result.txt')
    throw
}
