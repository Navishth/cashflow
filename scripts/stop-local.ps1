param([switch]$Database)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$registryPath=Join-Path $projectRoot '.local\processes.json'
if (Test-Path -LiteralPath $registryPath) {
    $entries=Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
    foreach ($entry in $entries) {
        $process=Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq $entry.startTimeTicks) {
            Stop-Process -Id $process.Id
            Write-Output "Stopped owned $($entry.name) process."
        }
    }
    Remove-Item -LiteralPath $registryPath
}
if ($Database) {
    $expected=[IO.Path]::GetFullPath((Join-Path $projectRoot '.local\postgres'))
    if (-not $expected.StartsWith([IO.Path]::GetFullPath($projectRoot)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected database path.' }
    & 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' -D $expected -m fast -w stop
}
