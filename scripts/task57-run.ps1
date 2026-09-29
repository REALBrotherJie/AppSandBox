param(
  [string]$Serial = 'emulator-5554',
  [int]$ExpectedApi = 36,
  [Parameter(Mandatory)][string]$HostApkPath,
  [Parameter(Mandatory)][string]$NormalApkPath,
  [Parameter(Mandatory)][string]$ExpectedHostSha256,
  [Parameter(Mandatory)][string]$ExpectedNormalSha256
)
$ErrorActionPreference = 'Stop'
if ($ExpectedApi -notin 31,36) { throw 'ExpectedApi must be 31 or 36' }
if ($ExpectedApi -eq 36 -and $Serial -ne 'emulator-5554') { throw 'API36 requires emulator-5554' }
if ($ExpectedApi -eq 31 -and $Serial -ne '7b670025') { throw 'API31 requires 7b670025' }
$root = Split-Path $PSScriptRoot -Parent
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$run = [guid]::NewGuid().ToString('N')
$report = Join-Path $root "build/reports/task57/$Serial/$run"
New-Item -ItemType Directory -Force $report | Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1')
function A([string[]]$args, [int]$timeout = 30) {
    Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $args -TimeoutSec $timeout -ReportDir $report
}
function Sha([string]$path) { (Get-FileHash -Algorithm SHA256 -LiteralPath (Resolve-Path $path)).Hash.ToLowerInvariant() }
if ((Sha $HostApkPath) -ne $ExpectedHostSha256.ToLowerInvariant()) { throw 'Host SHA-256 mismatch' }
if ((Sha $NormalApkPath) -ne $ExpectedNormalSha256.ToLowerInvariant()) { throw 'Guest SHA-256 mismatch' }
if ((A @('shell', 'getprop', 'ro.build.version.sdk')).Trim() -ne [string]$ExpectedApi) { throw 'unexpected API' }
function Value([string]$text, [string]$key) {
    $m = [regex]::Match($text, "(?m)^$([regex]::Escape($key))=(.*)$")
    if (!$m.Success) { throw "missing $key" }
    $m.Groups[2].Value.Trim()
}
function WaitReport([string]$id, [int]$seconds = 30) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        $text = A @('shell', 'run-as', 'com.example.appsandbox', 'sh', '-c', "'if [ -f files/task57-$id.result ]; then cat files/task57-$id.result; else echo NOT_READY; fi'")
        if ($text -match '(?m)^FINAL=1\s*$') {
            if ((Value $text 'commandId') -ne $id) { throw 'commandId mismatch' }
            return $text
        }
        if ($text -notmatch '(?m)^NOT_READY\s*$') { throw "unexpected report: $text" }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "report timeout $id"
}
function RunTask([string]$action, [hashtable]$extras = @{}) {
    $id = [guid]::NewGuid().ToString('N')
    $args = @('shell', 'am', 'start', '-W', '--activity-clear-task', '-n',
        'com.example.appsandbox/.automation.Task57AutomationActivity',
        '--es', 'commandId', $id, '--es', 'action', $action)
    foreach ($entry in $extras.GetEnumerator()) { $args += @('--es', $entry.Key, [string]$entry.Value) }
    A $args | Out-Null
    WaitReport $id
}
function UiXml {
    $path = Join-Path $report 'ui.xml'
    A @('shell', 'rm', '-f', '/sdcard/task57-ui.xml') | Out-Null
    A @('shell', 'uiautomator', 'dump', '/sdcard/task57-ui.xml') | Out-Null
    A @('pull', '/sdcard/task57-ui.xml', $path) | Out-Null
    Get-Content -Raw $path
}
function AssertUi([string]$pattern, [string]$message) {
    for ($i = 0; $i -lt 15; $i++) {
        try { if ((UiXml) -match $pattern) { return } } catch {}
        Start-Sleep -Milliseconds 400
    }
    throw $message
}
function Tap([string]$prefix) {
    $pattern = 'text="(' + [regex]::Escape($prefix) + '[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    for ($i = 0; $i -lt 10; $i++) {
        try {
            $xml = UiXml
            $m = [regex]::Match($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
            if ($m.Success) {
                $x = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
                $y = ([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2
                A @('shell', 'input', 'tap', "$x", "$y") | Out-Null
                Start-Sleep -Milliseconds 700
                return
            }
        } catch {}
        A @('shell', 'input', 'swipe', '540', '1700', '540', '500', '450') | Out-Null
        Start-Sleep -Milliseconds 400
    }
    throw "UI element not found: $prefix"
}
function OpenInstance([string]$prefix) {
    RunTask 'openWorkspace' @{ instancePrefix = $prefix } | Out-Null
    AssertUi ('Guest workspace.*instance=' + [regex]::Escape($prefix)) "Workspace not opened for $prefix"
}
function StateJson([string]$instance) {
    A @('shell', 'run-as', 'com.example.appsandbox', 'cat', "files/guest-instances/$instance/files/logical-activity.json")
}
function WaitRuntimeReport([string]$text) {
    if ((Value $text 'status') -ne 'PASS') { throw "task failed: $text" }
}

$freeze = Join-Path $report 'freeze'
New-Item -ItemType Directory -Force $freeze | Out-Null
Copy-Item (Resolve-Path $HostApkPath) (Join-Path $freeze 'host.apk') -Force
Copy-Item (Resolve-Path $NormalApkPath) (Join-Path $freeze 'normal.apk') -Force
A @('install', '-r', (Join-Path $freeze 'host.apk')) | Out-Null
A @('shell', 'pm', 'clear', 'com.example.appsandbox') | Out-Null
A @('shell', 'run-as', 'com.example.appsandbox', 'mkdir', '-p', 'files') | Out-Null
A @('push', (Join-Path $freeze 'normal.apk'), '/data/local/tmp/task57-normal.apk') | Out-Null
A @('shell', 'run-as', 'com.example.appsandbox', 'cp', '/data/local/tmp/task57-normal.apk', 'files/task57-normal-a.apk') | Out-Null
A @('shell', 'run-as', 'com.example.appsandbox', 'cp', '/data/local/tmp/task57-normal.apk', 'files/task57-normal-b.apk') | Out-Null
$setup = RunTask 'setup' @{
    normalApkA = '/data/data/com.example.appsandbox/files/task57-normal-a.apk'
    normalApkB = '/data/data/com.example.appsandbox/files/task57-normal-b.apk'
}
if ((Value $setup 'status') -ne 'PASS') { throw "setup failed: $setup" }
$idA = Value $setup 'instanceA'; $idB = Value $setup 'instanceB'
$revA = Value $setup 'revisionA'; $revB = Value $setup 'revisionB'
$pkg = Value $setup 'packageName'; $exported = Value $setup 'exportedActivity'
$private = Value $setup 'nonExportedActivity'
if ($idA -eq $idB -or $revA -eq $revB) { throw 'A/B setup did not isolate instances and revisions' }
if ((Value $setup 'shaA') -ne $ExpectedNormalSha256.ToLowerInvariant() -or
    (Value $setup 'shaB') -ne $ExpectedNormalSha256.ToLowerInvariant()) { throw 'normal Guest SHA mismatch in registry' }

$negative = RunTask 'negative' @{ packageName = $pkg; revisionId = $revA; nonExportedActivity = $private }
if ((Value $negative 'nonExported') -ne 'REJECTED' -or (Value $negative 'unknown') -ne 'REJECTED') { throw 'negative resolver gate failed' }

OpenInstance $idA.Substring(0, 8)
AssertUi ('Open Activity ' + [regex]::Escape($exported.Substring($exported.LastIndexOf('.') + 1))) 'exported Activity action missing'
Tap ('Open Activity ' + $exported.Substring($exported.LastIndexOf('.') + 1))
AssertUi 'Logical Activity' 'carrier did not open'
AssertUi ('component=' + [regex]::Escape($exported)) 'logical component identity missing'
AssertUi ('instance=' + [regex]::Escape($idA)) 'logical instance identity missing'
Tap 'Guest Increment'
AssertUi 'Counter: 1' 'carrier Guest action failed'
$openState = StateJson $idA
if ($openState -notmatch '"state":"OPEN"') { throw 'logical Activity state was not persisted OPEN' }
Tap 'Return logical result'
Start-Sleep -Seconds 1
$closedState = StateJson $idA
if ($closedState -notmatch '"state":"CLOSED"' -or $closedState -notmatch '"resultMessage":"completed"') { throw 'logical result was not persisted' }
AssertUi 'Guest workspace' 'result did not return to Workspace'

Tap ('Open Activity ' + $exported.Substring($exported.LastIndexOf('.') + 1))
AssertUi 'Logical Activity' 'logical Activity did not reopen'
$reopenState = StateJson $idA
$firstLaunch = [regex]::Match($closedState, '"launchId":"([^"]+)"').Groups[1].Value
$secondLaunch = [regex]::Match($reopenState, '"launchId":"([^"]+)"').Groups[1].Value
if (!$firstLaunch -or !$secondLaunch -or $firstLaunch -eq $secondLaunch) { throw 'reopen reused stale launch identity' }
A @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
if ((StateJson $idA) -notmatch '"resultMessage":"back"') { throw 'carrier back result not persisted' }

$malformed = RunTask 'malformed'
AssertUi 'Logical Activity unavailable' 'malformed carrier input did not fail closed'
A @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 600

OpenInstance $idB.Substring(0, 8)
AssertUi ('instance=' + [regex]::Escape($idB)) 'B workspace identity missing'
AssertUi 'Counter: 0' 'B leaked A logical state'
Tap ('Open Activity ' + $exported.Substring($exported.LastIndexOf('.') + 1))
AssertUi ('instance=' + [regex]::Escape($idB)) 'B carrier identity missing'
A @('shell', 'input', 'keyevent', '3') | Out-Null
Start-Sleep -Milliseconds 600
$packages = A @('shell', 'pm', 'list', 'packages', 'com.example.appsandbox.testguest')
if ($packages -match 'package:') { throw 'Guest package installed' }
$activities = A @('shell', 'dumpsys', 'activity', 'activities')
if ($activities -match 'com\.example\.appsandbox\.testguest/\.runtime\.') { throw 'Guest ActivityRecord exists' }

"status=PASS`nserial=$Serial`napi=$ExpectedApi`nrun=$run`ninstanceA=$idA`ninstanceB=$idB`nrevisionA=$revA`nrevisionB=$revB`nexportedActivity=$exported`nnonExportedRejected=PASS`nunknownRejected=PASS`ncarrierIdentity=PASS`ncarrierAction=PASS`nlogicalResult=PASS`nlogicalBack=PASS`nreopenLaunchCorrelation=PASS`nmalformedFailClosed=PASS`nABIsolation=PASS`nguestInstalled=false`nguestActivityRecord=false" | Set-Content (Join-Path $report 'result.txt')
Write-Output (Join-Path $report 'result.txt')
