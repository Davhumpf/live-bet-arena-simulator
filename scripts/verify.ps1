$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
  & npm.cmd ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias Node.js' }
  & npm.cmd run verify
  if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas fallaron' }
  $nativeDocker=Get-Command docker -ErrorAction SilentlyContinue
  function Invoke-Compose {
    if ($nativeDocker) { & docker compose @args }
    else { & "$PSScriptRoot/compose-wsl.ps1" @args }
  }
  Invoke-Compose config --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Configuración Compose inválida' }
  Invoke-Compose up -d --build --wait --wait-timeout 300
  if ($LASTEXITCODE -ne 0) { throw 'Algún contenedor no está saludable; revisa docker compose logs' }
  & node scripts/e2e.mjs
  if ($LASTEXITCODE -ne 0) { throw 'Falló la prueba de extremo a extremo' }
} finally { Pop-Location }
