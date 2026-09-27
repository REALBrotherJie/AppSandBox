param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference='Stop'; $adb='D:/Company/Install/Android/SDK/platform-tools/adb.exe'; $root=Split-Path $PSScriptRoot -Parent
$report=Join-Path $root "build/reports/task48/logical-matrix/$Serial"; New-Item -ItemType Directory -Force $report|Out-Null
. (Join-Path $PSScriptRoot 'task47-adb-helper.ps1')
function A([string[]]$argv){Invoke-AdbBounded -Adb $adb -Serial $Serial -CommandArgs $argv -TimeoutSec 30 -ReportDir $report}
$runId="matrix-$([guid]::NewGuid().ToString('N'))"; $path="files/task48-matrix-$runId.result"
A @('install','-r',(Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'))|Out-Null
A @('shell','run-as','com.example.appsandbox','rm','-f',$path)|Out-Null
A @('shell','am','start','-W','-n','com.example.appsandbox/.automation.Task48LogicalMatrixActivity','--es','runId',$runId)|Out-Null
for($i=0;$i -lt 40;$i++){try{$text=A @('shell','run-as','com.example.appsandbox','cat',$path);if($text -match "runId=$([regex]::Escape($runId))" -and $text -match 'status=PASS|status=FAIL'){$text|Set-Content (Join-Path $report 'result.txt');if($text -notmatch 'status=PASS'){Write-Error $text;exit 1};exit 0}}catch{};Start-Sleep -Milliseconds 250}
throw 'logical matrix report timeout'
