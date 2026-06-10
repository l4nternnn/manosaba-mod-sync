using System.Text.Json.Serialization;

namespace ManosabaUpdater;

public sealed class SyncManifest
{
    [JsonPropertyName("packId")]
    public string PackId { get; set; } = "";

    [JsonPropertyName("packName")]
    public string PackName { get; set; } = "";

    [JsonPropertyName("packVersion")]
    public string PackVersion { get; set; } = "";

    [JsonPropertyName("mods")]
    public List<SyncModEntry> Mods { get; set; } = [];
}

public sealed class SyncModEntry
{
    [JsonPropertyName("fileName")]
    public string FileName { get; set; } = "";

    [JsonPropertyName("sha256")]
    public string Sha256 { get; set; } = "";

    [JsonPropertyName("size")]
    public long Size { get; set; }

    [JsonPropertyName("downloadUrl")]
    public string DownloadUrl { get; set; } = "";
}

public sealed class UpdatePlan
{
    public required SyncManifest Manifest { get; init; }
    public required List<SyncModEntry> EntriesToDownload { get; init; }
    public required int ValidCount { get; init; }
    public required int ExtraCount { get; init; }

    public long TotalDownloadSize => EntriesToDownload.Sum(entry => Math.Max(0L, entry.Size));
    public bool IsComplete => EntriesToDownload.Count == 0;
}

public sealed class DownloadProgress
{
    public required string FileName { get; init; }
    public required int CompletedFiles { get; init; }
    public required int TotalFiles { get; init; }
    public required long DownloadedBytes { get; init; }
    public required long TotalBytes { get; init; }
    public required long BytesPerSecond { get; init; }

    public int RemainingFiles => Math.Max(0, TotalFiles - CompletedFiles);
    public long RemainingBytes => Math.Max(0L, TotalBytes - DownloadedBytes);
}
