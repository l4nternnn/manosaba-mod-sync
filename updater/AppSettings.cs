using System;
using System.IO;
using System.Text.Json;

namespace ManosabaUpdater;

public sealed class AppSettings
{
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public string GameDirectory { get; set; } = "";
    public string ManifestUrl { get; set; } = BuildDefaults.DefaultManifestUrl;

    public static string SettingsDirectory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ManosabaUpdater");

    public static string SettingsPath => Path.Combine(SettingsDirectory, "settings.json");

    public static AppSettings Load()
    {
        return LoadFromFile(SettingsPath);
    }

    public static AppSettings LoadFromFile(string settingsPath)
    {
        try
        {
            if (!File.Exists(settingsPath))
            {
                return new AppSettings();
            }
            return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(settingsPath), JsonOptions)
                   ?? new AppSettings();
        }
        catch
        {
            return new AppSettings();
        }
    }

    public void Save()
    {
        Directory.CreateDirectory(SettingsDirectory);
        File.WriteAllText(SettingsPath, JsonSerializer.Serialize(this, JsonOptions));
    }
}
