param([Parameter(Mandatory = $true)][string]$Serial)

$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task36/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
$result = Join-Path $report 'result.txt'
Remove-Item -LiteralPath $result -ErrorAction SilentlyContinue
"status=RUNNING`nserial=$Serial" | Set-Content $result
trap {
    "status=FAIL`nserial=$Serial`nerror=$($_.Exception.Message)" | Set-Content $result
    throw
}

function A([string[]]$argv) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $adb -s $Serial @argv 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previous
    $output | Tee-Object -FilePath (Join-Path $report 'adb-last.txt')
    if ($exitCode -ne 0) { throw "adb failed: $argv" }
}

function UiXml {
    $local = Join-Path $report 'ui.xml'
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        A @('shell', 'rm', '-f', '/sdcard/task36.xml') | Out-Null
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $adb -s $Serial shell uiautomator dump /sdcard/task36.xml 2>&1 | Out-Null
        & $adb -s $Serial pull /sdcard/task36.xml $local 2>&1 | Out-Null
        $pullExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previous
        if ($pullExitCode -eq 0 -and (Test-Path -LiteralPath $local)) {
            $xml = Get-Content -Raw $local
            if ($xml -match '<hierarchy') { return $xml }
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'uiautomator dump did not produce a hierarchy'
}

function AssertUi([string]$pattern, [string]$message) {
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        try { if ((UiXml) -match $pattern) { return } } catch {}
        Start-Sleep -Milliseconds 500
    }
    throw $message
}

function Tap([string]$prefix, [int]$index = 0) {
    $pattern = 'text="(' + [regex]::Escape($prefix) + '[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        $xml = UiXml
        $matches = [regex]::Matches($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($matches.Count -gt $index) {
            $match = $matches[$index]
            $x = ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
            $y = ([int]$match.Groups[3].Value + [int]$match.Groups[5].Value) / 2
            if ($y -gt 0) {
                A @('shell', 'input', 'tap', "$x", "$y") | Out-Null
                Start-Sleep -Milliseconds 600
                return
            }
        }
        $screen = [regex]::Match($xml, '<node[^>]*bounds="\[0,0\]\[(\d+),(\d+)\]"')
        if (!$screen.Success) { throw 'UI screen bounds unavailable' }
        $center = [int]$screen.Groups[1].Value / 2
        $height = [int]$screen.Groups[2].Value
        A @('shell', 'input', 'swipe', "$center", "$([int]($height * 0.8))", "$center", "$([int]($height * 0.3))", '300') | Out-Null
        Start-Sleep -Milliseconds 400
    }
    throw "UI not found after scrolling: $prefix index=$index"
}

function ImportApk([string]$path) {
    A @(
        'shell', 'am', 'start', '-S', '-W', '-n',
        'com.example.appsandbox/.automation.Task29AutomationActivity',
        '--es', 'stagedApk', $path
    ) | Out-Null
    Start-Sleep -Seconds 1
}

function LaunchMain {
    A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.MainActivity') | Out-Null
    for ($poll = 0; $poll -lt 10; $poll++) {
        $activities = (A @('shell', 'dumpsys', 'activity', 'activities')) -join "`n"
        if ($activities -match 'ResumedActivity: ActivityRecord\{[^\r\n]*com\.example\.appsandbox/\.MainActivity') {
            try { if ((UiXml) -match 'Guest Library') { return } } catch {}
        }
        Start-Sleep -Milliseconds 400
    }
    throw 'MainActivity did not reach foreground'
}

function OpenPackage([string]$packageName) {
    for ($launch = 0; $launch -lt 3; $launch++) {
        A @('shell', 'am', 'start', '-S', '-W', '-n', 'com.example.appsandbox/.automation.Task29AutomationActivity', '--es', 'packageName', $packageName, '--ei', 'instanceOrdinal', '0') | Out-Null
        for ($poll = 0; $poll -lt 10; $poll++) {
            $activities = (A @('shell', 'dumpsys', 'activity', 'activities')) -join "`n"
            if ($activities -match 'ResumedActivity: ActivityRecord\{[^\r\n]*com\.example\.appsandbox/\.GuestWorkspaceActivity') {
                try { if ((UiXml) -match 'instance=[0-9a-f-]{36}') { return } } catch {}
            }
            Start-Sleep -Milliseconds 400
        }
    }
    throw "workspace did not reach foreground: $packageName"
}

function RunAs([string[]]$argv) {
    $output = & $adb -s $Serial shell run-as com.example.appsandbox @argv 2>&1
    if ($LASTEXITCODE -ne 0) { throw "run-as failed: $argv`n$output" }
    $output -join "`n"
}

function Registry {
    RunAs @('cat', 'files/guests/registry.json')
}

function AssertBaseline([string]$expectedRegistry, [string]$label) {
    $actual = Registry
    if ($actual -ne $expectedRegistry) { throw "$label changed the Guest registry" }
    $revisionCount = [regex]::Matches($actual, '"revisionId"').Count
    if ($revisionCount -ne 2) { throw "$label left $revisionCount revisions; expected 2" }
    if ((RunAs @('find', 'files/guests/staging', '-mindepth', '1', '-print')).Trim()) {
        throw "$label left staging residue"
    }
    if ((RunAs @('find', 'files/guests', '-type', 'f', '-print')) -ne $baselineGuestFiles) {
        throw "$label changed committed Guest artifacts"
    }
    if ((RunAs @('find', 'files/guest-instances', '-type', 'f', '-print')) -ne $baselineInstanceFiles) {
        throw "$label changed instance or state files"
    }
    if ((RunAs @('cat', 'files/guest-instances/registry.properties')) -ne $baselineInstanceRegistry) {
        throw "$label changed the instance registry"
    }
}

function AssertLibrary {
    $actual = Registry
    if ($actual -notmatch '"packageName": "com\.example\.appsandbox\.testguest"' -or
        $actual -notmatch '"contractVersion": 2' -or
        $actual -notmatch '"packageName": "com\.example\.appsandbox\.independentguest"' -or
        $actual -notmatch '"contractVersion": 1') {
        throw 'Guest Library does not contain the original v1/v2 revisions'
    }
}

$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }

$hostApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$v2Apk = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
$v1Apk = Join-Path $root 'test-guests/IndependentGuest/build/outputs/apk/debug/IndependentGuest-debug.apk'
$fixtures = [ordered]@{
    unknownVersion = 'unknown-version'
    missingLayout = 'missing-layout'
    missingActionRaw = 'missing-action-resource'
    unknownField = 'unknown-field'
    unknownAction = 'unknown-action'
    duplicateBinding = 'duplicate-binding'
    invalidId = 'invalid-id'
    invalidStateKey = 'invalid-state-key'
    wrongButtonType = 'wrong-button-type'
    wrongTextViewType = 'wrong-textview-type'
}
foreach ($fixture in $fixtures.Keys) {
    $apk = Join-Path $root "test-guests/ContractInvalidFixtures/build/outputs/apk/$fixture/debug/ContractInvalidFixtures-$fixture-debug.apk"
    if (!(Test-Path -LiteralPath $apk)) { throw "Missing fixture APK: $apk" }
}
foreach ($path in @($hostApk, $v2Apk, $v1Apk)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing baseline APK: $path" }
}

"serial=$Serial`napi=$api`nworkflow=real APK contract negative matrix" | Set-Content (Join-Path $report 'device.txt')
A @('install', '-r', $hostApk)
A @('shell', 'pm', 'clear', 'com.example.appsandbox')
A @('push', $v2Apk, '/data/local/tmp/task36-v2.apk')
A @('push', $v1Apk, '/data/local/tmp/task36-v1.apk')
A @('shell', 'run-as', 'com.example.appsandbox', 'mkdir', '-p', 'files')
A @('shell', 'run-as', 'com.example.appsandbox', 'cp', '/data/local/tmp/task36-v2.apk', 'files/task36-v2.apk')
A @('shell', 'run-as', 'com.example.appsandbox', 'cp', '/data/local/tmp/task36-v1.apk', 'files/task36-v1.apk')

ImportApk '/data/data/com.example.appsandbox/files/task36-v2.apk'
Tap 'Create instance from selected revision'
ImportApk '/data/data/com.example.appsandbox/files/task36-v1.apk'
Tap 'Create instance from selected revision'
$baselineRegistry = Registry
if ([regex]::Matches($baselineRegistry, '"revisionId"').Count -ne 2) { throw 'v1/v2 baseline did not create exactly two revisions' }
AssertLibrary
$baselineGuestFiles = RunAs @('find', 'files/guests', '-type', 'f', '-print')
$baselineInstanceFiles = RunAs @('find', 'files/guest-instances', '-type', 'f', '-print')
$baselineInstanceRegistry = RunAs @('cat', 'files/guest-instances/registry.properties')

foreach ($fixture in $fixtures.Keys) {
    $source = Join-Path $root "test-guests/ContractInvalidFixtures/build/outputs/apk/$fixture/debug/ContractInvalidFixtures-$fixture-debug.apk"
    A @('push', $source, "/data/local/tmp/task36-$fixture.apk") | Out-Null
    A @('shell', 'run-as', 'com.example.appsandbox', 'cp', "/data/local/tmp/task36-$fixture.apk", "files/task36-$fixture.apk") | Out-Null
    ImportApk "/data/data/com.example.appsandbox/files/task36-$fixture.apk"
    $reason = $fixtures[$fixture]
    AssertUi ("UNSUPPORTED:.*" + [regex]::Escape($reason)) "$fixture did not expose reason $reason"
    AssertBaseline $baselineRegistry $fixture
    LaunchMain
    AssertLibrary
}

A @('shell', 'am', 'force-stop', 'com.example.appsandbox') | Out-Null
OpenPackage 'com.example.appsandbox.testguest'
AssertUi 'contract=v2' 'v2 workspace unavailable after negative imports'
Tap 'Guest Increment'
AssertUi 'Counter: 1' 'v2 action unavailable after negative imports'
A @('shell', 'input', 'keyevent', '3') | Out-Null

OpenPackage 'com.example.appsandbox.independentguest'
AssertUi 'contract=v1' 'v1 workspace unavailable after negative imports'
Tap 'Increment'
AssertUi 'Counter: 1' 'v1 action unavailable after negative imports'

if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '') { throw 'GuestTestApp must not be installed' }
if ((& $adb -s $Serial shell pm path com.example.appsandbox.independentguest) -join '') { throw 'IndependentGuest must not be installed' }
"status=PASS`nunknownVersion=PASS`nmissingLayout=PASS`nmissingActionRaw=PASS`nunknownField=PASS`nunknownAction=PASS`nduplicateBinding=PASS`ninvalidId=PASS`ninvalidStateKey=PASS`nwrongButtonType=PASS`nwrongTextViewType=PASS`nnoLibraryOrRevisionLeak=PASS`nnoInstanceOrStateLeak=PASS`nv1v2StillUsable=PASS`napi=$api`nguestPackagesInstalled=false" | Set-Content $result
