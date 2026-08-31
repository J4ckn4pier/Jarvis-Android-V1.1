param(
    [string]$Model = "qwen3:4b-instruct"
)

$ErrorActionPreference = "Stop"
$Port = 11434
$ServerBind = "0.0.0.0:$Port"

Write-Host "JARVIS Free Local Cortex setup" -ForegroundColor Cyan
Write-Host "This uses Ollama on this PC. There is no per-request API or token bill." -ForegroundColor Gray

function Find-Ollama {
    $cmd = Get-Command ollama -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw "Ollama is not installed and winget is unavailable. Install Ollama for Windows, then rerun this script."
    }

    Write-Host "Installing Ollama for the current Windows machine..." -ForegroundColor Yellow
    winget install --id Ollama.Ollama -e --accept-package-agreements --accept-source-agreements

    $candidate = Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe"
    if (Test-Path $candidate) { return $candidate }
    $cmd = Get-Command ollama -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "Ollama installation finished but ollama.exe was not found. Open a new PowerShell window and rerun this script."
}

function Get-PrivateLanAddress {
    $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
            $_.IPAddress -ne "127.0.0.1" -and
            $_.IPAddress -notlike "169.254.*" -and
            ($_.IPAddress -like "10.*" -or $_.IPAddress -like "192.168.*" -or (
                $_.IPAddress -match '^172\.(\d+)\.' -and
                [int]$Matches[1] -ge 16 -and [int]$Matches[1] -le 31
            ))
        } |
        Sort-Object InterfaceMetric
    return ($addresses | Select-Object -First 1).IPAddress
}

$Ollama = Find-Ollama

# Persist the LAN bind for future Ollama launches. The server is still local to the user's PC;
# Windows Firewall remains in control of which network profiles can reach it.
[Environment]::SetEnvironmentVariable("OLLAMA_HOST", $ServerBind, "User")
$env:OLLAMA_HOST = $ServerBind

# Restart only Ollama itself so it inherits the new bind address. No unrelated processes are touched.
Get-Process -Name "ollama" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500
Start-Process -FilePath $Ollama -ArgumentList "serve" -WindowStyle Hidden

# Talk to the server through loopback for setup, while the server itself remains bound to the LAN.
$env:OLLAMA_HOST = "127.0.0.1:$Port"
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/tags" -Method Get -TimeoutSec 2 | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Milliseconds 500
    }
}
if (-not $ready) {
    throw "Ollama did not start on port $Port. Check Ollama's Windows logs and rerun this script."
}

Write-Host "Downloading local assistant model: $Model" -ForegroundColor Yellow
& $Ollama pull $Model
if ($LASTEXITCODE -ne 0) { throw "ollama pull failed for $Model" }

$LanIp = Get-PrivateLanAddress
if (-not $LanIp) {
    Write-Warning "The model is installed, but no private LAN IPv4 address was detected. Connect the PC and phone to the same private network and rerun this script."
    exit 0
}

$Endpoint = "http://${LanIp}:$Port/v1/chat/completions"
Write-Host "" 
Write-Host "LOCAL CORTEX READY" -ForegroundColor Green
Write-Host "Model:    $Model"
Write-Host "Endpoint: $Endpoint"
Write-Host "" 
Write-Host "In JARVIS provider settings choose OpenAI-compatible, enter the model and endpoint above, and leave the API key blank." -ForegroundColor Cyan
Write-Host "If Windows asks whether Ollama may communicate on the network, allow PRIVATE networks only." -ForegroundColor Yellow
Write-Host "Do not expose port $Port directly to the public internet." -ForegroundColor Yellow
