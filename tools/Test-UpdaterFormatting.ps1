param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("manosaba-format-test-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot | Out-Null

try {
    $programPath = Join-Path $tempRoot "Program.cs"
    $outputPath = Join-Path $tempRoot "FormattingTest.dll"
    $runtimeConfigPath = Join-Path $tempRoot "FormattingTest.runtimeconfig.json"
    $formattingPath = Join-Path $ProjectRoot "updater\UpdaterFormatting.cs"

    @"
using System;
using ManosabaUpdater;

static void AssertEqual(string expected, string actual)
{
    if (!string.Equals(expected, actual, StringComparison.Ordinal))
    {
        throw new Exception($"Expected '{expected}', got '{actual}'.");
    }
}

AssertEqual("0.0 KB", UpdaterFormatting.FormatKilobytes(0));
AssertEqual("0.5 KB", UpdaterFormatting.FormatKilobytes(512));
AssertEqual("1.0 KB", UpdaterFormatting.FormatKilobytes(1024));
AssertEqual("1536.0 KB", UpdaterFormatting.FormatKilobytes(1572864));

Console.WriteLine("UpdaterFormatting tests passed.");
"@ | Set-Content -LiteralPath $programPath -Encoding UTF8

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
"@ | Set-Content -LiteralPath $runtimeConfigPath -Encoding UTF8

    $sdkRoot = Split-Path (Get-Command dotnet).Source
    $cscPath = Join-Path $sdkRoot "sdk\8.0.422\Roslyn\bincore\csc.dll"
    $refRoot = Join-Path $sdkRoot "packs\Microsoft.NETCore.App.Ref"
    $refVersion = Get-ChildItem -LiteralPath $refRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1
    $references = Get-ChildItem -LiteralPath (Join-Path $refVersion.FullName "ref\net8.0") -Filter "*.dll" |
        ForEach-Object { "/reference:$($_.FullName)" }

    dotnet $cscPath /noconfig /nostdlib+ /target:exe /out:$outputPath @references $formattingPath $programPath
    if ($LASTEXITCODE -ne 0) {
        throw "C# compilation failed."
    }

    dotnet $outputPath
    if ($LASTEXITCODE -ne 0) {
        throw "UpdaterFormatting test executable failed."
    }
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
