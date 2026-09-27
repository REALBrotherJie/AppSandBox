function Invoke-AdbBounded {
    param([string]$Adb,[string]$Serial,[string[]]$Args,[int]$TimeoutSec=20,[string]$ReportDir)
    $job = Start-Job -ScriptBlock { param($a,$s,$args) & $a -s $s @args 2>&1 } -ArgumentList $Adb,$Serial,$Args
    if (-not (Wait-Job $job -Timeout $TimeoutSec)) { Stop-Job $job -Force; Remove-Job $job -Force; throw "adb timeout after ${TimeoutSec}s: $($Args -join ' ')" }
    $out = Receive-Job $job; $code = $job.ChildJobs[0].JobStateInfo.Reason; Remove-Job $job -Force
    if ($ReportDir) { $out | Set-Content (Join-Path $ReportDir 'adb-last.txt') }
    if ($LASTEXITCODE -ne 0 -and $code) { throw "adb failed: $($Args -join ' ') $code" }
    $out
}

function New-RunReport {
    param([string]$Path,[string]$RunId,[string]$Serial,[string]$Api)
    New-Item -ItemType Directory -Force (Split-Path $Path) | Out-Null
    Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    "status=STARTED`nrunId=$RunId`nserial=$Serial`napi=$Api`nphase=starting" | Set-Content $Path
}

function Complete-RunReport {
    param([string]$Path,[string]$RunId,[string]$Serial,[string]$Api,[ValidateSet('PASS','FAIL')][string]$Status,[string]$Phase,[string]$ErrorCode='')
    "status=$Status`nrunId=$RunId`nserial=$Serial`napi=$Api`nphase=$Phase`nerrorCode=$ErrorCode" | Set-Content $Path
}
