param(
    [Parameter(Mandatory=$true)]
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$evidence = Join-Path $root "docs/experiments/evidence/task27/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
function Read-App([string]$name) { (& $adb -s $Serial shell run-as com.example.appsandbox cat "files/$name") -join "`n" }
function Launch([string]$action) {
    A @('shell','am','force-stop','com.example.appsandbox')
    $args = @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.task27.Task27DemoActivity')
    if ($action) { $args += @('--es','task27Action',$action) }
    A $args
    Start-Sleep -Milliseconds 900
}
$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($Serial -eq '7b670025' -and $api -ne '31') { throw "Expected API31, got $api" }
if ($Serial -eq 'emulator-5554' -and $api -ne '36') { throw "Expected API36, got $api" }
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
$hostSha = (Get-FileHash $hostApk -Algorithm SHA256).Hash.ToLowerInvariant()
$guestSha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
"serial=$Serial`napi=$api`nabi=$abi`npageSize=$page`nhostApkSha256=$hostSha`nguestApkSha256=$guestSha" | Set-Content (Join-Path $evidence 'device.txt')
A @('shell','pm','clear','com.example.appsandbox')
A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task27-input.apk')
A @('shell','run-as','com.example.appsandbox','mkdir','-p','files')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task27-input.apk','files/task27-input.apk')
A @('logcat','-c')
Launch ''
$initial = Read-App 'task27-result.txt'
Launch 'increment-a'; Launch 'increment-a'; Launch 'increment-b'; Launch 'increment-b'
$operated = Read-App 'task27-result.txt'
$operated | Set-Content (Join-Path $evidence 'multi-instance-result.txt')
($operated -split "`n" | Where-Object { $_ -match 'instance|counter|marker|revision|root' }) -join "`n" | Set-Content (Join-Path $evidence 'storage-isolation.txt')
A @('shell','screencap','-p','/sdcard/task27-operated.png')
A @('pull','/sdcard/task27-operated.png',(Join-Path $evidence 'ui.png'))
Launch ''
$restarted = Read-App 'task27-result.txt'
$restarted | Set-Content (Join-Path $evidence 'restart-delete-result.txt')
Launch 'reset-a'
$reset = Read-App 'task27-result.txt'
if ($reset -notmatch 'instanceA.counter=0' -or $reset -notmatch 'instanceB.counter=2') { throw 'reset isolation failed' }
Launch 'delete-a'
$deleted = Read-App 'task27-result.txt'
$deletedIds = @($deleted -split "`n" | Where-Object { $_ -match '^instance[A-Z]\.id=' })
if ($deletedIds.Count -ne 1 -or $deleted -notmatch 'instanceA.counter=2') { throw 'delete isolation failed' }
Launch 'increment-b'
$final = Read-App 'task27-result.txt'
$final | Add-Content (Join-Path $evidence 'restart-delete-result.txt')
$pm = (& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join ' '
 $hostGuestPrefs = (& $adb -s $Serial shell run-as com.example.appsandbox sh -c 'test -e shared_prefs/guest-demo.xml; echo $?' ).Trim()
 $hostGuestDb = (& $adb -s $Serial shell run-as com.example.appsandbox sh -c 'test -e databases/guest-demo.db; echo $?' ).Trim()
"guestPmPath=$pm" | Set-Content (Join-Path $evidence 'pm-path.txt')
"hostGuestPrefsExists=$hostGuestPrefs`nhostGuestDbExists=$hostGuestDb" | Add-Content (Join-Path $evidence 'storage-isolation.txt')
(& $adb -s $Serial shell dumpsys activity activities) -join "`n" | Set-Content (Join-Path $evidence 'host-state.txt')
(& $adb -s $Serial logcat -d -s Task27:I '*:S') -join "`n" | Set-Content (Join-Path $evidence 'logcat.txt')
if ($pm -or $hostGuestPrefs -ne '1' -or $hostGuestDb -ne '1' -or $final -notmatch 'guestSystemInstalled=false' -or $final -notmatch 'guestActivityAttached=false' -or
    $final -notmatch 'guestLifecycleExecuted=false' -or $operated -notmatch 'instanceA.counter=2' -or
    $operated -notmatch 'instanceB.counter=2' -or $operated -notmatch 'instanceA.root=' -or
    $operated -notmatch 'instanceB.root=' -or $reset -notmatch 'instanceB.counter=2' -or
    $final -notmatch 'instanceA.counter=3') { throw 'TASK-27 evidence incomplete' }
