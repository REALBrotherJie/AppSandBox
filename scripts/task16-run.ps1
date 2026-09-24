param(
    [string]$Serial = '7b670025',
    [string]$Mode = 'production-exp001'
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$sha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = Join-Path $root "docs/experiments/evidence/task16/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task16-input.apk')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task16-input.apk','files/task16-input.apk')
$before = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
if ($LASTEXITCODE -notin @(0,1) -or $before) { throw 'Guest unexpectedly installed before run' }
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f',"files/task16-$Mode.txt")
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode',$Mode,'--ez','import','true')
Start-Sleep -Seconds 2
$report = & $adb -s $Serial shell run-as com.example.appsandbox cat "files/task16-$Mode.txt"
$reportText = ($report -join "`n")
$reportText | Set-Content (Join-Path $evidence "$Mode.txt")
if ($reportText -notmatch 'runCount=1' -or $reportText -notmatch "sha256=$sha") { throw 'Production verification evidence incomplete' }
$after = & $adb -s $Serial shell pm path com.example.appsandbox.testguest
if ($LASTEXITCODE -notin @(0,1) -or $after) { throw 'Guest unexpectedly installed after run' }
