param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'
$adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe'
$root=Split-Path $PSScriptRoot -Parent
$report=Join-Path $root "build/reports/task35/$Serial"
New-Item -ItemType Directory -Force $report | Out-Null
Remove-Item -LiteralPath (Join-Path $report 'result.txt') -ErrorAction SilentlyContinue
"status=RUNNING`nserial=$Serial" | Set-Content (Join-Path $report 'result.txt')

function A([string[]]$v) {
    $out=& $adb -s $Serial @v 2>&1
    if($LASTEXITCODE -ne 0){throw "adb failed: $($v -join ' ')`n$($out -join "`n")"}
    $out
}
function PmPath([string]$packageName) {
    (& $adb -s $Serial shell pm path $packageName 2>&1) -join ''
}
function Xml {
    try {
        A @('shell','uiautomator','dump','/sdcard/t35.xml') | Out-Null
        A @('pull','/sdcard/t35.xml',(Join-Path $report 'ui.xml')) | Out-Null
        Get-Content -Raw (Join-Path $report 'ui.xml')
    } catch {
        ''
    }
}
function WaitXml([string]$pattern,[string]$message='UI state not reached') {
    for($i=0;$i -lt 40;$i++) {
        $x=Xml
        if($x -match $pattern){return $x}
        Start-Sleep -Milliseconds 300
    }
    throw $message
}
function AssertXml([string]$pattern,[string]$message) {
    WaitXml $pattern $message | Out-Null
}
function Tap([string]$prefix) {
    $p='text="('+[regex]::Escape($prefix)+'[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    for($attempt=0;$attempt -lt 6;$attempt++) {
        $x=Xml
        $m=[regex]::Match($x,$p,[System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if($m.Success) {
            $cx=([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2
            $cy=([int]$m.Groups[3].Value+[int]$m.Groups[5].Value)/2
            A @('shell','input','tap',"$cx","$cy") | Out-Null
            return
        }
        A @('shell','input','swipe','540','1700','540','500','450') | Out-Null
        Start-Sleep -Milliseconds 400
    }
    throw "UI bounds not found after bounded scroll: $prefix"
}
function TapCreateInstance {
    $prefix='Create instance from selected revision'
    for($i=0;$i -lt 12;$i++) {
        $x=Xml
        $p='text="('+[regex]::Escape($prefix)+'[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        $m=[regex]::Match($x,$p,[System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if($m.Success) {
            $cx=([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2
            $cy=([int]$m.Groups[3].Value+[int]$m.Groups[5].Value)/2
            A @('shell','input','tap',"$cx","$cy") | Out-Null
            return
        }
        A @('shell','input','swipe','540','1850','540','700','300') | Out-Null
        Start-Sleep -Milliseconds 300
    }
    throw "UI not found: $prefix"
}
function Import([string]$path) {
    A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','stagedApk',$path) | Out-Null
    for($i=0;$i -lt 20;$i++) {
        $x=Xml
        if($x -match 'Imported successfully; supported Guest contract v[12]|SUCCESS: imported supported Guest contract v[12]'){return}
        if($i -eq 4 -or $i -eq 9 -or $i -eq 14) {
            A @('shell','am','start','-W','-n','com.example.appsandbox/.MainActivity') | Out-Null
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'import did not complete'
}
function CreateInstance {
    A @('shell','am','start','-S','-W','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','createPackage','com.example.appsandbox.testguest') | Out-Null
    AssertXml 'Guest Library' 'create-instance host did not return'
}
function OpenPackage([string]$packageName,[int]$ordinal=0) {
    for($launch=0;$launch -lt 3;$launch++) {
        A @('shell','am','start','-W','--activity-clear-task','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','packageName',$packageName,'--ei','instanceOrdinal',"$ordinal") | Out-Null
        for($poll=0;$poll -lt 10;$poll++) {
            $activities=(A @('shell','dumpsys','activity','activities')) -join "`n"
            if($activities -match 'ResumedActivity: ActivityRecord\{[^\r\n]*com\.example\.appsandbox/\.GuestWorkspaceActivity' -and
               (Xml) -match 'instance=[0-9a-f-]{36}' -and (Xml) -match [regex]::Escape("package=$packageName")) { return }
            Start-Sleep -Milliseconds 400
        }
    }
    throw "workspace did not reach foreground: $packageName ordinal=$ordinal"
}
function OpenStale([string]$id) {
    A @('shell','am','start','-W','--activity-clear-task','-n','com.example.appsandbox/.automation.Task29AutomationActivity','--es','staleInstanceId',$id) | Out-Null
}
function CurrentId {
    $x=WaitXml 'instance=([0-9a-f-]{36})' 'workspace instance identity missing'
    [regex]::Match($x,'instance=([0-9a-f-]{36})','IgnoreCase').Groups[1].Value.ToLowerInvariant()
}
function DumpTasks([string]$name) {
    $path=Join-Path $report $name
    $out=& $adb -s $Serial shell dumpsys activity activities 2>&1
    if($LASTEXITCODE -ne 0){throw 'dumpsys activity failed'}
    $out | Set-Content $path
    ($out -join "`n")
}
function AssertTwoTasks([string]$dump,[string]$a,[string]$b) {
    if($dump -notmatch [regex]::Escape("appsandbox://workspace/$a") -or
       $dump -notmatch [regex]::Escape("appsandbox://workspace/$b")){throw 'workspace document URI missing'}
    $ids=[regex]::Matches($dump,'GuestWorkspaceActivity[^\r\n]*\bt(\d+)\b') |
        ForEach-Object {$_.Groups[1].Value} | Select-Object -Unique
    if($ids.Count -ne 2){throw "expected two distinct workspace taskIds, found $($ids.Count): $ids"}
}
function Prepare([string]$guestPath) {
    $hostApk=Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
    A @('install','-r',$hostApk) | Out-Null
    A @('shell','pm','clear','com.example.appsandbox') | Out-Null
    A @('shell','am','start','-W','-n','com.example.appsandbox/.MainActivity') | Out-Null
    AssertXml 'App Sandbox' 'Host main screen did not return after clear'
    A @('push',$guestPath,'/data/local/tmp/t35.apk') | Out-Null
    A @('shell','run-as','com.example.appsandbox','mkdir','-p','files') | Out-Null
    A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/t35.apk','files/t35.apk') | Out-Null
}
function Stage([string]$guestPath) {
    A @('push',$guestPath,'/data/local/tmp/t35.apk') | Out-Null
    A @('shell','run-as','com.example.appsandbox','mkdir','-p','files') | Out-Null
    A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/t35.apk','files/t35.apk') | Out-Null
}
function RunTask33 {
    $v2=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
    $v1=Join-Path $root 'test-guests/IndependentGuest/build/outputs/apk/debug/IndependentGuest-debug.apk'
    Prepare $v2
    Import '/data/data/com.example.appsandbox/files/t35.apk'
    TapCreateInstance; TapCreateInstance
    Stage $v1
    Import '/data/data/com.example.appsandbox/files/t35.apk'
    TapCreateInstance
    AssertXml 'com.example.appsandbox.testguest[^\"]*contract=v2' 'v2 library contract missing'
    AssertXml 'com.example.appsandbox.independentguest[^\"]*contract=v1' 'v1 library contract missing'
    OpenPackage 'com.example.appsandbox.testguest' 0
    AssertXml 'contract=v2' 'v2 workspace missing'
    AssertXml 'GUEST_A_MARKER_1D1C4B6A' 'v2 marker missing'
    Tap 'Guest Increment'; AssertXml 'Counter: 1' 'increment failed'
    Tap 'Guest Toggle'; AssertXml 'Counter: 0' 'toggle-to-zero failed'
    Tap 'Guest Toggle'; AssertXml 'Counter: 1' 'toggle-to-one failed'
    Tap 'Guest Reset'; AssertXml 'Counter: 0' 'reset failed'
    Tap 'Guest Increment'
    OpenPackage 'com.example.appsandbox.testguest' 1; AssertXml 'Counter: 0' 'second instance leaked state'
    Tap 'Guest Increment'; Tap 'Guest Increment'; AssertXml 'Counter: 2' 'second instance count failed'
    A @('shell','am','force-stop','com.example.appsandbox') | Out-Null
    OpenPackage 'com.example.appsandbox.testguest' 0; AssertXml 'Counter: 1' 'restart lost first instance state'
    Tap 'Delete current instance'; Tap 'Confirm delete instance'
    OpenPackage 'com.example.appsandbox.testguest' 0; AssertXml 'Counter: 2' 'remaining v2 instance changed after deletion'
    Tap 'Guest Reset'; Tap 'Guest Toggle'; AssertXml 'Counter: 1' 'remaining v2 action failed'
    OpenPackage 'com.example.appsandbox.independentguest' 0
    AssertXml 'contract=v1' 'v1 workspace missing'; AssertXml 'INDEPENDENT_GUEST_MARKER_B' 'v1 marker missing'
    Tap 'Increment'; AssertXml 'Counter: 1' 'v1 Host action regressed'
    if((PmPath 'com.example.appsandbox.testguest') -or
       (PmPath 'com.example.appsandbox.independentguest')){throw 'Guest installed'}
}
function RunTask34 {
    $v2=Join-Path $root 'test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk'
    Prepare $v2
    Import '/data/data/com.example.appsandbox/files/t35.apk'
    TapCreateInstance; TapCreateInstance
    OpenPackage 'com.example.appsandbox.testguest' 0; $idA=CurrentId; Tap 'Guest Increment'; AssertXml 'Counter: 1' 'A action failed'
    A @('shell','input','keyevent','3') | Out-Null
    OpenPackage 'com.example.appsandbox.testguest' 1; $idB=CurrentId
    if($idA -eq $idB){throw 'A/B identity collision'}
    AssertXml 'Counter: 0' 'B leaked A state'; Tap 'Guest Increment'; Tap 'Guest Increment'; AssertXml 'Counter: 2' 'B action failed'
    $dump=DumpTasks 'simultaneous.txt'; AssertTwoTasks $dump $idA $idB
    OpenPackage 'com.example.appsandbox.testguest' 0; AssertXml ([regex]::Escape("instance=$idA")) 'repeat A focused wrong instance'
    AssertTwoTasks (DumpTasks 'repeat-a.txt') $idA $idB
    A @('shell','input','keyevent','3') | Out-Null; OpenPackage 'com.example.appsandbox.testguest' 1; AssertXml 'Counter: 2' 'recent-task style B focus lost state'
    A @('shell','am','force-stop','com.example.appsandbox') | Out-Null
    OpenPackage 'com.example.appsandbox.testguest' 0
    AssertXml ([regex]::Escape("instance=$idA")) 'A restart opened the wrong instance'
    AssertXml 'Counter: 1' 'A restart state lost'
    OpenPackage 'com.example.appsandbox.testguest' 1
    AssertXml ([regex]::Escape("instance=$idB")) 'B restart opened the wrong instance'
    AssertXml 'Counter: 2' 'B restart state lost'
    AssertTwoTasks (DumpTasks 'relaunched.txt') $idA $idB
    OpenPackage 'com.example.appsandbox.testguest' 0; Tap 'Close workspace'
    $closed=''
    for($i=0;$i -lt 20;$i++) {
        $closed=DumpTasks 'closed-a.txt'
        if($closed -notmatch [regex]::Escape("appsandbox://workspace/$idA") -and
           $closed -match [regex]::Escape("appsandbox://workspace/$idB")){break}
        Start-Sleep -Milliseconds 300
    }
    if($closed -match [regex]::Escape("appsandbox://workspace/$idA") -or
       $closed -notmatch [regex]::Escape("appsandbox://workspace/$idB")){throw 'close isolation failed'}
    OpenPackage 'com.example.appsandbox.testguest' 0; Tap 'Delete current instance'; Tap 'Confirm delete instance'
    OpenStale $idA; AssertXml 'Instance unavailable or registry is corrupted' 'stale A intent did not fail closed'
    OpenPackage 'com.example.appsandbox.testguest' 0; AssertXml ([regex]::Escape("instance=$idB")) 'B unavailable after deleting A'
    AssertXml 'Counter: 2' 'B changed after deleting A'; Tap 'Guest Toggle'; AssertXml 'Counter: 0' 'B action failed after A deletion'
    $stateDir="files/guest-instances/$idB/files"
    $temp="$stateDir/counter.txt.tmp"
    A @('shell','run-as','com.example.appsandbox','mkdir',$temp) | Out-Null
    A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/t35.apk',"$temp/blocker") | Out-Null
    Tap 'Guest Increment'; AssertXml 'Guest action failed: Guest state temporary write failed' 'write failure did not fail closed'
    A @('shell','run-as','com.example.appsandbox','rm',"$temp/blocker") | Out-Null
    A @('shell','run-as','com.example.appsandbox','rmdir',$temp) | Out-Null
    OpenPackage 'com.example.appsandbox.testguest' 0; AssertXml 'Counter: 0' 'write failure changed committed state'
    A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/t35.apk',"$stateDir/counter.txt") | Out-Null
    A @('shell','run-as','com.example.appsandbox','cp','/data/local/tmp/t35.apk',"$stateDir/counter.txt.bak") | Out-Null
    OpenPackage 'com.example.appsandbox.testguest' 0
    AssertXml 'Guest actions unavailable: Guest state is corrupted' 'dual corruption did not fail closed'
    AssertXml 'Guest view disabled' 'corrupt state left Guest controls enabled'
    if((PmPath 'com.example.appsandbox.testguest')){throw 'Guest installed'}
    $final=DumpTasks 'final.txt'
    if($final -match 'com.example.appsandbox.testguest/.runtime.GuestMainActivity'){throw 'Guest ActivityRecord detected'}
}

$api=(& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if($Serial -eq '7b670025' -and $api -ne '31'){throw "API $api"}
if($Serial -eq 'emulator-5554' -and $api -ne '36'){throw "API $api"}
try {
    RunTask33
    RunTask34
    "status=PASS`ntask33=PASS`ntask34=PASS`nserial=$Serial" | Set-Content (Join-Path $report 'result.txt')
} catch {
    "status=FAIL`nserial=$Serial`nerror=$($_.Exception.Message)" | Set-Content (Join-Path $report 'result.txt')
    throw
}
