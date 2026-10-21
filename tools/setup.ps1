# Universal 1-Click Android TV Transformation Suite for Windows
param (
    [string]$TargetHost = "",
    [switch]$Yes = $false
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       🍎 Universal Apple TV Launcher Setup Suite         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Check for bash (Git Bash / WSL / MSYS2)
$bashCmd = Get-Command bash -ErrorAction SilentlyContinue
if ($bashCmd) {
    Write-Host "==> Running unified Audit & Transformation pipeline via Bash..." -ForegroundColor Green
    $argsList = @("$ScriptDir/setup.sh")
    if (-not [string]::IsNullOrWhiteSpace($TargetHost)) { $argsList += $TargetHost }
    if ($Yes) { $argsList += "-y" }
    & bash @argsList
    exit $LASTEXITCODE
} else {
    Write-Error @"
Error: Bash environment not detected.
Please run setup using Git Bash (bundled with Git for Windows) or WSL:
    ./tools/setup.sh $TargetHost
"@
    exit 1
}
