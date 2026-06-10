using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text.Json;

namespace ManosabaUpdater;

public sealed class UpdaterService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly HttpClient _httpClient = new()
    {
        Timeout = TimeSpan.FromSeconds(30)
    };

    public UpdaterService()
    {
        _httpClient.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("ManosabaUpdater", "1.0"));
    }

    public async Task<UpdatePlan> CreatePlanAsync(string gameDirectory, string manifestUrl, CancellationToken cancellationToken)
    {
        Uri manifestUri = ValidateManifestUri(manifestUrl);
        SyncManifest manifest = await FetchManifestAsync(manifestUri, cancellationToken);
        string modsDirectory = ResolveModsDirectory(gameDirectory);
        Directory.CreateDirectory(modsDirectory);

        Dictionary<string, string> localHashes = await HashLocalModsAsync(modsDirectory, cancellationToken);
        List<SyncModEntry> downloads = [];
        int valid = 0;

        foreach (SyncModEntry entry in manifest.Mods)
        {
            ValidateEntry(entry, manifestUri.Host);
            cancellationToken.ThrowIfCancellationRequested();
            if (localHashes.TryGetValue(entry.FileName, out string? localHash)
                && string.Equals(localHash, entry.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                valid++;
                continue;
            }
            downloads.Add(entry);
        }

        HashSet<string> expectedNames = manifest.Mods.Select(entry => entry.FileName).ToHashSet(StringComparer.OrdinalIgnoreCase);
        int extra = localHashes.Keys.Count(name => !expectedNames.Contains(name));
        return new UpdatePlan
        {
            Manifest = manifest,
            EntriesToDownload = downloads,
            ValidCount = valid,
            ExtraCount = extra
        };
    }

    public async Task DownloadAsync(
        string gameDirectory,
        UpdatePlan plan,
        IProgress<DownloadProgress> progress,
        CancellationToken cancellationToken)
    {
        string modsDirectory = ResolveModsDirectory(gameDirectory);
        Directory.CreateDirectory(modsDirectory);
        string tempDirectory = Path.Combine(modsDirectory, ".manosaba-updater", "downloads");
        string backupDirectory = Path.Combine(modsDirectory, ".manosaba-updater", "backup", DateTime.Now.ToString("yyyyMMdd-HHmmss"));
        Directory.CreateDirectory(tempDirectory);

        long totalBytes = plan.TotalDownloadSize;
        long downloadedBytes = 0L;
        int completedFiles = 0;
        Stopwatch stopwatch = Stopwatch.StartNew();

        foreach (SyncModEntry entry in plan.EntriesToDownload)
        {
            cancellationToken.ThrowIfCancellationRequested();
            string targetPath = SafePath(modsDirectory, entry.FileName);
            string tempPath = SafePath(tempDirectory, entry.FileName + ".download");

            progress.Report(CreateProgress(entry.FileName, completedFiles, plan.EntriesToDownload.Count, downloadedBytes, totalBytes, stopwatch));
            if (File.Exists(tempPath))
            {
                File.Delete(tempPath);
            }

            await DownloadFileAsync(entry, tempPath, bytes =>
            {
                downloadedBytes += bytes;
                progress.Report(CreateProgress(entry.FileName, completedFiles, plan.EntriesToDownload.Count, downloadedBytes, totalBytes, stopwatch));
            }, cancellationToken);

            string actualHash = await Sha256FileAsync(tempPath, cancellationToken);
            if (!string.Equals(actualHash, entry.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(tempPath);
                throw new InvalidOperationException($"SHA-256 校验失败：{entry.FileName}");
            }

            if (File.Exists(targetPath))
            {
                Directory.CreateDirectory(backupDirectory);
                string backupPath = SafePath(backupDirectory, entry.FileName);
                File.Move(targetPath, backupPath, overwrite: true);
            }
            File.Move(tempPath, targetPath, overwrite: true);

            completedFiles++;
            progress.Report(CreateProgress(entry.FileName, completedFiles, plan.EntriesToDownload.Count, downloadedBytes, totalBytes, stopwatch));
        }
    }

    public static string ResolveModsDirectory(string gameDirectory)
    {
        if (string.IsNullOrWhiteSpace(gameDirectory))
        {
            throw new InvalidOperationException("请先选择游戏目录。");
        }
        return Path.Combine(gameDirectory, "mods");
    }

    private async Task<SyncManifest> FetchManifestAsync(Uri manifestUri, CancellationToken cancellationToken)
    {
        using HttpResponseMessage response = await _httpClient.GetAsync(manifestUri, cancellationToken);
        response.EnsureSuccessStatusCode();
        await using Stream stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        SyncManifest? manifest = await JsonSerializer.DeserializeAsync<SyncManifest>(stream, JsonOptions, cancellationToken);
        if (manifest == null)
        {
            throw new InvalidOperationException("manifest.json 内容为空。");
        }
        if (manifest.Mods.Count == 0)
        {
            throw new InvalidOperationException("manifest.json 没有包含任何 mod。");
        }
        return manifest;
    }

    private static Uri ValidateManifestUri(string manifestUrl)
    {
        if (!Uri.TryCreate(manifestUrl, UriKind.Absolute, out Uri? uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException("manifestUrl 必须是 HTTPS 地址。");
        }
        return uri;
    }

    private static void ValidateEntry(SyncModEntry entry, string manifestHost)
    {
        if (string.IsNullOrWhiteSpace(entry.FileName) || entry.FileName.Contains('/') || entry.FileName.Contains('\\'))
        {
            throw new InvalidOperationException($"manifest 含有不安全文件名：{entry.FileName}");
        }
        if (string.IsNullOrWhiteSpace(entry.Sha256) || entry.Sha256.Length != 64)
        {
            throw new InvalidOperationException($"manifest 中 {entry.FileName} 的 SHA-256 无效。");
        }
        if (!Uri.TryCreate(entry.DownloadUrl, UriKind.Absolute, out Uri? uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException($"{entry.FileName} 的下载地址必须是 HTTPS。");
        }
        if (!string.Equals(uri.Host, manifestHost, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException($"{entry.FileName} 的下载域名必须与 manifest 域名一致。");
        }
    }

    private async Task<Dictionary<string, string>> HashLocalModsAsync(string modsDirectory, CancellationToken cancellationToken)
    {
        Dictionary<string, string> hashes = new(StringComparer.OrdinalIgnoreCase);
        foreach (string file in Directory.EnumerateFiles(modsDirectory, "*.jar", SearchOption.TopDirectoryOnly))
        {
            cancellationToken.ThrowIfCancellationRequested();
            hashes[Path.GetFileName(file)] = await Sha256FileAsync(file, cancellationToken);
        }
        return hashes;
    }

    private async Task DownloadFileAsync(
        SyncModEntry entry,
        string tempPath,
        Action<int> reportBytes,
        CancellationToken cancellationToken)
    {
        using HttpResponseMessage response = await _httpClient.GetAsync(
            entry.DownloadUrl,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
        response.EnsureSuccessStatusCode();

        await using Stream input = await response.Content.ReadAsStreamAsync(cancellationToken);
        await using FileStream output = File.Create(tempPath);
        byte[] buffer = new byte[64 * 1024];
        while (true)
        {
            int read = await input.ReadAsync(buffer.AsMemory(0, buffer.Length), cancellationToken);
            if (read == 0)
            {
                break;
            }
            await output.WriteAsync(buffer.AsMemory(0, read), cancellationToken);
            reportBytes(read);
        }
    }

    private static DownloadProgress CreateProgress(
        string fileName,
        int completedFiles,
        int totalFiles,
        long downloadedBytes,
        long totalBytes,
        Stopwatch stopwatch)
    {
        double seconds = Math.Max(0.001, stopwatch.Elapsed.TotalSeconds);
        return new DownloadProgress
        {
            FileName = fileName,
            CompletedFiles = completedFiles,
            TotalFiles = totalFiles,
            DownloadedBytes = downloadedBytes,
            TotalBytes = totalBytes,
            BytesPerSecond = (long)(downloadedBytes / seconds)
        };
    }

    private static async Task<string> Sha256FileAsync(string path, CancellationToken cancellationToken)
    {
        await using FileStream stream = File.OpenRead(path);
        byte[] hash = await SHA256.HashDataAsync(stream, cancellationToken);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    private static string SafePath(string directory, string fileName)
    {
        string root = Path.GetFullPath(directory);
        string target = Path.GetFullPath(Path.Combine(root, fileName));
        if (!target.StartsWith(root + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException($"文件路径越界：{fileName}");
        }
        return target;
    }
}
