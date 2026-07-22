$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root "web-manager"
$frontend = Join-Path $backend "frontend"
$npm = "C:\Program Files\nodejs\npm.cmd"

if (-not (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
    throw "Docker chưa được cài. Cài Docker Desktop, sau đó chạy lại script này."
}

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if (-not $maven) {
    $bundledMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path -LiteralPath $bundledMaven) { $maven = $bundledMaven }
}
if (-not $maven) { throw "Không tìm thấy Maven. Hãy cài Maven 3.9+." }
if (-not (Test-Path -LiteralPath $npm)) { throw "Không tìm thấy npm.cmd." }

Push-Location $root
try {
    docker compose up -d postgres
} finally {
    Pop-Location
}

Push-Location $frontend
try {
    & $npm ci
    & $npm run build
} finally {
    Pop-Location
}

Push-Location $backend
try {
    & $maven spring-boot:run
} finally {
    Pop-Location
}
