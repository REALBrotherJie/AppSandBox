param([Parameter(Mandatory = $true)][string]$Serial)

$ErrorActionPreference = 'Stop'
if ($Serial -ne 'emulator-5554') { throw 'Task-43 permits only API36 serial emulator-5554' }
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task43/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
Remove-Item -LiteralPath (Join-Path $report 'result.txt') -ErrorAction SilentlyContinue

function A([string[]]$argv) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $adb -s $Serial @argv 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previous
    $output | Tee-Object -FilePath (Join-Path $report 'adb-last.txt')
    if ($exitCode -ne 0) { throw "adb failed: $($argv -join ' ')" }
    $output
}

function UiXml {
    $local = Join-Path $report 'ui.xml'
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $adb -s $Serial shell uiautomator dump /sdcard/task43.xml 2>&1 | Out-Null
        $dumpExitCode = $LASTEXITCODE
        & $adb -s $Serial pull /sdcard/task43.xml $local 2>&1 | Out-Null
        $pullExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previous
        if ($dumpExitCode -eq 0 -and $pullExitCode -eq 0 -and (Test-Path -LiteralPath $local)) {
            $xml = Get-Content -Raw $local
            if ($xml -match '<hierarchy') { return $xml }
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'uiautomator dump did not produce a hierarchy'
}

function AssertUi([string]$pattern, [string]$message) {
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        if ((UiXml) -match $pattern) { return }
        Start-Sleep -Milliseconds 500
    }
    throw $message
}

function RunAs([string[]]$argv) {
    $output = & $adb -s $Serial shell run-as com.example.appsandbox @argv 2>&1
    if ($LASTEXITCODE -ne 0) { throw "run-as failed: $($argv -join ' ')`n$output" }
    $output -join "`n"
}

function Stage([string]$source, [string]$name) {
    A @('push', $source, "/data/local/tmp/$name") | Out-Null
    A @('shell', 'run-as', 'com.example.appsandbox', 'mkdir', '-p', 'files') | Out-Null
    A @('shell', 'run-as', 'com.example.appsandbox', 'cp', "/data/local/tmp/$name", "files/$name") | Out-Null
}

function ImportApk([string]$name) {
    A @(
        'shell', 'am', 'start', '-S', '-W', '-n',
        'com.example.appsandbox/.automation.Task29AutomationActivity',
        '--es', 'stagedApk', "/data/data/com.example.appsandbox/files/$name"
    ) | Out-Null
    AssertUi 'Guest Library' "import did not return to Guest Library: $name"
}

function Resolve(
    [int]$ordinal,
    [string]$type,
    [string]$action,
    [string[]]$categories,
    [string]$mimeType,
    [string]$uri,
    [string]$pattern,
    [string]$message
) {
    $args = @(
        'shell', 'am', 'start', '-S', '-W', '-n',
        'com.example.appsandbox/.automation.Task43ImplicitResolverAutomationActivity',
        '--es', 'packageName', 'com.example.appsandbox.intentfilterfixture',
        '--ei', 'revisionOrdinal', "$ordinal",
        '--es', 'componentType', $type,
        '--es', 'callerScope', 'HOST_EXTERNAL',
        '--es', 'action', $action
    )
    if ($categories.Count -gt 0) { $args += @('--esa', 'categories', ($categories -join ',')) }
    if ($mimeType) { $args += @('--es', 'mimeType', $mimeType) }
    if ($uri) { $args += @('--es', 'uri', $uri) }
    A $args | Out-Null
    AssertUi $pattern $message
}

$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($api -ne '36') { throw "Expected API36, got $api" }

$hostApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$v2Apk = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
$v1Apk = Join-Path $root 'test-guests/IndependentGuest/build/outputs/apk/debug/IndependentGuest-debug.apk'
$intentV1 = Join-Path $root 'test-guests/IntentFilterFixtures/build/outputs/apk/intentV1/debug/IntentFilterFixtures-intentV1-debug.apk'
$intentV2 = Join-Path $root 'test-guests/IntentFilterFixtures/build/outputs/apk/intentV2/debug/IntentFilterFixtures-intentV2-debug.apk'
foreach ($path in @($hostApk, $v2Apk, $v1Apk, $intentV1, $intentV2)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing APK: $path" }
}

Set-Content (Join-Path $report 'device.txt') "serial=$Serial`napi=$api`nworkflow=bounded revision-bound implicit intent resolver"
A @('install', '-r', $hostApk) | Out-Null
A @('shell', 'pm', 'clear', 'com.example.appsandbox') | Out-Null
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.MainActivity') | Out-Null
AssertUi 'Guest Library' 'Host main screen did not return after clear'

Stage $v2Apk 'task43-v2.apk'
ImportApk 'task43-v2.apk'
Stage $v1Apk 'task43-v1.apk'
ImportApk 'task43-v1.apk'
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'createPackage', 'com.example.appsandbox.testguest') | Out-Null
AssertUi 'Guest Library|SUCCESS: created' 'v2 instance was not created'
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'createPackage', 'com.example.appsandbox.independentguest') | Out-Null
AssertUi 'Guest Library|SUCCESS: created' 'v1 instance was not created'
$baselineInstances = RunAs @('find', 'files/guest-instances', '-type', 'f', '-print')
$baselineInstanceRegistry = RunAs @('cat', 'files/guest-instances/registry.properties')

Stage $intentV1 'task43-intent-v1.apk'
ImportApk 'task43-intent-v1.apk'
Stage $intentV2 'task43-intent-v2.apk'
ImportApk 'task43-intent-v2.apk'

Resolve 0 'activity' 'com.example.intent.VIEW' @('com.example.intent.DEFAULT', 'com.example.intent.IMAGE') 'image/png' 'https://example.com/images' 'RESOLVED count=2' 'v1 exact/wildcard Activity candidates did not resolve'
Resolve 0 'activity' 'com.example.intent.EDIT' @('com.example.intent.DEFAULT', 'com.example.intent.IMAGE') 'image/png' 'https://example.com/images' 'RESOLVED count=1' 'multiple-action filter did not resolve'
Resolve 0 'receiver' 'com.example.intent.PING' @('com.example.intent.DEFAULT') '' '' 'RESOLVED count=1' 'Receiver filter did not resolve'
Resolve 0 'activity' 'com.example.intent.DISABLED' @('com.example.intent.DEFAULT') '' '' 'REJECTED reason=disabled' 'disabled filter did not fail closed'
Resolve 0 'activity' 'com.example.intent.PROTECTED' @('com.example.intent.DEFAULT') '' '' 'REJECTED reason=permission-required' 'permission filter did not fail closed'
Resolve 1 'activity' 'com.example.intent.REVISION_V1' @() '' '' 'REJECTED reason=no-match' 'old revision filter leaked into v2'
Resolve 1 'activity' 'com.example.intent.REVISION_V2' @() '' '' 'RESOLVED count=1' 'v2-only filter did not resolve'
Resolve 1 'receiver' 'com.example.intent.PING' @() '' '' 'REJECTED reason=not-exported' 'v2 non-exported Receiver did not fail closed'
Resolve 0 'activity' 'com.example.intent.VIEW' @('com.example.intent.DEFAULT', 'com.example.intent.IMAGE') 'image/png' 'https://example.com/images?query=1' 'REJECTED reason=invalid-request' 'unsupported URI query did not fail closed'

if ((RunAs @('find', 'files/guest-instances', '-type', 'f', '-print')) -ne $baselineInstances) {
    throw 'implicit resolver imports changed instance or state files'
}
if ((RunAs @('cat', 'files/guest-instances/registry.properties')) -ne $baselineInstanceRegistry) {
    throw 'implicit resolver imports changed instance registry'
}
if ((& $adb -s $Serial shell pm path com.example.appsandbox.intentfilterfixture) -join '') {
    throw 'IntentFilter fixture must not be installed'
}
if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '' -or
    (& $adb -s $Serial shell pm path com.example.appsandbox.independentguest) -join '') {
    throw 'Guest test packages must not be installed'
}
$activities = (A @('shell', 'dumpsys', 'activity', 'activities')) -join "`n"
if ($activities -match 'com\.example\.appsandbox\.intentfilterfixture|intentfilterfixture') {
    throw 'Guest ActivityRecord was observed'
}

A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', 'com.example.appsandbox.testguest', '--ei', 'instanceOrdinal', '0') | Out-Null
AssertUi 'contract=v2' 'v2 workspace unavailable after implicit resolver imports'
A @('shell', 'input', 'keyevent', '3') | Out-Null
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', 'com.example.appsandbox.independentguest', '--ei', 'instanceOrdinal', '0') | Out-Null
AssertUi 'contract=v1' 'v1 workspace unavailable after implicit resolver imports'

Set-Content (Join-Path $report 'result.txt') "action-category=PASS`nmime-exact-wildcard=PASS`nuri-scheme-host-path=PASS`npriority-specificity-order=PASS`ndisabled-permission=PASS`nrevision-binding=PASS`nno-instance-state-leak=PASS`nno-guest-activity-record=true`nintent-filter-fixture-installed=false`napi=$api"
