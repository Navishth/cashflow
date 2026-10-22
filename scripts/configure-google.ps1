param([Parameter(Mandatory=$true)][string]$ClientJsonPath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $projectRoot '.local\environment.json'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw 'Run scripts/setup-local.ps1 first.' }
if (-not (Test-Path -LiteralPath $ClientJsonPath -PathType Leaf)) { throw 'Google client JSON file not found.' }
try { $download = Get-Content -LiteralPath $ClientJsonPath -Raw | ConvertFrom-Json }
catch { throw 'Could not read the Google client JSON. Download the Web application client JSON from Google Auth Platform.' }
$client = $download.web
if (-not $client) { throw 'Use a Web application OAuth client, not a Desktop app or service account.' }
if ([string]::IsNullOrWhiteSpace($client.client_id) -or [string]::IsNullOrWhiteSpace($client.client_secret)) { throw 'The downloaded client must include both client_id and client_secret.' }
if ($client.client_id -notmatch '^[a-zA-Z0-9_-]+\.apps\.googleusercontent\.com$') { throw 'The client ID is not a Google OAuth client ID.' }
$settings = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$frontend = ([string]$settings.FRONTEND_URL).TrimEnd('/')
if ($frontend -notin @('http://localhost:5173','http://127.0.0.1:5173')) { throw 'This helper configures the local app only. See docs/GOOGLE_AUTH.md for deployment configuration.' }
$callback = $frontend + '/login/oauth2/code/google'
if ($callback -cnotin @($client.redirect_uris)) { throw "Add this exact authorized redirect URI in Google Auth Platform, then download the client JSON again: $callback" }
$settings | Add-Member -NotePropertyName GOOGLE_CLIENT_ID -NotePropertyValue ([string]$client.client_id).Trim() -Force
$settings | Add-Member -NotePropertyName GOOGLE_CLIENT_SECRET -NotePropertyValue ([string]$client.client_secret).Trim() -Force
$settings | Add-Member -NotePropertyName GOOGLE_REDIRECT_URI -NotePropertyValue $callback -Force
# Only the ignored local configuration receives credentials. No secret is written to terminal output.
$temporaryPath = $configPath + '.google-import.tmp'
try {
    [IO.File]::WriteAllText($temporaryPath, ($settings | ConvertTo-Json -Depth 10))
    Move-Item -LiteralPath $temporaryPath -Destination $configPath -Force
} finally {
    if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath }
}
Write-Output "Google credentials saved locally. Callback: $callback"
Write-Output 'Restart the app with scripts/stop-local.ps1 then scripts/start-local.ps1. Continue with Google will appear on sign-in and registration.'
