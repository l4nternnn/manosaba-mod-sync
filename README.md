# Manosaba Mod Sync

Manosaba Mod Sync 是给 **Minecraft Fabric 1.21.8** 整合包使用的模组一致性工具集，用来保证玩家客户端的 `mods/` 目录与服主发布的版本完全一致。

## 设计理念

早期方案是在游戏进程里「边连服务器边下载模组」，但这种方式受限于游戏运行时无法热加载新 jar、网络异常难以恢复、与启动器流程割裂等问题。

当前推荐方案改为：**玩家进服前先运行独立更新器 KuiLunUPDater**。

- 更新器从自建 HTTPS 下载站读取 `manifest.json`。
- 按文件名和 `SHA-256` 校验本地 `mods/` 目录。
- 只下载缺失或校验不一致的 jar，已存在且一致的文件直接跳过。
- Fabric Mod 侧保留游戏内提示入口，不再承担主要下载逻辑。

整体数据流：

```text
服主本地 mods/  ──扫描+SHA256──▶  manifest.json  ──上传──▶  HTTPS 下载站
                                                                  │
玩家 mods/  ◀──仅下载缺失/不一致──  KuiLunUPDater  ◀──读取清单──┘
```

## 项目组成

| 目录 | 技术栈 | 职责 |
| --- | --- | --- |
| `src/main` | Java 21 / Fabric | Mod 通用逻辑、配置、manifest 模型、网络协议与服务端逻辑 |
| `src/client` | Java 21 / Fabric + ModernUI | 游戏内轻量提示入口和旧同步界面的兼容代码 |
| `updater` | C# / WPF / .NET 8 | Windows 优先的独立更新器，产物名 `KuiLunUPDater.exe` |
| `installer` | Inno Setup | 安装包脚本，产物 `KuiLunUPDater-GitHub-Setup.exe` 等 |
| `tools` | PowerShell | manifest 生成与构建/打包辅助脚本 |

> 说明：游戏内 Mod 与独立更新器版本号相互独立。Mod 版本由 `gradle.properties` 的 `mod_version` 控制，更新器版本由 `installer/KuiLunUPDater.iss` 的 `AppVersion` 控制。

## 玩家使用流程

1. 运行 `KuiLunUPDater-*-Setup.exe` 安装更新器。安装包已内置 .NET Desktop Runtime，无需额外安装运行时。
2. 打开 `KuiLunUPDater`。
3. 首次启动时选择游戏目录，也就是包含 `mods/` 文件夹的目录，例如：

   ```text
   D:\PCL\.minecraft\versions\YourModpack
   ```

4. 确认 Manifest URL（个人服务器版安装包已预填，公共版需手动填写）：

   ```text
   https://download.example.com/manosaba/manifest.json
   ```

5. 点击「检查并更新」，等待下载和校验完成。
6. 更新器显示可以启动游戏后，再用原启动器进入服务器。

### 更新器行为细节

- 更新器会先把文件下载为 `.download` 临时文件，**SHA-256 校验通过后**才移动进 `mods/`，避免半截文件污染目录。
- 替换旧文件前，原文件会被移动到带时间戳的备份目录：

  ```text
  mods\.manosaba-updater\backup\yyyyMMdd-HHmmss\
  ```

- 每次「检查并更新」都会按 mod id 去重：同一 mod id 只保留 manifest 列出的那份（按文件名），其余旧版本/重复（如带中文前缀的副本）移至同一 backup 目录。玩家自装 mod（id 不在 manifest 中）不会动。

- 安全校验（任一不满足都会拒绝该条目）：
  - `manifestUrl` 必须是 HTTPS。
  - 每个 `downloadUrl` 必须是 HTTPS，且**域名必须与 manifest 的域名一致**，防止 manifest 被改成任意外部下载源。
  - `fileName` 不允许包含路径分隔符，避免越界写入 `mods/` 之外。
  - `sha256` 必须是 64 位十六进制。

更新器配置保存在：

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

## Manifest 格式

更新器读取的字段如下（多余字段会被忽略）：

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

| 字段 | 含义 |
| --- | --- |
| `packId` | 整合包标识，仅作展示与区分 |
| `packName` | 整合包显示名称 |
| `packVersion` | 版本标签，建议带日期，便于辨认 |
| `mods[].fileName` | jar 文件名，更新器据此匹配本地文件 |
| `mods[].sha256` | 64 位小写十六进制校验值 |
| `mods[].size` | 文件字节数，用于进度展示 |
| `mods[].downloadUrl` | HTTPS 下载地址，域名须与 manifest 一致 |

## 服主发布流程

假设本地整合包模组目录是：

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

   脚本默认会跳过纯服务端 mod 和清单文件本身（可用 `-IgnoreFileNames` 追加）。
3. 上传所有 jar 到服务器 `/www/wwwroot/download.example.com/manosaba/mods/`。
4. **最后**上传 `manifest.json`。

> 顺序很重要：先传 jar 再传 manifest，玩家就不会读到「清单已更新但 jar 还没传完」的中间状态。

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

`manifest.json` 设为不缓存，保证玩家总能拿到最新清单；jar 允许短时间缓存，减轻回源压力。修改后执行：

```bash
nginx -t
nginx -s reload
```

## 构建

### Fabric Mod

```powershell
.\gradlew.bat build
```

运行核心逻辑回归测试（无依赖的自定义 runner，`check` 会自动带上）：

```powershell
.\gradlew.bat coreLogicTest
```

### 更新器

更新器有两个发布变体，唯一区别是是否预填 Manifest URL：

公共版（不预填，适合 GitHub 发布）：

```powershell
dotnet publish .\updater\ManosabaUpdater.csproj -p:PublishProfile=GitHub -p:DefaultManifestUrl=""
```

个人服务器版（预填，URL 通过命令行传入，不写入仓库）：

```powershell
dotnet publish .\updater\ManosabaUpdater.csproj -p:PublishProfile=PersonalServer -p:DefaultManifestUrl="https://download.example.com/manosaba/manifest.json"
```

> 维护者本地构建个人服务器版时，真实 Manifest URL 记录在本地 `AGENTS.md`；该文件已被 `.gitignore` 忽略，不会进入公开仓库。

产物输出目录：

```text
build\KuiLunUPDater-GitHub-publish\
build\KuiLunUPDater-Server-publish\
```

### 安装包

分别构建两个安装包：

```powershell
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DPublishDir="..\build\KuiLunUPDater-GitHub-publish" /DSetupFileBaseName="KuiLunUPDater-GitHub-Setup" .\installer\KuiLunUPDater.iss
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DPublishDir="..\build\KuiLunUPDater-Server-publish" /DSetupFileBaseName="KuiLunUPDater-Server-Setup" .\installer\KuiLunUPDater.iss
```

也可以用脚本一次生成两个版本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Publish-UpdaterVariants.ps1 -PersonalManifestUrl "https://download.example.com/manosaba/manifest.json"
```

常见产物：

```text
build\libs\manosaba_mod_sync-1.0.2.jar
build\KuiLunUPDater-installer\KuiLunUPDater-GitHub-Setup.exe
build\KuiLunUPDater-installer\KuiLunUPDater-Server-Setup.exe
```

### 发布纪律

- GitHub release **只上传**公共版安装包 `KuiLunUPDater-GitHub-Setup.exe`。
- 个人服务器版安装包仅作为本地构建产物，**不上传** GitHub（避免泄漏个人下载站域名）。

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
