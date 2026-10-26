$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot '..\code\.runtime\api.pid'
if (-not (Test-Path $pidFile)) { Write-Output '没有记录中的本机后端进程'; return }
$apiPid = [int](Get-Content $pidFile -Raw)
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$apiPid"
if ($process -and $process.CommandLine -like '*music-service-0.1.0.jar*') {
    Stop-Process -Id $apiPid
    Write-Output "已停止后端进程 $apiPid"
}
Remove-Item -LiteralPath $pidFile
