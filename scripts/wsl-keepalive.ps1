param([switch]$Stop)
$ErrorActionPreference='Stop'
$stateDir=Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts'
$pidFile=Join-Path $stateDir 'wsl-keepalive.pid'
$keeper=$null
if (Test-Path -LiteralPath $pidFile) {
  $keeperId=[int](Get-Content -LiteralPath $pidFile -Raw).Trim()
  $candidate=Get-CimInstance Win32_Process -Filter "ProcessId = $keeperId" -ErrorAction SilentlyContinue
  if ($candidate -and $candidate.Name -eq 'wsl.exe' -and $candidate.CommandLine -like '*-d Ubuntu --exec sleep infinity*') { $keeper=$candidate }
}
if ($Stop) {
  if ($keeper) { Stop-Process -Id $keeper.ProcessId; Write-Output 'Proceso de continuidad WSL del proyecto detenido.' }
  if (Test-Path -LiteralPath $pidFile) { Remove-Item -LiteralPath $pidFile }
} elseif (-not $keeper) {
  [IO.Directory]::CreateDirectory($stateDir) | Out-Null
  $process=Start-Process -FilePath 'wsl.exe' -ArgumentList @('-d','Ubuntu','--exec','sleep','infinity') -WindowStyle Hidden -PassThru
  [IO.File]::WriteAllText($pidFile,[string]$process.Id)
  Write-Output 'Ubuntu WSL permanecerá activo para ejecutar la arena. Detener con ./scripts/wsl-keepalive.ps1 -Stop'
}
