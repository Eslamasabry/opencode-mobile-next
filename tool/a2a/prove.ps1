$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskPython = 'C:/Users/Eslam/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$taskVenv = Join-Path $taskRoot 'build/traycer/a2a-venv'
& $taskPython -m venv $taskVenv
if ($LASTEXITCODE -ne 0) { throw 'A2A isolated venv setup failed' }
$taskVenvPython = Join-Path $taskVenv 'Scripts/python.exe'
& $taskVenvPython -m pip install --disable-pip-version-check -r (Join-Path $PSScriptRoot 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'A2A pinned dependency setup failed' }
& $taskVenvPython (Join-Path $PSScriptRoot 'prove.py') (Join-Path $taskRoot 'build/traycer/a2a-proof')
if ($LASTEXITCODE -ne 0) { throw 'A2A synthetic lifecycle proof failed' }
# prove.py owns its loopback socket in this process and closes it in finally.
# No detached server or pattern-based process cleanup is used.
