param([Parameter(ValueFromRemainingArguments=$true)][string[]]$NpmArgs=@('run','dev'))
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'node-env.ps1')
$localNpm=Join-Path $projectRoot '.local\npm-runtime\node_modules\npm\bin\npm-cli.js'
$npmCommand=Get-Command npm.cmd -ErrorAction SilentlyContinue
$npmCli=if (Test-Path -LiteralPath $localNpm) { $localNpm }
    elseif ($npmCommand) { Join-Path (Split-Path $npmCommand.Source -Parent) 'node_modules\npm\bin\npm-cli.js' }
    else { throw 'Install npm with Node.js first.' }
Push-Location (Join-Path $projectRoot 'frontend')
try {
    & $nodePath $npmCli @NpmArgs
    if ($LASTEXITCODE -ne 0) { throw "npm exited with code $LASTEXITCODE." }
} finally { Pop-Location }
