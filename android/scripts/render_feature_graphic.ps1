# Renders android/play-store/feature-graphic.html to feature-graphic.png
# (1024 x 500, the size Play Console requires) with headless Microsoft Edge.
#
#   powershell -ExecutionPolicy Bypass -File android/scripts/render_feature_graphic.ps1

$ErrorActionPreference = "Stop"
$store = Resolve-Path (Join-Path $PSScriptRoot "..\play-store")
$html = Join-Path $store "feature-graphic.html"
$png = Join-Path $store "feature-graphic.png"
$edge = "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
if (-not (Test-Path $edge)) { $edge = "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe" }

$url = "file:///" + ($html -replace "\\", "/")
& $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 `
    --window-size=1024,500 "--screenshot=$png" $url | Out-Null
Start-Sleep -Seconds 2
if (-not (Test-Path $png)) { throw "Edge did not write $png" }
Write-Output "wrote $png"
