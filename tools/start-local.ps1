param([switch]$Import)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$apiDir = Join-Path $root 'code'
$runtimeDir = Join-Path $apiDir '.runtime'
$jar = Join-Path $apiDir 'target\music-service-0.1.0.jar'
$config = Join-Path $root '.env'
if (-not (Test-Path $config)) { throw "找不到 $config；请根据 code/.env.example 创建项目根目录 .env" }
if (-not (Test-Path $jar)) { throw "找不到 $jar；请先用 Maven 打包" }

Get-Content $config | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
$mysqlPort = if ($env:MYSQL_HOST_PORT) { $env:MYSQL_HOST_PORT } else { '3307' }
$env:DB_URL = "jdbc:mysql://localhost:$mysqlPort/shengyu?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:MUSIC_DIR = Join-Path $root 'music'
$env:IMAGE_DIR = Join-Path $root 'img'
if ($Import) { $env:IMPORT_LOCAL_MUSIC = 'true' }

$ffmpeg = Get-ChildItem (Join-Path $root 'tools\ffmpeg') -Recurse -Filter ffmpeg.exe -File -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if ($ffmpeg) { $env:FFMPEG_BIN = $ffmpeg }

New-Item -ItemType Directory -Force $runtimeDir | Out-Null
$pidFile = Join-Path $runtimeDir 'api.pid'
if (Test-Path $pidFile) {
    $oldPid = [int](Get-Content $pidFile -Raw)
    if (Get-Process -Id $oldPid -ErrorAction SilentlyContinue) { throw "后端进程 $oldPid 已在运行" }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
$process = Start-Process -FilePath $java -ArgumentList '-jar', $jar -WorkingDirectory $apiDir `
    -RedirectStandardOutput (Join-Path $runtimeDir 'api.out.log') `
    -RedirectStandardError (Join-Path $runtimeDir 'api.err.log') -WindowStyle Hidden -PassThru
Set-Content -Path $pidFile -Value $process.Id
Write-Output "后端进程：$($process.Id)，接口：http://localhost:8080，日志：$runtimeDir"
