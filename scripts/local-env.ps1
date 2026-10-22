$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $projectRoot '.local\environment.json'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Run scripts/setup-local.ps1 first.' }
$settings = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
foreach ($item in $settings.PSObject.Properties) { [Environment]::SetEnvironmentVariable($item.Name, [string]$item.Value, 'Process') }
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.0.2\jbr' }
$env:MAVEN_USER_HOME = Join-Path $projectRoot '.local\maven'
