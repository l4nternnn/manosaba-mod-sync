param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("manosaba-defaults-test-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot | Out-Null

function New-RuntimeConfig {
    param([string]$Path)

    @"
{
  "runtimeOptions": {
    "tfm": "net8.0",
    "framework": {
      "name": "Microsoft.NETCore.App",
      "version": "8.0.0"
    }
  }
}
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Invoke-Csc {
    param(
        [string]$OutputPath,
        [string[]]$Sources
    )

    $sdkRoot = Split-Path (Get-Command dotnet).Source
    $cscPath = Join-Path $sdkRoot "sdk\8.0.422\Roslyn\bincore\csc.dll"
    $refRoot = Join-Path $sdkRoot "packs\Microsoft.NETCore.App.Ref"
    $refVersion = Get-ChildItem -LiteralPath $refRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1
    $references = Get-ChildItem -LiteralPath (Join-Path $refVersion.FullName "ref\net8.0") -Filter "*.dll" |
        ForEach-Object { "/reference:$($_.FullName)" }

    dotnet $cscPath /noconfig /nostdlib+ /target:exe /out:$OutputPath @references @Sources
    if ($LASTEXITCODE -ne 0) {
        throw "C# compilation failed."
    }
}

function Invoke-Case {
    param(
        [string]$Name,
        [string]$DefaultManifestUrl
    )

    $caseRoot = Join-Path $tempRoot $Name
    New-Item -ItemType Directory -Path $caseRoot | Out-Null

    $programPath = Join-Path $caseRoot "Program.cs"
    $outputPath = Join-Path $caseRoot "$Name.dll"
    $runtimeConfigPath = Join-Path $caseRoot "$Name.runtimeconfig.json"
    $settingsPath = Join-Path $caseRoot "settings.json"

    $escapedUrl = $DefaultManifestUrl.Replace("\", "\\").Replace('"', '\"')
    @"
using System;
using System.IO;
using System.Reflection;
using ManosabaUpdater;

[assembly: AssemblyMetadata("DefaultManifestUrl", "$escapedUrl")]

static void AssertEqual(string expected, string actual, string message)
{
    if (!string.Equals(expected, actual, StringComparison.Ordinal))
    {
        throw new Exception(message + $" Expected '{expected}', got '{actual}'.");
    }
}

AppSettings fresh = AppSettings.LoadFromFile(@"$($settingsPath.Replace("\", "\\"))");
AssertEqual("$escapedUrl", fresh.ManifestUrl, "Fresh settings should use build default.");

File.WriteAllText(@"$($settingsPath.Replace("\", "\\"))", "{\"GameDirectory\":\"C:\\\\Game\",\"ManifestUrl\":\"https://saved.example/manifest.json\"}");

AppSettings saved = AppSettings.LoadFromFile(@"$($settingsPath.Replace("\", "\\"))");
AssertEqual("https://saved.example/manifest.json", saved.ManifestUrl, "Saved settings should win over build default.");

Console.WriteLine("$Name passed.");
"@ | Set-Content -LiteralPath $programPath -Encoding UTF8

    New-RuntimeConfig -Path $runtimeConfigPath
    Invoke-Csc -OutputPath $outputPath -Sources @(
        (Join-Path $ProjectRoot "updater\AppSettings.cs"),
        (Join-Path $ProjectRoot "updater\BuildDefaults.cs"),
        $programPath
    )

    dotnet $outputPath
    if ($LASTEXITCODE -ne 0) {
        throw "$Name executable failed."
    }
}

try {
    Invoke-Case -Name "GithubDefaults" -DefaultManifestUrl ""
    Invoke-Case -Name "ServerDefaults" -DefaultManifestUrl "https://personal.example/manosaba/manifest.json"
    Write-Host "Updater build default tests passed."
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
