param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\17\bin')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localPath = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Path $localPath -Force | Out-Null
$configPath = Join-Path $localPath 'environment.json'
if (-not (Test-Path -LiteralPath $configPath)) {
    $dbPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $mlKey = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    @{
        DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/cashflow'
        DATABASE_USER='cashflow'
        DATABASE_PASSWORD=$dbPassword
        ML_API_KEY=$mlKey
        ML_URL='http://127.0.0.1:8001'
        MAIL_MODE='file'
        MAIL_DIRECTORY=(Join-Path $localPath 'mail')
        FRONTEND_URL='http://localhost:5173'
        ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173'
        MODEL_PATH=(Join-Path $projectRoot 'ml-service\artifacts\model.joblib')
    } | ConvertTo-Json | Set-Content -LiteralPath $configPath
}
. (Join-Path $PSScriptRoot 'local-env.ps1')
$pgData = Join-Path $localPath 'postgres'
if (-not (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION'))) {
    $pwPath = Join-Path $localPath 'pg-password.tmp'
    [IO.File]::WriteAllText($pwPath, $env:DATABASE_PASSWORD)
    try {
        & (Join-Path $PostgresBin 'initdb.exe') -D $pgData -U cashflow --auth=scram-sha-256 "--pwfile=$pwPath" --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
    } finally { Remove-Item -LiteralPath $pwPath -ErrorAction SilentlyContinue }
    Add-Content -LiteralPath (Join-Path $pgData 'postgresql.conf') -Value "`nlisten_addresses = '127.0.0.1'`nport = 55432"
}
& (Join-Path $PostgresBin 'pg_ctl.exe') -D $pgData status 2>$null
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $pgData -l (Join-Path $localPath 'postgres.log') -w start
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed' }
}
$env:PGPASSWORD=$env:DATABASE_PASSWORD
$existing = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p 55432 -U cashflow -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='cashflow'"
if ($existing -ne '1') {
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p 55432 -U cashflow cashflow
    if ($LASTEXITCODE -ne 0) { throw 'Database creation failed' }
}
Remove-Item Env:PGPASSWORD
Write-Output 'Local database ready on 127.0.0.1:55432. Secrets stored only in ignored .local/environment.json.'
