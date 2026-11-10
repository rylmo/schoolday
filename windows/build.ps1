# Compiles SchooldaySetup.exe with the C# compiler built into Windows. Nothing to install.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$csc = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$out = "..\SchooldaySetup.exe"
& $csc /nologo /target:winexe /optimize+ /platform:anycpu "/out:$out" /win32icon:assets\schoolday.ico /win32manifest:app.manifest `
    /resource:assets\qr_vpn.png,qr_vpn.png /resource:assets\qr_ipa.png,qr_ipa.png `
    /r:System.Windows.Forms.dll /r:System.Drawing.dll SchooldaySetup.cs
if ($LASTEXITCODE -ne 0) { throw "Compile failed" }
Write-Host "Built $((Resolve-Path $out).Path)"
