                                                                        
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$ComposeArgs)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if ($ComposeArgs -contains 'up' -or $ComposeArgs -contains 'start' -or $ComposeArgs -contains 'restart') { & "$PSScriptRoot/wsl-keepalive.ps1" }
$linuxRoot=(& wsl -d Ubuntu --exec wslpath -a $root).Trim()
if ($LASTEXITCODE -ne 0) { throw 'No se pudo resolver el proyecto en WSL Ubuntu' }
$ErrorActionPreference='Continue'
& wsl -d Ubuntu -u root --cd $linuxRoot --exec docker compose @ComposeArgs
exit $LASTEXITCODE
