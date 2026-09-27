param([Parameter(Mandatory = $true)][string]$Serial)

$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1')
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task40/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
Remove-Item -LiteralPath (Join-Path $report 'result.txt') -ErrorAction SilentlyContinue

function A([string[]]$argv) {
    Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $argv -TimeoutSec 30 -ReportDir $report
}

function UiXml {
    $local = Join-Path $report 'ui.xml'
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $adb -s $Serial shell uiautomator dump /sdcard/task40.xml 2>&1 | Out-Null
        $dumpExitCode = $LASTEXITCODE
        & $adb -s $Serial pull /sdcard/task40.xml $local 2>&1 | Out-Null
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

function Resolve([int]$ordinal, [string]$className, [string]$type, [string]$scope, [string]$pattern, [string]$message) {
    A @(
        'shell', 'am', 'start', '-S', '-W', '-n',
        'com.example.appsandbox/.automation.Task40ResolverAutomationActivity',
        '--es', 'packageName', 'com.example.appsandbox.resolverfixture',
        '--ei', 'revisionOrdinal', "$ordinal",
        '--es', 'className', $className,
        '--es', 'componentType', $type,
        '--es', 'callerScope', $scope
    ) | Out-Null
    AssertUi $pattern $message
}

$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }

$hostApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$v2Apk = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
$v1Apk = Join-Path $root 'test-guests/IndependentGuest/build/outputs/apk/debug/IndependentGuest-debug.apk'
$resolverV1 = Join-Path $root 'test-guests/ResolverFixtures/build/outputs/apk/resolverV1/debug/ResolverFixtures-resolverV1-debug.apk'
$resolverV2 = Join-Path $root 'test-guests/ResolverFixtures/build/outputs/apk/resolverV2/debug/ResolverFixtures-resolverV2-debug.apk'
foreach ($path in @($hostApk, $v2Apk, $v1Apk, $resolverV1, $resolverV2)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing APK: $path" }
}

Set-Content (Join-Path $report 'device.txt') "serial=$Serial`napi=$api`nworkflow=revision-bound explicit component resolver"
A @('install', '-r', $hostApk) | Out-Null
A @('shell', 'pm', 'clear', 'com.example.appsandbox') | Out-Null
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.MainActivity') | Out-Null
AssertUi 'Guest Library' 'Host main screen did not return after clear'

Stage $v2Apk 'task40-v2.apk'
ImportApk 'task40-v2.apk'
Stage $v1Apk 'task40-v1.apk'
ImportApk 'task40-v1.apk'
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'createPackage', 'com.example.appsandbox.testguest') | Out-Null
AssertUi 'Guest Library|SUCCESS: created' 'v2 instance was not created'
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'createPackage', 'com.example.appsandbox.independentguest') | Out-Null
AssertUi 'Guest Library|SUCCESS: created' 'v1 instance was not created'
$baselineInstances = RunAs @('find', 'files/guest-instances', '-type', 'f', '-print')
$baselineInstanceRegistry = RunAs @('cat', 'files/guest-instances/registry.properties')

Stage $resolverV1 'task40-resolver-v1.apk'
ImportApk 'task40-resolver-v1.apk'
Stage $resolverV2 'task40-resolver-v2.apk'
ImportApk 'task40-resolver-v2.apk'

Resolve 0 '.FixtureComponents\$ExportedActivity' 'activity' 'HOST_EXTERNAL' 'RESOLVED' 'exported Activity did not resolve'
Resolve 0 '.FixtureComponents\$PrivateActivity' 'activity' 'HOST_EXTERNAL' 'REJECTED reason=not-exported' 'private external Activity did not fail closed'
Resolve 0 '.FixtureComponents\$PrivateActivity' 'activity' 'GUEST_INTERNAL' 'RESOLVED' 'private internal Activity did not resolve'
Resolve 0 '.FixtureComponents\$EnabledService' 'service' 'HOST_EXTERNAL' 'RESOLVED' 'enabled Service did not resolve'
Resolve 0 '.FixtureComponents\$DisabledService' 'service' 'HOST_EXTERNAL' 'REJECTED reason=disabled' 'disabled Service did not fail closed'
Resolve 0 '.FixtureComponents\$ProtectedService' 'service' 'HOST_EXTERNAL' 'REJECTED reason=permission-required' 'permission Service did not fail closed'
Resolve 0 '.FixtureComponents\$FixtureReceiver' 'receiver' 'HOST_EXTERNAL' 'RESOLVED' 'Receiver did not resolve'
Resolve 0 '.FixtureComponents\$FixtureProvider' 'provider' 'HOST_EXTERNAL' 'RESOLVED' 'Provider did not resolve'
Resolve 0 '.FixtureComponents\$ExportedActivity' 'service' 'HOST_EXTERNAL' 'REJECTED reason=type-mismatch' 'wrong component type did not fail closed'
Resolve 1 '.FixtureComponents\$ChangedActivity' 'activity' 'HOST_EXTERNAL' 'RESOLVED' 'second revision Activity did not resolve'
Resolve 1 '.FixtureComponents\$PrivateActivity' 'activity' 'HOST_EXTERNAL' 'REJECTED reason=not-found' 'old-only component resolved in second revision'
Resolve 0 '.FixtureComponents\$ChangedActivity' 'activity' 'HOST_EXTERNAL' 'REJECTED reason=not-found' 'new-only component resolved in first revision'

if ((RunAs @('find', 'files/guest-instances', '-type', 'f', '-print')) -ne $baselineInstances) {
    throw 'resolver imports changed instance or state files'
}
if ((RunAs @('cat', 'files/guest-instances/registry.properties')) -ne $baselineInstanceRegistry) {
    throw 'resolver imports changed instance registry'
}
if ((& $adb -s $Serial shell pm path com.example.appsandbox.resolverfixture) -join '') {
    throw 'Resolver fixture must not be installed'
}
if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '' -or
    (& $adb -s $Serial shell pm path com.example.appsandbox.independentguest) -join '') {
    throw 'Guest test packages must not be installed'
}

A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', 'com.example.appsandbox.testguest', '--ei', 'instanceOrdinal', '0') | Out-Null
AssertUi 'contract=v2' 'v2 workspace unavailable after resolver imports'
A @('shell', 'input', 'keyevent', '3') | Out-Null
A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', 'com.example.appsandbox.independentguest', '--ei', 'instanceOrdinal', '0') | Out-Null
AssertUi 'contract=v1' 'v1 workspace unavailable after resolver imports'

Set-Content (Join-Path $report 'result.txt') "activity-exported=PASS`nactivity-not-exported=PASS`nservice-enabled-disabled=PASS`nreceiver-provider=PASS`npermission-rejection=PASS`nrevision-binding=PASS`nno-instance-state-leak=PASS`nv1v2-still-usable=PASS`nresolver-fixture-installed=false`napi=$api"
