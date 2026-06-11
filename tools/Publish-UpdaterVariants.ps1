param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https://')]
    [string]$PersonalManifestUrl,

    [string]$InnoSetupCompiler = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$updaterProject = Join-Path $projectRoot "updater\ManosabaUpdater.csproj"
$installerScript = Join-Path $projectRoot "installer\KuiLunUPDater.iss"

if (-not (Test-Path -LiteralPath $InnoSetupCompiler)) {
    throw "Inno Setup compiler not found: $InnoSetupCompiler"
}

dotnet publish $updaterProject -p:PublishProfile=GitHub -p:DefaultManifestUrl=""
if ($LASTEXITCODE -ne 0) {
    throw "GitHub updater publish failed."
}

dotnet publish $updaterProject -p:PublishProfile=PersonalServer -p:DefaultManifestUrl="$PersonalManifestUrl"
if ($LASTEXITCODE -ne 0) {
    throw "Personal server updater publish failed."
}

& $InnoSetupCompiler /DPublishDir="..\build\KuiLunUPDater-GitHub-publish" /DSetupFileBaseName="KuiLunUPDater-GitHub-Setup" $installerScript
if ($LASTEXITCODE -ne 0) {
    throw "GitHub installer compile failed."
}

& $InnoSetupCompiler /DPublishDir="..\build\KuiLunUPDater-Server-publish" /DSetupFileBaseName="KuiLunUPDater-Server-Setup" $installerScript
if ($LASTEXITCODE -ne 0) {
    throw "Personal server installer compile failed."
}

Write-Host "Updater variant installers created:"
Write-Host (Join-Path $projectRoot "build\KuiLunUPDater-installer\KuiLunUPDater-GitHub-Setup.exe")
Write-Host (Join-Path $projectRoot "build\KuiLunUPDater-installer\KuiLunUPDater-Server-Setup.exe")
