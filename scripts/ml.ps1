. (Join-Path $PSScriptRoot 'local-env.ps1')
Push-Location (Join-Path $projectRoot 'ml-service')
try { & (Join-Path $projectRoot '.venv\Scripts\python.exe') -m uvicorn app.main:app --host 127.0.0.1 --port 8001 }
finally { Pop-Location }
