$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PythonExe = Join-Path $ProjectDir ".venv\Scripts\python.exe"
$Scheduler = Join-Path $ProjectDir "scheduler.py"

if (-not (Test-Path $PythonExe)) {
    throw "Chưa có virtual environment. Hãy chạy run.ps1 trước."
}

$Action = New-ScheduledTaskAction `
    -Execute $PythonExe `
    -Argument "`"$Scheduler`"" `
    -WorkingDirectory $ProjectDir

$Trigger = New-ScheduledTaskTrigger -AtLogOn
$Settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 5)

Register-ScheduledTask `
    -TaskName "AI-TechFlow-MVP" `
    -Action $Action `
    -Trigger $Trigger `
    -Settings $Settings `
    -Description "Tự chạy AI TechFlow scheduler khi đăng nhập Windows." `
    -Force

Write-Host "Đã đăng ký task AI-TechFlow-MVP."
