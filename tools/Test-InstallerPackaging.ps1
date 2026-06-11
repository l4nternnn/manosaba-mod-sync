param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$csprojPath = Join-Path $ProjectRoot "updater\ManosabaUpdater.csproj"
$installerPath = Join-Path $ProjectRoot "installer\KuiLunUPDater.iss"

[xml]$project = Get-Content -LiteralPath $csprojPath
$properties = $project.Project.PropertyGroup

if ($properties.RuntimeIdentifier -ne "win-x64") {
    throw "ManosabaUpdater.csproj must publish for win-x64."
}

if ($properties.SelfContained -ne "true") {
    throw "ManosabaUpdater.csproj must publish self-contained."
}

if ($properties.PublishSelfContained -ne "true") {
    throw "ManosabaUpdater.csproj must explicitly publish self-contained."
}

$installer = Get-Content -LiteralPath $installerPath -Raw

if ($installer -match "IsDotNet8DesktopRuntimeInstalled|dotnet.microsoft.com/download/dotnet/8.0|InitializeSetup") {
    throw "Installer must not block on or open an external .NET Desktop Runtime download."
}

if ($installer -notmatch '\{#PublishDir\}\\\*') {
    throw "Installer must include every file from the self-contained publish directory."
}

if ($installer -notmatch "SetupFileBaseName") {
    throw "Installer must allow variant-specific setup file names."
}

Write-Host "Installer packaging tests passed."
