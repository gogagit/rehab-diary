. (Join-Path $PSScriptRoot 'runtime.ps1')
$projectRoot = Split-Path $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$jdkPath = Find-ProjectJdk
$javaPath = Join-Path $jdkPath 'bin\java.exe'
$jarPath = Join-Path $projectRoot 'app\rehab-diary.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    $jarPath = Join-Path $projectRoot 'target\rehab-diary-1.0.0.jar'
}
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Application JAR is missing. Run build.cmd first.' }
$portValue = if ($env:PORT) { $env:PORT } else { '8080' }
Write-Host "Open http://127.0.0.1:$portValue after the server starts. Press Ctrl+C to stop."
# Short ASCII directory avoids the JDK 23 Windows socket-pipe issue with long Unicode TEMP paths.
$socketTemp = Join-Path $env:WINDIR 'Temp'
& $javaPath "-Djdk.net.unixdomain.tmpdir=$socketTemp" -jar $jarPath
exit $LASTEXITCODE
