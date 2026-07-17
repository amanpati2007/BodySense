param (
    [switch]$NoBackend = $false
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " BodySense Phase 2 Dev Environment Setup" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Resolve Android SDK Path
$localProp = "local.properties"
$sdkPath = $null

if (Test-Path $localProp) {
    Write-Host "[*] Reading local.properties..."
    $content = Get-Content $localProp
    foreach ($line in $content) {
        if ($line.StartsWith("sdk.dir=")) {
            $rawPath = $line.Substring(8).Trim()
            # Handle Windows paths in Java properties (e.g. D\:\\Android Studio...)
            $sdkPath = $rawPath -replace '\\:', ':' -replace '\\\\', '\'
            break
        }
    }
}

if (-not $sdkPath) {
    # Fallback to default Windows SDK path
    $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
}

Write-Host "[+] Resolved Android SDK Path: $sdkPath" -ForegroundColor Green

# 2. Inject ADB into PATH temporarily
$adbDir = "$sdkPath\platform-tools"
if (Test-Path "$adbDir\adb.exe") {
    if ($env:PATH -notmatch [regex]::Escape($adbDir)) {
        $env:PATH = "$adbDir;" + $env:PATH
        Write-Host "[+] Injected ADB into active terminal session PATH." -ForegroundColor Green
    } else {
        Write-Host "[-] ADB is already in PATH." -ForegroundColor Yellow
    }
} else {
    Write-Host "[!] ADB not found at $adbDir. Wireless debugging cannot be initialized automatically." -ForegroundColor Red
}

# 3. Start Backend if requested
if (-not $NoBackend) {
    Write-Host "[*] Starting FastAPI Backend..." -ForegroundColor Cyan
    Set-Location "Backend"
    Start-Process -FilePath "..\ML\.venv\Scripts\python.exe" -ArgumentList "-m uvicorn main:app --reload --host 0.0.0.0 --port 8000" -NoNewWindow
    Write-Host "[+] Backend running on http://localhost:8000" -ForegroundColor Green
    Set-Location ".."
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Environment ready!" -ForegroundColor Cyan
Write-Host " To connect physical device for wireless debugging:"
Write-Host "   1. adb pair <IP>:<PORT>"
Write-Host "   2. adb connect <IP>:<PORT>"
Write-Host "==========================================" -ForegroundColor Cyan
