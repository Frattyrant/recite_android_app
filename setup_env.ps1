# Setup environment variables for MIearn project
$root = $PSScriptRoot
if ($null -eq $root -or $root -eq "") { $root = Get-Location }

Write-Host "Setting up environment for MIearn at $root..." -ForegroundColor Cyan

# JDK
if (-not $env:JAVA_HOME) {
    if (Test-Path "D:\Android_Studio\jbr") {
        $env:JAVA_HOME = "D:\Android_Studio\jbr"
        Write-Host "JAVA_HOME set to $env:JAVA_HOME"
    } else {
        Write-Warning "Default JDK path not found. Please set JAVA_HOME manually."
    }
}

# Android SDK
$sdk_path = Join-Path $root ".android-sdk"
if (Test-Path $sdk_path) {
    $env:ANDROID_HOME = $sdk_path
    $env:ANDROID_SDK_ROOT = $sdk_path
    Write-Host "ANDROID_HOME set to $env:ANDROID_HOME"
}

# Gradle Home (optional, keeps project isolated)
$gradle_user_home = Join-Path $root ".gradle-user-home"
if (Test-Path $gradle_user_home) {
    $env:GRADLE_USER_HOME = $gradle_user_home
    Write-Host "GRADLE_USER_HOME set to $env:GRADLE_USER_HOME"
}

# Path
$paths_to_add = @(
    (Join-Path $env:JAVA_HOME "bin"),
    (Join-Path $env:ANDROID_HOME "platform-tools"),
    (Join-Path $env:ANDROID_HOME "emulator"),
    (Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin")
)

foreach ($p in $paths_to_add) {
    if (Test-Path $p) {
        if ($env:PATH -notlike "*$p*") {
            $env:PATH = "$p;$env:PATH"
            Write-Host "Added to PATH: $p"
        }
    }
}

Write-Host "Environment setup complete!" -ForegroundColor Green
