# Builds the signed APK on this PC and publishes it as the latest GitHub release.
# Every installed copy of the app finds it through update.json at its next launch.
#
# Before running: raise versionCode and versionName in app/build.gradle.kts and push to main.
# Usage:  .\scripts\publish-release.ps1 -Notes "What changed"

param([string]$Notes = "")

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$repo = "rylmo/schoolday"
$gh = "C:\Program Files\GitHub CLI\gh.exe"
if (-not (Test-Path $gh)) { $gh = "gh" }

$gradle = Get-Content "app\build.gradle.kts" -Raw
$code = [int][regex]::Match($gradle, 'versionCode = (\d+)').Groups[1].Value
$name = [regex]::Match($gradle, 'versionName = "([^"]+)"').Groups[1].Value
$tag = "v$name"

& $gh release view $tag --repo $repo *> $null
if ($LASTEXITCODE -eq 0) { throw "Release $tag already exists. Raise the version first." }

& .\gradlew.bat assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$out = "build\release"
New-Item -ItemType Directory -Force $out | Out-Null
$apkName = "Schoolday-$name.apk"
$apk = Join-Path $out $apkName
Copy-Item "app\build\outputs\apk\release\app-release.apk" $apk -Force

$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$manifest = Join-Path $out "update.json"
$json = @{
    versionCode = $code
    versionName = $name
    url         = "https://github.com/$repo/releases/download/$tag/$apkName"
    sha256      = $sha
    minSdk      = 31
} | ConvertTo-Json
# No byte-order mark: the app parses this file as plain JSON.
[IO.File]::WriteAllText((Join-Path (Get-Location) $manifest), $json, (New-Object Text.UTF8Encoding $false))

& $gh release create $tag $apk $manifest --repo $repo --target main --title "Schoolday $name" --notes "$Notes" --latest
if ($LASTEXITCODE -ne 0) { throw "Publishing failed" }
Write-Host "Published Schoolday $name (versionCode $code)"
