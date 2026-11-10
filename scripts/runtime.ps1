$ErrorActionPreference = 'Stop'
function Find-ProjectJdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javacCommand) { $candidates += Split-Path (Split-Path $javacCommand.Source) }
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkRoot) { $candidates += Get-ChildItem -LiteralPath $jdkRoot -Directory | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName }
    foreach ($candidate in $candidates) {
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $releaseFile) -and (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) {
            $versionLine = Get-Content -LiteralPath $releaseFile | Where-Object { $_ -match '^JAVA_VERSION=' }
            if ($versionLine -match '"(\d+)') {
                if ([int]$Matches[1] -ge 17) { return $candidate }
            }
        }
    }
    throw 'JDK 17 or newer was not found. Install a JDK and set JAVA_HOME.'
}
