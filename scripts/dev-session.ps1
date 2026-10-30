# Starts a local test session: the dev server plus three clients (Dev, Dev2, Dev3) that join it automatically,
# then tiles the game windows so they do not overlap. Players are opped on first join via run-server/ops.json.
# Usage (from the repo root):  powershell -File scripts/dev-session.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Loom needs JDK 21+; prefer an installed Temurin 21 when JAVA_HOME points at an older JDK.
$jdk21 = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk21) { $env:JAVA_HOME = $jdk21.FullName }

function Start-Task($task, $log) {
    Start-Process -FilePath "$root\gradlew.bat" -ArgumentList $task, '--console=plain' -WindowStyle Minimized `
        -RedirectStandardOutput "$root\build\$log.log" -RedirectStandardError "$root\build\$log.err.log" | Out-Null
}
New-Item -ItemType Directory -Force "$root\build" | Out-Null

Write-Output 'Starting server...'
Start-Task 'runServer' 'dev-server'
$deadline = (Get-Date).AddMinutes(3)
while (-not (Select-String -Path "$root\build\dev-server.log" -Pattern 'Done \(' -Quiet -ErrorAction SilentlyContinue)) {
    if ((Get-Date) -gt $deadline) { throw 'Server did not start within 3 minutes; see build/dev-server.log' }
    Start-Sleep -Seconds 2
}

# Clients are staggered so Gradle is not compiling three times at once.
foreach ($task in 'runClient', 'runClient2', 'runClient3') {
    Write-Output "Starting $task..."
    Start-Task $task "dev-$task"
    Start-Sleep -Seconds 25
}

Write-Output 'Waiting for game windows...'
$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 5
    $windows = @(Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match '^\[Dev\d?\]' })
} while ($windows.Count -lt 3 -and (Get-Date) -lt $deadline)
& "$PSScriptRoot\arrange-windows.ps1"
