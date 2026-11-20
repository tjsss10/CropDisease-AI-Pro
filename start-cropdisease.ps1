# =====================================================================
#  CropDisease AI Pro - one-click start (same flow as FitJanta)
#  1. Waits for MySQL (service MYSQL80) to accept connections
#  2. Starts the Spring Boot app in a minimized window
#  3. Opens the QR page so a phone can scan and open the app
# =====================================================================

$appDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$appPort = 8081
$qrPage  = "http://localhost:$appPort/qr.html"

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        return ($async.AsyncWaitHandle.WaitOne(1000) -and $client.Connected)
    } catch { return $false } finally { $client.Close() }
}

Write-Host ""
Write-Host "  CROPDISEASE AI PRO - starting up" -ForegroundColor Green
Write-Host "  --------------------------------"

if (Test-Port $appPort) {
    Write-Host "  Already running. Opening the QR page..."
    Start-Process $qrPage
    exit 0
}

Write-Host "  Waiting for MySQL on port 3306..."
$mysqlReady = $false
for ($i = 0; $i -lt 90; $i++) {
    if (Test-Port 3306) { $mysqlReady = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $mysqlReady) {
    Write-Host "  MySQL is not running." -ForegroundColor Red
    Write-Host "  Start it: Win + R -> services.msc -> MYSQL80 -> right-click -> Start"
    Read-Host "  Press Enter to close"
    exit 1
}
Write-Host "  MySQL is running." -ForegroundColor Green

Write-Host "  Starting the server (about 20-30 seconds)..."
Start-Process -FilePath "$appDir\gradlew.bat" -ArgumentList "bootRun", "--console=plain" `
              -WorkingDirectory $appDir -WindowStyle Minimized

for ($i = 0; $i -lt 120; $i++) {
    if (Test-Port $appPort) {
        Start-Sleep -Seconds 2
        Write-Host "  CropDisease AI Pro is running at http://localhost:$appPort" -ForegroundColor Green
        Start-Process $qrPage
        exit 0
    }
    Start-Sleep -Seconds 2
}
Write-Host "  The server did not start within 4 minutes." -ForegroundColor Red
Write-Host "  Open the minimized 'gradlew' window on the taskbar to see the error."
Read-Host "  Press Enter to close"
exit 1
