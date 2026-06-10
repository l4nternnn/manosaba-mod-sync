using System.Diagnostics;
using System.IO;
using System.Windows;
using Forms = System.Windows.Forms;

namespace ManosabaUpdater;

public partial class MainWindow : Window
{
    private readonly AppSettings _settings = AppSettings.Load();
    private readonly UpdaterService _updater = new();
    private CancellationTokenSource? _cancellation;
    private readonly List<string> _details = [];

    public MainWindow()
    {
        InitializeComponent();
        LoadSettingsIntoUi();
    }

    private void LoadSettingsIntoUi()
    {
        GameDirectoryTextBox.Text = _settings.GameDirectory;
        ManifestUrlTextBox.Text = _settings.ManifestUrl;
    }

    private void ChooseDirectoryButton_Click(object sender, RoutedEventArgs e)
    {
        using var dialog = new Forms.FolderBrowserDialog
        {
            Description = "请选择包含 mods 文件夹的 Minecraft 游戏目录",
            UseDescriptionForTitle = true,
            SelectedPath = Directory.Exists(GameDirectoryTextBox.Text)
                ? GameDirectoryTextBox.Text
                : Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData)
        };

        if (dialog.ShowDialog() == Forms.DialogResult.OK)
        {
            GameDirectoryTextBox.Text = dialog.SelectedPath;
            SaveSettings();
        }
    }

    private void SaveButton_Click(object sender, RoutedEventArgs e)
    {
        SaveSettings();
    }

    private async void CheckAndUpdateButton_Click(object sender, RoutedEventArgs e)
    {
        await CheckAndUpdateAsync();
    }

    private void CancelButton_Click(object sender, RoutedEventArgs e)
    {
        _cancellation?.Cancel();
    }

    private void OpenModsButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            string modsDirectory = UpdaterService.ResolveModsDirectory(GameDirectoryTextBox.Text.Trim());
            Directory.CreateDirectory(modsDirectory);
            Process.Start(new ProcessStartInfo
            {
                FileName = modsDirectory,
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show(this, ex.Message, "无法打开 mods", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void SaveSettings()
    {
        _settings.GameDirectory = GameDirectoryTextBox.Text.Trim();
        _settings.ManifestUrl = ManifestUrlTextBox.Text.Trim();
        _settings.Save();
        SetStatus("配置已保存。");
        AddDetail("配置已保存。");
    }

    private async Task CheckAndUpdateAsync()
    {
        SaveSettings();
        SetBusy(true);
        ResetProgress();
        _details.Clear();
        DetailsTextBox.Text = "";
        DetailsExpander.Visibility = Visibility.Collapsed;
        DetailsExpander.IsExpanded = false;
        _cancellation = new CancellationTokenSource();

        try
        {
            SetStatus("正在读取 manifest...");
            AddDetail("正在读取 manifest...");
            UpdatePlan plan = await _updater.CreatePlanAsync(_settings.GameDirectory, _settings.ManifestUrl, _cancellation.Token);
            PackVersionText.Text = string.IsNullOrWhiteSpace(plan.Manifest.PackVersion) ? "已读取清单" : plan.Manifest.PackVersion;
            SetStatus($"{plan.Manifest.PackName} {plan.Manifest.PackVersion}");
            AddDetail($"Manifest：{plan.Manifest.PackName} {plan.Manifest.PackVersion}");
            AddDetail($"已匹配：{plan.ValidCount}，需要下载：{plan.EntriesToDownload.Count}，额外文件：{plan.ExtraCount}");

            if (plan.IsComplete)
            {
                DownloadProgressBar.Value = 100;
                PercentText.Text = "100%";
                CurrentFileText.Text = "当前文件：-";
                RemainingFilesText.Text = "0";
                DownloadedText.Text = "0 B";
                RemainingSizeText.Text = "0 B";
                SpeedText.Text = "-";
                SetStatus("已是最新，可以启动游戏。");
                return;
            }

            var progress = new Progress<DownloadProgress>(UpdateDownloadProgress);
            SetStatus($"发现 {plan.EntriesToDownload.Count} 个文件需要更新，正在下载...");
            AddDetail("开始下载缺失或不一致的文件...");
            await _updater.DownloadAsync(_settings.GameDirectory, plan, progress, _cancellation.Token);
            DownloadProgressBar.Value = 100;
            PercentText.Text = "100%";
            SetStatus("更新完成，可以启动游戏。");
            AddDetail("更新完成。");
        }
        catch (OperationCanceledException)
        {
            SetStatus("已取消。");
            AddDetail("操作已取消。");
        }
        catch (Exception ex)
        {
            SetStatus("更新失败：" + RootMessage(ex));
            AddDetail(ex.ToString());
            DetailsExpander.Visibility = Visibility.Visible;
        }
        finally
        {
            SetBusy(false);
            _cancellation?.Dispose();
            _cancellation = null;
        }
    }

    private void UpdateDownloadProgress(DownloadProgress progress)
    {
        int percent = progress.TotalBytes <= 0L
            ? 0
            : (int)Math.Clamp(progress.DownloadedBytes * 100L / progress.TotalBytes, 0L, 100L);
        DownloadProgressBar.Value = percent;
        PercentText.Text = percent + "%";
        CurrentFileText.Text = "当前文件：" + progress.FileName;
        RemainingFilesText.Text = progress.RemainingFiles.ToString();
        DownloadedText.Text = FormatBytes(progress.DownloadedBytes);
        RemainingSizeText.Text = FormatBytes(progress.RemainingBytes);
        SpeedText.Text = FormatBytes(progress.BytesPerSecond) + "/s";
    }

    private void ResetProgress()
    {
        DownloadProgressBar.Value = 0;
        PercentText.Text = "0%";
        CurrentFileText.Text = "当前文件：-";
        RemainingFilesText.Text = "-";
        DownloadedText.Text = "-";
        RemainingSizeText.Text = "-";
        SpeedText.Text = "-";
    }

    private void SetBusy(bool busy)
    {
        CheckAndUpdateButton.IsEnabled = !busy;
        ChooseDirectoryButton.IsEnabled = !busy;
        SaveButton.IsEnabled = !busy;
        ManifestUrlTextBox.IsReadOnly = busy;
        GameDirectoryTextBox.IsReadOnly = busy;
        CancelButton.IsEnabled = busy;
        Cursor = busy ? System.Windows.Input.Cursors.Wait : System.Windows.Input.Cursors.Arrow;
    }

    private void SetStatus(string message)
    {
        StatusText.Text = message;
    }

    private void AddDetail(string message)
    {
        _details.Add($"[{DateTime.Now:HH:mm:ss}] {message}");
        DetailsTextBox.Text = string.Join(Environment.NewLine, _details);
    }

    private static string RootMessage(Exception exception)
    {
        Exception current = exception;
        while (current.InnerException != null)
        {
            current = current.InnerException;
        }
        return current.Message;
    }

    private static string FormatBytes(long bytes)
    {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return $"{kib:F1} KiB";
        double mib = kib / 1024.0;
        if (mib < 1024.0) return $"{mib:F1} MiB";
        return $"{mib / 1024.0:F1} GiB";
    }
}
