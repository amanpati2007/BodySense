# start_backend.ps1 — BodySense Backend Launcher
# Usage:
#   .\start_backend.ps1          (development with auto-reload)
#   .\start_backend.ps1 -Prod    (production mode, no reload)

param([switch]$Prod)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Paths ──────────────────────────────────────────────────────────────────────
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$VenvPath   = Join-Path $ScriptDir "..\ML\.venv"
$ActivatePs = Join-Path $VenvPath "Scripts\Activate.ps1"

# ── Virtual Environment ────────────────────────────────────────────────────────
if (-not (Test-Path $ActivatePs)) {
    Write-Host "Creating Python virtual environment at $VenvPath ..." -ForegroundColor Yellow
    py -3.13 -m venv $VenvPath
    if ($LASTEXITCODE -ne 0) { throw "Failed to create virtual environment." }
}

& $ActivatePs

# ── Dependencies ───────────────────────────────────────────────────────────────
$ReqFile = Join-Path $ScriptDir "requirements.txt"
Write-Host "Installing / verifying backend dependencies..." -ForegroundColor Cyan
pip install -r $ReqFile --quiet
if ($LASTEXITCODE -ne 0) { throw "pip install failed." }

# ── Display LAN IP (useful for physical device testing) ───────────────────────
$LanIp = (Get-NetIPAddress -AddressFamily IPv4 |
          Where-Object { $_.IPAddress -notmatch "^(127\.|169\.254\.)" } |
          Select-Object -First 1 -ExpandProperty IPAddress)

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host "  BodySense Backend" -ForegroundColor Cyan
Write-Host "  Emulator URL : http://10.0.2.2:8000/" -ForegroundColor Green
if ($LanIp) {
    Write-Host "  Physical Dev : http://${LanIp}:8000/" -ForegroundColor Green
    Write-Host ""
    Write-Host "  To use physical device, add to gradle.properties:" -ForegroundColor Yellow
    Write-Host "    bodysense.target=device" -ForegroundColor Yellow
    Write-Host "    bodysense.lan.ip=$LanIp" -ForegroundColor Yellow
}
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host ""

# ── Launch ─────────────────────────────────────────────────────────────────────
$uvicornArgs = @("main:app", "--host", "0.0.0.0", "--port", "8000", "--log-level", "info")
if (-not $Prod) { $uvicornArgs += "--reload" }

Set-Location $ScriptDir
uvicorn @uvicornArgs
