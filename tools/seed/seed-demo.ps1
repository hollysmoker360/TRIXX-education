# Seed the local demo dataset (dev only).
# The backend seeds automatically at startup when SCHOOLSPHERE_SEED_ENABLED=true (DemoSeeder,
# spec 2.3). This script makes that explicit and idempotent so the demo accounts are always
# present locally, without touching a database that has been customised.
#
#   .\seed-demo.ps1            Check that the demo accounts are in place.
#   .\seed-demo.ps1 -Reset    Delete the demo school so the next backend start reseeds it.

param(
    [switch] Reset,
    [switch] SkipHealthCheck
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot   # tools/seed -> repository root
$backendDir = Join-Path $repoRoot 'backend'

function Ensure-DemoSchoolExists {
    Write-Host 'The running backend seeds the demo school on startup (DemoSeeder).'
    Write-Host 'Every demo account keeps the password Demo1234!.'
}

function Reset-DemoSchool {
    # Deletes the demo school (slug `kilimani`). DemoSeeder re-creates it on the next startup.
    # Destructive: development databases only.
    $psql = Join-Path $env:ProgramFiles 'PostgreSQL\18\bin\psql.exe'
    if (-not (Test-Path $psql)) {
        $psql = 'psql'
    }
    Write-Host "Deleting the demo school (requires PostgreSQL on PATH or in $env:ProgramFiles) ..."
    & $psql `
        -h localhost -p 5433 -U schoolsphere -d schoolsphere `
        -c "DELETE FROM school WHERE slug = 'kilimani';"
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not delete the demo school. Re-run as the owner role.'
    }
}

Invoke-CheckBackend -SkipHealthCheck:$SkipHealthCheck

if ($Reset) {
    Reset-DemoSchool
    Write-Host 'Demo school deleted. Restart the backend so DemoSeeder recreates it.'
} else {
    Ensure-DemoSchoolExists
}
