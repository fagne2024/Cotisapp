# Répare Flyway après un échec de la migration V21 (exercice).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/repair-flyway-v21.ps1

$ErrorActionPreference = "Stop"
$backend = Join-Path $PSScriptRoot "..\backend"
Push-Location $backend

Write-Host "Réparation de l'historique Flyway (migration V21 en échec)..." -ForegroundColor Cyan
Write-Host "(URL/user/password : backend/pom.xml — ajuster flyway.* si besoin)" -ForegroundColor DarkGray

if ($env:DB_USER) { mvn -q flyway:repair flyway:migrate "-Dflyway.user=$env:DB_USER" "-Dflyway.password=$env:DB_PASSWORD" }
else { mvn -q flyway:repair flyway:migrate }

Pop-Location
Write-Host "Terminé. Redémarrez le backend." -ForegroundColor Green
