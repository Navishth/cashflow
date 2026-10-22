param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs = @('spring-boot:run'))
. (Join-Path $PSScriptRoot 'local-env.ps1')
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$wrapperPath = Join-Path $projectRoot 'backend\mvnw.cmd'
$mavenPath = if (Test-Path -LiteralPath $wrapperPath) { $wrapperPath } elseif ($maven) { $maven.Source } else { 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & $mavenPath "-Dmaven.repo.local=$projectRoot\.local\m2" -s maven-settings.xml @MavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
