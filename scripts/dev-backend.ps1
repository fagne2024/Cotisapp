# Démarre le backend avec recompilation + redémarrage automatiques (Spring DevTools).
# Usage : depuis la racine du projet
#   powershell -ExecutionPolicy Bypass -File scripts/dev-backend.ps1

$ErrorActionPreference = "Stop"
$backendRoot = (Join-Path $PSScriptRoot "..\backend" | Resolve-Path).Path
$srcRoot = Join-Path $backendRoot "src"

Write-Host "Backend : $backendRoot" -ForegroundColor Cyan
Write-Host "Profil Spring : dev (DevTools actif)" -ForegroundColor Cyan
Write-Host "Arrêt : Ctrl+C" -ForegroundColor DarkGray

function Invoke-BackendCompile {
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Recompilation…" -ForegroundColor Yellow
    Push-Location $backendRoot
    & mvn -q compile -DskipTests
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) {
        Write-Host "Échec de compilation (code $code)" -ForegroundColor Red
    } else {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Classes à jour — redémarrage DevTools." -ForegroundColor Green
    }
}

Push-Location $backendRoot
& mvn -q compile -DskipTests
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
Pop-Location

$watchJob = Start-Job -ArgumentList $srcRoot, $backendRoot -ScriptBlock {
    param($watchRoot, $mavenRoot)
    $watcher = New-Object System.IO.FileSystemWatcher
    $watcher.Path = $watchRoot
    $watcher.IncludeSubdirectories = $true
    $watcher.EnableRaisingEvents = $true
    $watcher.Filter = "*.*"
    $types = [IO.WatcherChangeTypes]::Changed -bor
        [IO.WatcherChangeTypes]::Created -bor
        [IO.WatcherChangeTypes]::Deleted -bor
        [IO.WatcherChangeTypes]::Renamed

    while ($true) {
        $change = $watcher.WaitForChanged($types, 2000)
        if ($change.TimedOut) { continue }
        $name = $change.Name
        if ($name -notmatch '\.(java|yml|yaml|properties|xml)$') { continue }
        Start-Sleep -Milliseconds 700
        Push-Location $mavenRoot
        & mvn -q compile -DskipTests 2>&1 | Out-Null
        Pop-Location
    }
}

try {
    Push-Location $backendRoot
    $env:SPRING_PROFILES_ACTIVE = "dev"
    $env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
    & mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"
    exit $LASTEXITCODE
} finally {
    if ($watchJob) {
        Stop-Job $watchJob -ErrorAction SilentlyContinue
        Remove-Job $watchJob -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}
