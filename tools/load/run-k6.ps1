# Load test runner for the SchoolSphere API (tools/load).
# Requires k6 installed and on PATH (`k6` executable).

param(
    [string] Scenario = 'load-test.js',
    [switch] NoSetup
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot   # tools/load -> repository root

if (-not $NoSetup) {
    & k6 run --vus 5 --duration 30s (Join-Path $repoRoot 'tools/load' $Scenario)
}

Write-Host 'Load test finished.'
