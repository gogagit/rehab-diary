. (Join-Path $PSScriptRoot 'runtime.ps1')
$projectRoot = Split-Path $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$env:JAVA_HOME = Find-ProjectJdk
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { $null }
if (-not $mavenPath) {
    $ideaRoot = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path -LiteralPath $ideaRoot) {
        foreach ($idea in (Get-ChildItem -LiteralPath $ideaRoot -Directory | Sort-Object Name -Descending)) {
            $candidate = Join-Path $idea.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd'
            if (Test-Path -LiteralPath $candidate) { $mavenPath = $candidate; break }
        }
    }
}
if (-not $mavenPath) { throw 'Maven was not found. Install Maven or build pom.xml in IntelliJ IDEA.' }
& $mavenPath -B -ntp clean verify
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Read the Maven output above.' }
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'app') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'target\rehab-diary-1.0.0.jar') -Destination (Join-Path $projectRoot 'app\rehab-diary.jar') -Force
Write-Host 'Build and tests passed. Run start.cmd.'
