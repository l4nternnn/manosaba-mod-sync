#define AppName "KuiLunUPDater"
#define AppVersion "1.0.0"
#define Publisher "Manosaba"
#ifndef PublishDir
#define PublishDir "..\build\KuiLunUPDater-publish"
#endif
#define OutputDir "..\build\KuiLunUPDater-installer"
#ifndef SetupFileBaseName
#define SetupFileBaseName "KuiLunUPDater-Setup"
#endif

[Setup]
AppId={{4B2B36BF-0FD1-4E32-AF7B-6F19D67E88D1}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Publisher}
DefaultDirName={localappdata}\Programs\{#AppName}
AppendDefaultDirName=yes
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutputDir}
OutputBaseFilename={#SetupFileBaseName}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupLogging=yes
UninstallDisplayIcon={app}\KuiLunUPDater.exe

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加选项："; Flags: checkedonce

[Files]
Source: "{#PublishDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\KuiLunUPDater.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\KuiLunUPDater.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\KuiLunUPDater.exe"; Description: "启动 {#AppName}"; Flags: nowait postinstall skipifsilent
