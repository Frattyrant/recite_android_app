param(
    [string]$ContentPath = "app/src/main/assets/content/words_v1.json",
    [string]$OutputDirectory = "app/src/main/assets/audio",
    [string]$ManifestPath = "app/src/main/assets/content/audio_manifest_v1.json",
    [string]$ModelPath = "tools/.piper-models/en_US-lessac-medium/en_US-lessac-medium.onnx",
    [string]$FfmpegPath = "D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe",
    [int]$Start = 0,
    [int]$Limit = 0,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $python)) {
    throw "Workspace Piper environment not found: $python"
}

$arguments = @(
    (Join-Path $PSScriptRoot "generate_audio.py"),
    "--content", (Join-Path $root $ContentPath),
    "--output", (Join-Path $root $OutputDirectory),
    "--manifest", (Join-Path $root $ManifestPath),
    "--model", (Join-Path $root $ModelPath),
    "--ffmpeg", $FfmpegPath,
    "--start", $Start,
    "--limit", $Limit
)
if ($Force) {
    $arguments += "--force"
}

& $python @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Piper audio generation failed with exit code $LASTEXITCODE"
}
