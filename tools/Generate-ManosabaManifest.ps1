param(
    [string]$ModsPath = ".\mods",
    [string]$OutputPath = ".\manifest.json",
    [string]$BaseUrl = "https://download.example.com/manosaba/mods/",
    [string]$PackId = "manosaba",
    [string]$PackName = "Manosaba",
    [string]$PackVersion = "",
    [string[]]$IgnoreFileNames = @()
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PackVersion)) {
    $PackVersion = "pack-" + (Get-Date -Format "yyyy.MM.dd-HHmm")
}

$defaultIgnoreFileNames = @(
    "manosaba-sync-manifest.json",
    "manifest.json",
    "fabric-loader-0.18.4.jar",
    "jline4mcdsrv-0.7.0.jar",
    "LuckPerms-Fabric-5.5.10.jar",
    "servercore-fabric-1.5.14+1.21.8.jar",
    "spark-1.10.142-fabric.jar",
    "lithium-fabric-0.18.1+mc1.21.8.jar",
    "vanilla-permissions-0.3.4+1.21.8.jar"
)

if (-not (Test-Path -LiteralPath $ModsPath -PathType Container)) {
    throw "ModsPath does not exist: $ModsPath"
}

if (-not $BaseUrl.EndsWith("/")) {
    $BaseUrl = $BaseUrl + "/"
}

$ignore = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($name in ($defaultIgnoreFileNames + $IgnoreFileNames)) {
    [void]$ignore.Add($name)
}

$mods = Get-ChildItem -LiteralPath $ModsPath -Filter "*.jar" -File |
    Where-Object { -not $ignore.Contains($_.Name) } |
    Sort-Object Name |
    ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        [ordered]@{
            fileName = $_.Name
            sha256 = $hash.Hash.ToLowerInvariant()
            size = $_.Length
            downloadUrl = $BaseUrl + [System.Uri]::EscapeDataString($_.Name)
        }
    }

$manifest = [ordered]@{
    packId = $PackId
    packName = $PackName
    packVersion = $PackVersion
    mods = @($mods)
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host "Generated manifest: $OutputPath"
Write-Host "Included mods: $($mods.Count)"
