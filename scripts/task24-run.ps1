param(
    [Parameter(Mandatory=$true)]
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$hostApk = "$root/app/build/outputs/apk/debug/app-debug.apk"
$guestApk = "$root/test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk"
$guestSha = (Get-FileHash $guestApk -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = Join-Path $root "docs/experiments/evidence/task24/$Serial"
New-Item -ItemType Directory -Force $evidence | Out-Null
function A([string[]]$argv) { & $adb -s $Serial @argv; if ($LASTEXITCODE -ne 0) { throw "adb failed: $argv" } }
function Read-AppFile([string]$name) { (& $adb -s $Serial shell run-as com.example.appsandbox cat "files/$name") -join "`n" }

$api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
$product = (& $adb -s $Serial shell getprop ro.product.name).Trim()
$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$page = (& $adb -s $Serial shell getconf PAGESIZE 2>$null).Trim()
"serial=$Serial`napi=$api`nproduct=$product`nmodel=$model`nabi=$abi`npageSize=$page`nguestApkSha256=$guestSha" | Set-Content (Join-Path $evidence 'device.txt')

A @('install','-r',$hostApk)
A @('push',$guestApk,'/data/local/tmp/task24-input.apk')
A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/task24-input.apk','files/task24-input.apk')
$before = (& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join ' '
if ($before) { throw 'Guest unexpectedly installed before run' }
A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','run-as','com.example.appsandbox','rm','-f','files/task24-act004a-negative.txt','files/task24-act004a-invalid.txt','files/task24-host-stub.txt')
A @('logcat','-c')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act004a-negative','--ez','import','true')
Start-Sleep -Seconds 2
$runner = Read-AppFile 'task24-act004a-negative.txt'
$stub = Read-AppFile 'task24-host-stub.txt'
$all = "$runner`n$stub"
$all | Set-Content (Join-Path $evidence 'act004a-negative.txt')
$stub | Set-Content (Join-Path $evidence 'host-stub.txt')
($runner -split "`n" | Where-Object { $_ -match 'guest\.|guestObject|identity\.|mapping.phase|instrumentation|constructor' }) -join "`n" | Set-Content (Join-Path $evidence 'guest-object.txt')
(& $adb -s $Serial logcat -d -s AppSandbox.Act003:I Task15:I '*:S') -join "`n" | Set-Content (Join-Path $evidence 'act004a-negative-logcat.txt')
($runner -split "`n" | Where-Object { $_ -match 'directGuest' }) -join "`n" | Set-Content (Join-Path $evidence 'direct-guest-launch.txt')

A @('shell','am','force-stop','com.example.appsandbox')
A @('shell','am','start','-W','-n','com.example.appsandbox/.experiments.v1.ExperimentActivity','--es','mode','act004a-invalid','--ez','import','false')
Start-Sleep -Seconds 1
$invalid = Read-AppFile 'task24-act004a-invalid.txt'
$invalid | Set-Content (Join-Path $evidence 'invalid-mapping.txt')
$after = (& $adb -s $Serial shell pm path com.example.appsandbox.testguest) -join ' '
"before=$before`nafter=$after" | Set-Content (Join-Path $evidence 'act004a-pm-path.txt')

if ($all -notmatch 'verification=VALID' -or $all -notmatch "record.sha256=$guestSha" -or
    $all -notmatch 'mapping.phase=UNATTACHED' -or $all -notmatch 'guest.lifecycleExecuted=false' -or
    $all -notmatch 'componentName=com.example.appsandbox/.experiments.act003.Act003StubActivity' -or
    $all -notmatch 'hostStub.objectClass=com.example.appsandbox.experiments.act003.Act003StubActivity' -or
    $all -notmatch 'decor.windowToken.nonNull=true' -or $all -notmatch 'decor.applicationWindowToken.nonNull=true' -or
    $all -notmatch 'hostStubTokenOnly=true' -or $all -notmatch 'guestTokenWindowTask=NOT_CLAIMED' -or
    $all -match 'directGuestLaunch=UNEXPECTED_SUCCESS' -or $after -or
    $invalid -notmatch 'mappingResult=REJECTED' -or $invalid -notmatch 'guestObjectCreated=false' -or
    $invalid -notmatch 'hostSurvived=true' -or $all -notmatch 'hostSurvived=true') {
    throw 'ACT-004A negative evidence incomplete'
}
