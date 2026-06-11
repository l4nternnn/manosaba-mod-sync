param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$githubProfilePath = Join-Path $ProjectRoot "updater\Properties\PublishProfiles\GitHub.pubxml"
$serverProfilePath = Join-Path $ProjectRoot "updater\Properties\PublishProfiles\PersonalServer.pubxml"
$projectPath = Join-Path $ProjectRoot "updater\ManosabaUpdater.csproj"
$installerPath = Join-Path $ProjectRoot "installer\KuiLunUPDater.iss"
$publishScriptPath = Join-Path $ProjectRoot "tools\Publish-UpdaterVariants.ps1"

foreach ($path in @($githubProfilePath, $serverProfilePath, $publishScriptPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing release variant file: $path"
    }
}

[xml]$githubProfile = Get-Content -LiteralPath $githubProfilePath
[xml]$serverProfile = Get-Content -LiteralPath $serverProfilePath

if ($githubProfile.Project.PropertyGroup.PublishDir -ne "..\build\KuiLunUPDater-GitHub-publish\") {
    throw "GitHub publish profile must output to build\KuiLunUPDater-GitHub-publish."
}

if ($githubProfile.Project.PropertyGroup.DefaultManifestUrl -ne "") {
    throw "GitHub publish profile must leave DefaultManifestUrl empty."
}

if ($serverProfile.Project.PropertyGroup.PublishDir -ne "..\build\KuiLunUPDater-Server-publish\") {
    throw "PersonalServer publish profile must output to build\KuiLunUPDater-Server-publish."
}

$project = Get-Content -LiteralPath $projectPath -Raw
if ($project -notmatch "PersonalServer publish requires") {
    throw "Project must reject PersonalServer publish without DefaultManifestUrl."
}

$installer = Get-Content -LiteralPath $installerPath -Raw
if ($installer -notmatch "#ifndef PublishDir" -or $installer -notmatch "#ifndef SetupFileBaseName") {
    throw "Installer script must allow PublishDir and SetupFileBaseName overrides."
}

$publishScript = Get-Content -LiteralPath $publishScriptPath -Raw
if ($publishScript -notmatch "KuiLunUPDater-GitHub-Setup" -or $publishScript -notmatch "KuiLunUPDater-Server-Setup") {
    throw "Publish script must produce distinct GitHub and server setup names."
}

Write-Host "Updater release variant tests passed."
