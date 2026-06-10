#define AppName "KuiLunUPDater"
#define AppVersion "1.0.0"
#define Publisher "Manosaba"
#define PublishDir "..\build\KuiLunUPDater-publish"
#define OutputDir "..\build\KuiLunUPDater-installer"

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
OutputBaseFilename=KuiLunUPDater-Setup
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

[Code]
function IsDotNet8DesktopRuntimeInstalled(): Boolean;
var
  FindRec: TFindRec;
begin
  Result := False;
  if FindFirst(ExpandConstant('{commonpf}\dotnet\shared\Microsoft.WindowsDesktop.App\8.*'), FindRec) then
  begin
    try
      repeat
        if FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY <> 0 then
        begin
          Result := True;
          Exit;
        end;
      until not FindNext(FindRec);
    finally
      FindClose(FindRec);
    end;
  end;
end;

function InitializeSetup(): Boolean;
var
  ErrorCode: Integer;
begin
  Result := True;
  if not IsDotNet8DesktopRuntimeInstalled() then
  begin
    MsgBox(
      '未检测到 .NET 8 Desktop Runtime。请先安装运行时，然后重新运行安装器。' + #13#10#13#10 +
      '打开下载页后，请下载“.NET 桌面运行时 8.0.x”的 Windows x64 安装程序。' + #13#10 +
      '不要下载 SDK、ASP.NET Core 运行时或普通“.NET 运行时”。' + #13#10#13#10 +
      '即将打开 Microsoft .NET 8 下载页面。',
      mbInformation,
      MB_OK
    );
    ShellExec('open', 'https://dotnet.microsoft.com/download/dotnet/8.0', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
    Result := False;
  end;
end;
