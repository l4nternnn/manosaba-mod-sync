# Manosaba Updater

Windows-first desktop updater for the Manosaba modpack.

## Build

Install the .NET 8 SDK, then build the public GitHub variant:

```powershell
dotnet publish .\updater\ManosabaUpdater.csproj -p:PublishProfile=GitHub -p:DefaultManifestUrl=""
```

Build the personal-server variant by passing the default manifest URL at publish time:

```powershell
dotnet publish .\updater\ManosabaUpdater.csproj -p:PublishProfile=PersonalServer -p:DefaultManifestUrl="https://download.example.com/manosaba/manifest.json"
```

For maintainer-local server builds, keep the real manifest URL in the ignored root `AGENTS.md`; do not write it into tracked source files or public release notes.

The self-contained executable and bundled .NET desktop runtime files will be emitted under:

```text
build/KuiLunUPDater-GitHub-publish/
build/KuiLunUPDater-Server-publish/
```

## Runtime Flow

1. Pick the Minecraft game directory that contains the `mods` folder.
2. Confirm the manifest URL.
3. Click `检查并更新`.
4. The updater downloads only missing or SHA-256 mismatched jar files.

The updater writes settings to:

```text
%APPDATA%\ManosabaUpdater\settings.json
```

Downloaded files are written as `.download` first and moved into `mods` only after SHA-256 verification succeeds.

## Manifest

The updater expects:

```json
{
  "packId": "manosaba",
  "packName": "魔法少女的溃论覆辙",
  "packVersion": "pack-2026.06.09",
  "mods": [
    {
      "fileName": "ice-phone-1.3.0.jar",
      "sha256": "...",
      "size": 123456,
      "downloadUrl": "https://download.example.com/manosaba/mods/ice-phone-1.3.0.jar"
    }
  ]
}
```

For the first version, every `downloadUrl` must use HTTPS and the same host as the manifest URL.

## Installer

Install Inno Setup, then run:

```powershell
iscc /DPublishDir="..\build\KuiLunUPDater-GitHub-publish" /DSetupFileBaseName="KuiLunUPDater-GitHub-Setup" .\installer\KuiLunUPDater.iss
iscc /DPublishDir="..\build\KuiLunUPDater-Server-publish" /DSetupFileBaseName="KuiLunUPDater-Server-Setup" .\installer\KuiLunUPDater.iss
```

The setup executable will be emitted to:

```text
build/KuiLunUPDater-installer/KuiLunUPDater-GitHub-Setup.exe
build/KuiLunUPDater-installer/KuiLunUPDater-Server-Setup.exe
```

Upload only `KuiLunUPDater-GitHub-Setup.exe` to GitHub releases. Keep the server installer as a local-only artifact.
