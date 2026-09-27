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
$evidence = Join-Path $root "build/reports/task48/writable-dcl/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1')
function A([string[]]$argv) { Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $argv -TimeoutSec 30 -ReportDir $evidence }
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task16-input.apk')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task16-input.apk','files/task15-input.apk')
$before = try { A @('shell','pm','path','com.example.appsandbox.testguest') } catch { '' }
if ($before) { throw 'Guest unexpectedly installed before run' }
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f',"files/task16-$Mode.txt")
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode',$Mode,'--ez','import','true')
Start-Sleep -Seconds 2
$reportText = A @('shell','run-as','com.example.appsandbox','cat',"files/task16-$Mode.txt")
$reportText | Set-Content (Join-Path $evidence "$Mode.txt")
if ($reportText -notmatch 'runCount=1' -or $reportText -notmatch "sha256=$sha") { throw 'Production verification evidence incomplete' }
if ($Mode -eq 'writable-negative' -and $reportText -notmatch 'writable.load=java.lang.SecurityException') { throw 'Writable DCL was not rejected with SecurityException' }
$after = try { A @('shell','pm','path','com.example.appsandbox.testguest') } catch { '' }
if ($after) { throw 'Guest unexpectedly installed after run' }
