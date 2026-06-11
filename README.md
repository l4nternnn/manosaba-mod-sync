# Manosaba Mod Sync

Manosaba Mod Sync 是给 **Minecraft Fabric 1.21.8** 整合包使用的模组一致性工具集。

当前推荐方案不是在游戏进程里边连服务器边下载模组，而是让玩家进服前先运行独立更新器 **KuiLunUPDater**。更新器读取自建 HTTPS 下载站上的 `manifest.json`，按文件名和 `SHA-256` 校验本地 `mods/`，只下载缺失或校验不一致的 jar。Fabric Mod 侧保留游戏内提示入口，不再承担主要下载逻辑。

## 项目组成

- `src/main`：Fabric Mod 通用逻辑、配置、manifest、网络协议与服务端逻辑。
- `src/client`：游戏内轻量提示入口和旧同步界面的兼容代码。
- `updater`：Windows 优先的 WPF 独立更新器，产物名为 `KuiLunUPDater.exe`。
- `installer`：Inno Setup 安装包脚本，常见产物为 `KuiLunUPDater-GitHub-Setup.exe` 和 `KuiLunUPDater-Server-Setup.exe`。
- `tools`：manifest 生成脚本，用于扫描本地 `mods` 并生成下载清单。

## 玩家使用流程

1. 运行对应版本的 `KuiLunUPDater-*-Setup.exe` 安装更新器。
2. 安装包已经包含 .NET Desktop Runtime，不需要玩家额外安装运行时。
3. 打开 `KuiLunUPDater`。
4. 首次启动时选择游戏目录，也就是包含 `mods/` 文件夹的目录，例如：

   ```text
   D:\PCL\.minecraft\versions\YourModpack
   ```

5. 确认 Manifest URL：

   ```text
   https://download.example.com/manosaba/manifest.json
   ```

6. 点击“检查并更新”，等待下载和校验完成。
7. 更新器显示可以启动游戏后，再使用原启动器进入服务器。

更新器配置会保存在：

```text
%APPDATA%\ManosabaUpdater\settings.json
```

## 下载站目录约定

服务器静态站目录按下面的结构放置：

```text
/manosaba/manifest.json
/manosaba/mods/*.jar
```

对应公网地址：

```text
https://download.example.com/manosaba/manifest.json
https://download.example.com/manosaba/mods/xxx.jar
```

注意：更新器会校验 `downloadUrl` 的域名必须和 `manifest.json` 的域名一致，避免 manifest 被改成任意外部下载源。

## Manifest 格式

```json
{
  "packId": "manosaba",
  "packName": "Your Modpack",
  "packVersion": "pack-2026.06.09",
  "mods": [
    {
      "fileName": "ice-phone-1.3.0.jar",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "size": 123456,
      "downloadUrl": "https://download.example.com/manosaba/mods/ice-phone-1.3.0.jar"
    }
  ]
}
```

## 服主发布流程

假设你的本地整合包模组目录是：

```text
D:\PCL\.minecraft\versions\YourModpack\mods
```

1. 把需要下发给玩家的客户端 jar 放在这个 `mods` 目录。
2. 生成 `manifest.json`：

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Generate-ManosabaManifest.ps1 `
     -ModsPath "D:\PCL\.minecraft\versions\YourModpack\mods" `
     -OutputPath "D:\PCL\.minecraft\versions\YourModpack\manifest.json" `
     -BaseUrl "https://download.example.com/manosaba/mods/" `
     -PackId "manosaba" `
     -PackName "Your Modpack"
   ```

3. 上传所有 jar 到服务器：

   ```text
   /www/wwwroot/download.example.com/manosaba/mods/
   ```

4. 最后上传 `manifest.json` 到服务器：

   ```text
   /www/wwwroot/download.example.com/manosaba/manifest.json
   ```

建议最后再上传 `manifest.json`，这样玩家不会读到“清单已经更新但 jar 还没传完”的中间状态。

## 宝塔 Nginx 关键配置

在站点配置里保留证书相关配置，并确保有下面两个 location：

```nginx
location = /manosaba/manifest.json {
    default_type application/json;
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    add_header Pragma "no-cache" always;
    add_header Expires "0" always;
    try_files $uri =404;
}

location ^~ /manosaba/mods/ {
    default_type application/java-archive;
    add_header Cache-Control "public, max-age=300" always;
    try_files $uri =404;
}
```

修改后执行：

```bash
nginx -t
nginx -s reload
```

## 构建

构建 Fabric Mod：

```powershell
.\gradlew.bat build
```

构建 WPF 更新器公共版（不预填 Manifest URL，适合 GitHub 发布）：

```powershell
dotnet publish .\updater\ManosabaUpdater.csproj -p:PublishProfile=GitHub -p:DefaultManifestUrl=""
```

构建 WPF 更新器个人服务器版（预填 Manifest URL，URL 通过命令行传入，不写入仓库）：

```powershell
dotnet publish .\updater\ManosabaUpdater.csproj -p:PublishProfile=PersonalServer -p:DefaultManifestUrl="https://download.example.com/manosaba/manifest.json"
```

如果是维护者本地构建个人服务器版，真实 Manifest URL 记录在本地 `AGENTS.md` 中；该文件已被 `.gitignore` 忽略，不会提交到公开仓库。

分别构建安装包：

```powershell
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DPublishDir="..\build\KuiLunUPDater-GitHub-publish" /DSetupFileBaseName="KuiLunUPDater-GitHub-Setup" .\installer\KuiLunUPDater.iss
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DPublishDir="..\build\KuiLunUPDater-Server-publish" /DSetupFileBaseName="KuiLunUPDater-Server-Setup" .\installer\KuiLunUPDater.iss
```

也可以用脚本一次生成两个版本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Publish-UpdaterVariants.ps1 -PersonalManifestUrl "https://download.example.com/manosaba/manifest.json"
```

GitHub release 只上传 `KuiLunUPDater-GitHub-Setup.exe`。`KuiLunUPDater-Server-Setup.exe` 仅作为个人服务器版本地构建产物，不上传到 GitHub。

常见产物：

```text
build\libs\manosaba_mod_sync-1.0.2.jar
build\KuiLunUPDater-installer\KuiLunUPDater-GitHub-Setup.exe
build\KuiLunUPDater-installer\KuiLunUPDater-Server-Setup.exe
```

## 测试建议

- 空 `mods/` 目录：应下载 manifest 中全部 jar。
- 损坏 jar：改动任意字节后应被重新下载。
- 完整目录：再次检查应显示无需更新。
- 断网或 manifest 地址错误：应显示明确错误，详情可展开查看。
- 更新完成后再启动 Minecraft：不应再出现缺少客户端模组的报错。

## 重要限制

- 第一版只做 Windows 更新器。
- 更新器不是完整启动器，不处理账号、Java、启动参数。
- 已经运行中的 Minecraft 不能热加载新 jar，必须先更新再启动游戏。
- Fabric Mod 不是下载器的替代品，主要负责游戏内提醒。
- 下载源建议使用香港或其他可稳定直连的 HTTPS 静态站。

## License

本项目使用 [MIT License](LICENSE)。
