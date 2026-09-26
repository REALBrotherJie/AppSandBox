param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root "build/reports/task29/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv 2>&1 | Tee-Object -FilePath (Join-Path $report 'adb-last.txt'); if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
function UiXml { A @('shell','uiautomator','dump','/sdcard/task29-ui.xml') | Out-Null; A @('pull','/sdcard/task29-ui.xml',(Join-Path $report 'ui.xml')) | Out-Null; Get-Content -Raw (Join-Path $report 'ui.xml') }
function TapText([string]$text) {
    $xml = UiXml
    $escaped = [regex]::Escape($text)
    $pattern = 'text="' + $escaped + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (!$m.Success) { throw "UI text not found: $text" }
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    A @('shell','input','tap',"$x","$y") | Out-Null
    Start-Sleep -Milliseconds 500
}
function TapFirst([string]$prefix) {
    $xml = UiXml
    $pattern = 'text="(' + [regex]::Escape($prefix) + '[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (!$m.Success) { throw "UI prefix not found: $prefix" }
    $x = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    $y = ([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2
    A @('shell','input','tap',"$x","$y") | Out-Null
    Start-Sleep -Milliseconds 500
}
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }
$apk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$guest = Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
"serial=$Serial`napi=$api`nworkflow=debug-signed automation seam" | Set-Content (Join-Path $report 'device.txt')
A @('install','-r',$apk); A @('shell','pm','clear','com.example.appsandbox'); A @('push',$guest,'/data/local/tmp/task29-input.apk')
A @('shell','run-as','com.example.appsandbox','mkdir','-p','files'); A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task29-input.apk','files/task29-input.apk')
A @('shell','am','start','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','stagedApk','/data/data/com.example.appsandbox/files/task29-input.apk')
Start-Sleep -Seconds 2
TapText 'Create instance'; TapText 'Create instance'
TapFirst 'Open '
TapText 'Increment'; TapText 'Increment'; A @('shell','input','keyevent','4'); Start-Sleep -Milliseconds 700
TapFirst 'Open '
TapText 'Increment'; TapText 'Increment'; TapText 'Increment'; A @('shell','input','keyevent','4'); Start-Sleep -Milliseconds 700
A @('shell','am','force-stop','com.example.appsandbox'); A @('shell','monkey','-p','com.example.appsandbox','1'); Start-Sleep -Seconds 2
TapText 'Delete'; TapText 'Delete'; Start-Sleep -Seconds 1
$xml = UiXml
if (($xml | Select-String -Pattern 'Open ' -AllMatches).Matches.Count -ne 1) { throw 'expected one remaining workspace after deleting A' }
TapFirst 'Open '; TapText 'Increment'
if ((& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join '') { throw 'Guest package must not be installed' }
"import=PASS`ncreateAB=PASS`nindependentCounters=PASS`nrestart=PASS`ndeleteA_BRemains=PASS`nguestPackageInstalled=false" | Set-Content (Join-Path $report 'result.txt')
