$ErrorActionPreference='Stop'
$nodeCommand=Get-Command node.exe -ErrorAction SilentlyContinue
$nodePath=if ($nodeCommand) { $nodeCommand.Source } else { $null }
$nodeMajor=if ($nodePath) { [int]((& $nodePath --version).TrimStart('v').Split('.')[0]) } else { 0 }
if ($nodeMajor -lt 22 -or $nodeMajor -eq 23) {
    $bundledNode=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    if (Test-Path -LiteralPath $bundledNode) { $nodePath=$bundledNode }
    else { throw 'Install Node.js 22.12+ LTS or 24+ before continuing.' }
}
$env:PATH=(Split-Path $nodePath -Parent)+[IO.Path]::PathSeparator+$env:PATH
