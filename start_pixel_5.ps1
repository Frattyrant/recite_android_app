# Start Pixel 5 AVD
$root = $PSScriptRoot
if ($null -eq $root -or $root -eq "") { $root = Get-Location }

# Source environment
. "$root\setup_env.ps1"

if (-not (Test-Path "$env:ANDROID_HOME\emulator\emulator.exe")) {
    Write-Error "Emulator not found at $env:ANDROID_HOME\emulator\emulator.exe"
    exit 1
}

Write-Host "Starting Pixel_5..." -ForegroundColor Cyan
# Start in background
Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" `
    -ArgumentList "-avd Pixel_5", "-no-snapshot-load" `
    -WindowStyle Hidden
