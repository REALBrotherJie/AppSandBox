function Invoke-AdbBounded {
    param([string]$Adb,[string]$Serial,[string[]]$CommandArgs,[int]$TimeoutSec=20,[string]$ReportDir)
    $psi = [Diagnostics.ProcessStartInfo]::new(); $psi.FileName = $Adb; $psi.UseShellExecute = $false; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $allArgs = @('-s', $Serial) + @($CommandArgs)
    $psi.Arguments = (($allArgs | ForEach-Object { $v = [string]$_; if ($v -match '[\s"]') { '"' + ($v -replace '([\\"])','\$1') + '"' } else { $v } }) -join ' ')
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $psi; [void]$process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSec * 1000)) { $process.Kill(); $process.Dispose(); throw "ADB_TIMEOUT: $($CommandArgs -join ' ')" }
    $stdoutTask.Wait(); $stderrTask.Wait(); $out = $stdoutTask.Result + $stderrTask.Result; $code = $process.ExitCode; $process.Dispose()
    if ($ReportDir) { $out | Set-Content (Join-Path $ReportDir 'adb-last.txt') }
    if ($code -ne 0) { throw "ADB_EXIT_$code`: $($CommandArgs -join ' ')`n$out" }
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
