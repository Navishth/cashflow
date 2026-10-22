$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-env.ps1')
. (Join-Path $PSScriptRoot 'node-env.ps1')
$jarPath=Join-Path $projectRoot 'backend\target\cashflow-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build first: .\scripts\backend.ps1 -MavenArgs @("-B","-ntp","package")' }
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'frontend\dist\index.html'))) { throw 'Build the frontend first: .\scripts\frontend.ps1 -NpmArgs @("run","build")' }
foreach ($port in @(8080,8001,5173)) {
    $probe=[Net.Sockets.TcpClient]::new()
    try { $probe.Connect('127.0.0.1',$port); throw "Port $port is already in use. Stop existing app processes first." }
    catch [Net.Sockets.SocketException] { }
    finally { $probe.Dispose() }
}
& (Join-Path $PSScriptRoot 'setup-local.ps1')
$processes=@()
try {
    $java=Start-Process -WindowStyle Hidden -PassThru -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList @('-jar',"`"$jarPath`"") -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $projectRoot '.local\backend.stdout.log') -RedirectStandardError (Join-Path $projectRoot '.local\backend.stderr.log')
    $processes+=@{name='backend';id=$java.Id;startTimeTicks=$java.StartTime.ToUniversalTime().Ticks}
    $mlPath=Join-Path $projectRoot 'ml-service'
    $python=Start-Process -WindowStyle Hidden -PassThru -FilePath (Join-Path $projectRoot '.venv\Scripts\python.exe') -ArgumentList @('-m','uvicorn','app.main:app','--app-dir',"`"$mlPath`"",'--host','127.0.0.1','--port','8001','--no-access-log') -WorkingDirectory $mlPath -RedirectStandardOutput (Join-Path $projectRoot '.local\ml.stdout.log') -RedirectStandardError (Join-Path $projectRoot '.local\ml.stderr.log')
    $processes+=@{name='ml';id=$python.Id;startTimeTicks=$python.StartTime.ToUniversalTime().Ticks}
    $vitePath=Join-Path $projectRoot 'frontend\node_modules\vite\bin\vite.js'
    $node=Start-Process -WindowStyle Hidden -PassThru -FilePath $nodePath -ArgumentList @("`"$vitePath`"",'preview','--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -RedirectStandardOutput (Join-Path $projectRoot '.local\frontend.stdout.log') -RedirectStandardError (Join-Path $projectRoot '.local\frontend.stderr.log')
    $processes+=@{name='frontend';id=$node.Id;startTimeTicks=$node.StartTime.ToUniversalTime().Ticks}
} finally {
    $processes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot '.local\processes.json')
}
foreach ($url in @('http://127.0.0.1:8080/actuator/health','http://127.0.0.1:8001/health','http://127.0.0.1:5173')) {
    $ready=$false
    for ($attempt=0;$attempt -lt 25;$attempt++) {
        try { $result=Invoke-WebRequest -Uri $url -TimeoutSec 2; if ($result.StatusCode -eq 200) { $ready=$true; break } } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw "Service did not become ready: $url. Inspect .local/*.log and run scripts/stop-local.ps1 before retrying." }
}
Write-Output 'CashFlow is ready at http://localhost:5173. Local verification messages are in .local/mail.'
