# generate-courier-icons.ps1
# Generate Android launcher icons for BCE Courier app using ImageMagick

$ErrorActionPreference = 'Stop'

function Try-InstallMagick {
    if (Get-Command magick -ErrorAction SilentlyContinue) {
        Write-Host 'magick already installed' -ForegroundColor Green
        return $true
    }

    Write-Host 'magick not found — attempting winget install...' -ForegroundColor Yellow
    try {
        winget install --id ImageMagick.ImageMagick -e --silent -h
        Start-Sleep -Seconds 2
    } catch {
        Write-Host "winget install failed or not available: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    if (Get-Command magick -ErrorAction SilentlyContinue) {
        Write-Host 'magick installed via winget' -ForegroundColor Green
        return $true
    }

    Write-Host 'Trying choco install ImageMagick...' -ForegroundColor Yellow
    try {
        choco install imagemagick -y --no-progress
    } catch {
        Write-Host "choco install failed or not available: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    if (Get-Command magick -ErrorAction SilentlyContinue) {
        Write-Host 'magick installed via choco' -ForegroundColor Green
        return $true
    }

    return $false
}

if (-not (Try-InstallMagick)) {
    Write-Host 'ImageMagick not available. Please install ImageMagick manually and re-run.' -ForegroundColor Red
    exit 1
}

# Use the BCE Courier splash logo
$src = Join-Path $PSScriptRoot '..\..\bce splash item.png'
if (-not (Test-Path $src)) {
    Write-Host "Source $src not found" -ForegroundColor Red
    exit 1
}

Write-Host "Using source logo: $src" -ForegroundColor Cyan

$configs = @(
    @{size=48; d='mipmap-mdpi'},
    @{size=72; d='mipmap-hdpi'},
    @{size=96; d='mipmap-xhdpi'},
    @{size=144; d='mipmap-xxhdpi'},
    @{size=192; d='mipmap-xxxhdpi'}
)

foreach ($c in $configs) {
    $outDir = Join-Path $PSScriptRoot "..\app\src\main\res\$($c.d)"
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

    $out = Join-Path $outDir 'ic_launcher.png'
    $outR = Join-Path $outDir 'ic_launcher_round.png'

    Write-Host "Generating $($c.size)x$($c.size) -> $($c.d)" -ForegroundColor Yellow
    & magick "$src" -resize "$($c.size)x$($c.size)" "$out"
    & magick "$src" -resize "$($c.size)x$($c.size)" "$outR"

    if (Test-Path $out) {
        Write-Host "  OK Created $out" -ForegroundColor Green
    }
}

Write-Host "`nIcon generation complete! Now rebuild the app." -ForegroundColor Green
Write-Host "Run: ./gradlew clean assembleDebug" -ForegroundColor Cyan

