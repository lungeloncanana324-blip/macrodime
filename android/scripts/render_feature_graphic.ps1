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
# The page loads the app's own font and photo from res/, which a file:// page
# may only do with file access allowed. Its own profile keeps an Edge window
# that is already open from taking the job over.
$userData = Join-Path ([IO.Path]::GetTempPath()) "macrodime-feature-graphic"
if (Test-Path $png) { Remove-Item $png }
& $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 `
    --allow-file-access-from-files "--user-data-dir=$userData" `
    --window-size=1024,500 "--screenshot=$png" $url | Out-Null
for ($i = 0; $i -lt 40 -and -not (Test-Path $png); $i++) { Start-Sleep -Milliseconds 500 }
Start-Sleep -Seconds 1
if (-not (Test-Path $png)) { throw "Edge did not write $png" }
Write-Output "wrote $png"
