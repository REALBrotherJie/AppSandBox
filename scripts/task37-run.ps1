param([Parameter(Mandatory = $true)][string]$Serial, [string]$RunLabel = 'latest')
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
if ($RunLabel -notmatch '^[a-zA-Z0-9_-]+$') { throw 'RunLabel must be alphanumeric, underscore, or hyphen' }
$report = Join-Path $root "build/reports/task37/$Serial/$RunLabel"
New-Item -ItemType Directory -Force $report | Out-Null
$result = Join-Path $report 'result.txt'
Remove-Item -LiteralPath $result -ErrorAction SilentlyContinue
"status=RUNNING`nserial=$Serial`nrun=$RunLabel" | Set-Content $result
trap {
    "status=FAIL`nserial=$Serial`nrun=$RunLabel`nerror=$($_.Exception.Message)" | Set-Content $result
    throw
}

function A([string[]]$argv) {
    & $adb -s $Serial @argv 2>&1 | Tee-Object -FilePath (Join-Path $report 'adb-last.txt')
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" }
}

function UiXml {
    $local = Join-Path $report 'ui.xml'
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        A @('shell', 'rm', '-f', '/sdcard/task37-ui.xml') | Out-Null
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $adb -s $Serial shell uiautomator dump /sdcard/task37-ui.xml 2>&1 |
            Tee-Object -FilePath (Join-Path $report 'uiautomator-last.txt') | Out-Null
        $ErrorActionPreference = $previous
        try {
            A @('pull', '/sdcard/task37-ui.xml', $local) | Out-Null
            $xml = Get-Content -Raw $local
            if ($xml -match '<hierarchy') { return $xml }
        } catch {}
        Start-Sleep -Milliseconds 400
    }
    throw 'uiautomator dump did not produce a fresh hierarchy'
}

function AssertXml([string]$pattern, [string]$message) {
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        try {
            if ((UiXml) -match $pattern) { return }
        } catch {}
        Start-Sleep -Milliseconds 500
    }
    throw $message
}

function Tap([string]$prefix) {
    $pattern = 'text="(' + [regex]::Escape($prefix) + '[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    for ($attempt = 0; $attempt -lt 6; $attempt++) {
        try { $xml = UiXml } catch { Start-Sleep -Milliseconds 400; continue }
        $match = [regex]::Match($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            $x = ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
            $y = ([int]$match.Groups[3].Value + [int]$match.Groups[5].Value) / 2
            A @('shell', 'input', 'tap', "$x", "$y") | Out-Null
            Start-Sleep -Milliseconds 600
            return
        }
        A @('shell', 'input', 'swipe', '540', '1700', '540', '500', '450') | Out-Null
        Start-Sleep -Milliseconds 400
    }
    throw "UI not found after bounded scroll: $prefix"
}

function WaitForeground([string]$activity, [string]$uiPattern) {
    for ($poll = 0; $poll -lt 10; $poll++) {
        $activities = (& $adb -s $Serial shell dumpsys activity activities) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw 'dumpsys activity failed' }
        if ($activities -match ('ResumedActivity: ActivityRecord\{[^\r\n]*com\.example\.appsandbox/\.' + $activity)) {
            try { if ((UiXml) -match $uiPattern) { return $true } } catch {}
        }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

function Import([string]$path) {
    A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'stagedApk', $path) | Out-Null
    Start-Sleep -Seconds 1
}

function CreateInstance {
    A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'createPackage', 'com.example.appsandbox.testguest') | Out-Null
    Start-Sleep -Milliseconds 600
}

function OpenPackage([int]$ordinal) {
    for ($launch = 0; $launch -lt 3; $launch++) {
        A @('shell', 'am', 'start', '-W', '--activity-clear-task', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', 'com.example.appsandbox.testguest', '--ei', 'instanceOrdinal', "$ordinal") | Out-Null
        if (WaitForeground 'GuestWorkspaceActivity' 'instance=[0-9a-f-]{36}') { return }
    }
    throw "workspace did not reach foreground: ordinal=$ordinal"
}

function OpenStale([string]$instanceId) {
    A @('shell', 'am', 'start', '-W', '--activity-clear-task', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'staleInstanceId', $instanceId) | Out-Null
    if (!(WaitForeground 'GuestWorkspaceActivity' 'Instance unavailable or registry is corrupted')) {
        throw 'stale workspace did not reach foreground'
    }
}

function CurrentId {
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        try {
            $match = [regex]::Match((UiXml), 'instance=([0-9a-f-]{36})', 'IgnoreCase')
            if ($match.Success) { return $match.Groups[1].Value.ToLowerInvariant() }
        } catch {}
        Start-Sleep -Milliseconds 400
    }
    throw 'workspace instance identity missing'
}

function ActivityDump([string]$name) {
    $path = Join-Path $report $name
    $output = & $adb -s $Serial shell dumpsys activity activities 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'dumpsys activity failed' }
    $output | Set-Content $path
    ($output -join "`n")
}

function RecentDump([string]$name) {
    $path = Join-Path $report $name
    $output = & $adb -s $Serial shell dumpsys activity recents 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'dumpsys activity recents failed' }
    $output | Set-Content $path
    ($output -join "`n")
}

function TaskIdFor([string]$dump, [string]$instanceId) {
    $uri = [regex]::Escape("appsandbox://workspace/$instanceId")
    $match = [regex]::Match($dump, '(?ms)^\s*\* Task\{[^\r\n]*#(\d+)[^\r\n]*\r?\n(?:(?!^\s*\* Task\{).)*?' + $uri)
    if ($match.Success) { return [int]$match.Groups[1].Value }
    $match = [regex]::Match($dump, '(?ms)^\s+\* Recent #[^\r\n]*.*?' + $uri + '.*?taskId=(\d+)')
    if ($match.Success) { return [int]$match.Groups[1].Value }
    throw "taskId missing for $instanceId"
}

function WaitTaskId([string]$name, [string]$instanceId) {
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $dump = ActivityDump $name
        try { return (TaskIdFor $dump $instanceId) } catch {}
        Start-Sleep -Milliseconds 400
    }
    throw "taskId missing after bounded polling for $instanceId"
}

function StartLauncher {
    A @('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-n', 'com.example.appsandbox/.MainActivity') | Out-Null
    if (!(WaitForeground 'MainActivity' 'Guest Library')) { throw 'MainActivity did not reach foreground' }
}

function PressHome {
    A @('shell', 'input', 'keyevent', '3') | Out-Null
    Start-Sleep -Milliseconds 700
}

function OpenRecents {
    A @('shell', 'input', 'keyevent', '187') | Out-Null
    Start-Sleep -Seconds 1
}

$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }

$hostApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$guestApk = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
A @('install', '-r', $hostApk)
A @('shell', 'pm', 'clear', 'com.example.appsandbox')
A @('push', $guestApk, '/data/local/tmp/task37.apk')
A @('shell', 'run-as', 'com.example.appsandbox', 'mkdir', '-p', 'files')
A @('shell', 'run-as', 'com.example.appsandbox', 'cp', '/data/local/tmp/task37.apk', 'files/task37.apk')

Import '/data/data/com.example.appsandbox/files/task37.apk'
CreateInstance
CreateInstance

OpenPackage 0
$idA = CurrentId
Tap 'Guest Increment'
AssertXml 'Counter: 1' 'A action failed'
$beforeRepeat = ActivityDump 'before-repeat.txt'
$taskA = WaitTaskId 'before-repeat.txt' $idA
$beforeRepeat = Get-Content -Raw (Join-Path $report 'before-repeat.txt')

PressHome
StartLauncher
AssertXml 'Guest Library' 'launcher did not restore MainActivity'
AssertXml 'Focus' 'Library did not expose Focus for the open instance'

OpenPackage 0
$afterRepeat = ActivityDump 'repeat-a.txt'
if ((WaitTaskId 'repeat-a.txt' $idA) -ne $taskA) { throw 'repeated instance did not focus the existing task' }

OpenPackage 1
$idB = CurrentId
if ($idA -eq $idB) { throw 'A/B identity collision' }
AssertXml 'Counter: 0' 'B leaked A state'
Tap 'Guest Increment'
Tap 'Guest Increment'
AssertXml 'Counter: 2' 'B action failed'
$simultaneous = ActivityDump 'simultaneous.txt'
$taskB = WaitTaskId 'simultaneous.txt' $idB
$simultaneous = Get-Content -Raw (Join-Path $report 'simultaneous.txt')
if ($taskA -eq $taskB) { throw 'different instances reused one taskId' }

PressHome
OpenRecents
$recents = UiXml
$recentDump = RecentDump 'recents.txt'
$labelA = 'com\.example\.appsandbox\.testguest / ' + [regex]::Escape($idA.Substring(0, 8))
$labelB = 'com\.example\.appsandbox\.testguest / ' + [regex]::Escape($idB.Substring(0, 8))
if ($beforeRepeat -notmatch $labelA -or $simultaneous -notmatch $labelB) {
    throw 'workspace task descriptions did not retain package/instance labels'
}
if ($recents -notmatch 'com\.example\.appsandbox\.testguest' -or
    ($recents -notmatch [regex]::Escape($idA.Substring(0, 8)) -and $recents -notmatch [regex]::Escape($idB.Substring(0, 8))) -or
    $recentDump -notmatch [regex]::Escape("appsandbox://workspace/$idA") -or
    $recentDump -notmatch [regex]::Escape("appsandbox://workspace/$idB")) {
    throw 'recents did not expose package/instance labels for both workspaces'
}

A @('shell', 'am', 'force-stop', 'com.example.appsandbox') | Out-Null
OpenPackage 0
AssertXml 'Counter: 1' 'A force-stop/relaunch recovery lost state'
OpenPackage 1
AssertXml 'Counter: 2' 'B force-stop/relaunch recovery lost state'

OpenPackage 0
Tap 'Close workspace'
Start-Sleep -Seconds 1
StartLauncher
AssertXml 'Open' 'closed A did not return to Open state'
OpenPackage 0
AssertXml 'Counter: 1' 'closed A could not be reopened'

OpenPackage 0
Tap 'Delete current instance'
Tap 'Confirm delete instance'
Start-Sleep -Seconds 1
OpenStale $idA
AssertXml 'Instance unavailable or registry is corrupted' 'deleted A stale task did not fail closed'
OpenPackage 0
AssertXml ("instance=" + [regex]::Escape($idB)) 'B identity was not restored'
AssertXml 'Counter: 2' 'B was not retained after deleting A'

if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '') { throw 'Guest package must not be installed' }
$final = ActivityDump 'final.txt'
if ($final -match 'com.example.appsandbox.testguest/.runtime.GuestMainActivity') { throw 'Guest ActivityRecord detected' }
$finalTaskB = WaitTaskId 'final.txt' $idB
$final = Get-Content -Raw (Join-Path $report 'final.txt')
if ($final -notmatch [regex]::Escape("appsandbox://workspace/$idB") -or
    $final -notmatch 'ResumedActivity: ActivityRecord\{[^\r\n]*com\.example\.appsandbox/\.GuestWorkspaceActivity' -or
    (UiXml) -notmatch ([regex]::Escape("instance=$idB") + '.*Counter: 2')) {
    throw 'final B document, foreground Activity, or UI state is inconsistent'
}

@"
status=PASS
serial=$Serial
api=$api
run=$RunLabel
instanceA=$idA
documentA=appsandbox://workspace/$idA
taskA=$taskA
instanceB=$idB
documentB=appsandbox://workspace/$idB
taskB=$taskB
finalTaskB=$finalTaskB
finalUi=instanceB,Counter:2
launcherHome=PASS
recents=PASS
repeatInstanceFocus=PASS
distinctInstanceTasks=PASS
forceStopRelaunch=PASS
closeReopen=PASS
deleteStaleFailClosed=PASS
guestActivityRecord=false
guestInstalled=false
"@ | Set-Content $result
