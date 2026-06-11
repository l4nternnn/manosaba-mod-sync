using System.Diagnostics;
using System.ComponentModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using ManosabaUpdater.UI;
using Forms = System.Windows.Forms;

namespace ManosabaUpdater;

public partial class MainWindow : Window
{
    private readonly AppSettings _settings = AppSettings.Load();
    private readonly UpdaterService _updater = new();
    private CancellationTokenSource? _cancellation;
    private CancellationTokenSource? _toastCancellation;
    private readonly List<string> _details = [];
    private FrameworkElement? _currentPage;
    private bool _allowClose;

    public MainWindow()
    {
        InitializeComponent();
        LoadSettingsIntoUi();
        _currentPage = OverviewPage;
        NavOverviewButton.IsChecked = true;
        ThemeToggleButton.IsChecked = ThemeManager.CurrentTheme == AppTheme.Light;
        UpdateCaptionButton();
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

    private void SaveSettings(bool showNotification = true)
    {
        _settings.GameDirectory = GameDirectoryTextBox.Text.Trim();
        _settings.ManifestUrl = ManifestUrlTextBox.Text.Trim();
        _settings.Save();
        SetStatus("配置已保存。");
        AddDetail("配置已保存。");
        if (showNotification)
        {
            ShowToast("配置已保存", "游戏目录和 manifest 地址已经写入本地设置。");
        }
    }

    private async Task CheckAndUpdateAsync()
    {
        SaveSettings(showNotification: false);
        SetBusy(true);
        ResetProgress();
        DownloadProgressBar.IsIndeterminate = true;
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
            DownloadProgressBar.IsIndeterminate = false;
            PackVersionText.Text = string.IsNullOrWhiteSpace(plan.Manifest.PackVersion) ? "已读取清单" : plan.Manifest.PackVersion;
            SetStatus($"{plan.Manifest.PackName} {plan.Manifest.PackVersion}");
            AddDetail($"Manifest：{plan.Manifest.PackName} {plan.Manifest.PackVersion}");
            AddDetail($"已匹配：{plan.ValidCount}，需要下载：{plan.EntriesToDownload.Count}，额外文件：{plan.ExtraCount}");

            if (plan.IsComplete)
            {
                int cleaned = await Task.Run(
                    () => _updater.CleanDuplicateMods(_settings.GameDirectory, plan.Manifest, _cancellation.Token),
                    _cancellation.Token);
                DownloadProgressBar.Value = 100;
                PercentText.Text = "100%";
                CurrentFileText.Text = "当前文件：-";
                RemainingFilesText.Text = "0";
                DownloadedText.Text = UpdaterFormatting.FormatKilobytes(0);
                RemainingSizeText.Text = UpdaterFormatting.FormatKilobytes(0);
                SpeedText.Text = "-";
                if (cleaned > 0)
                {
                    AddDetail($"已清理 {cleaned} 个重复/旧版本 mod（移至 backup）。");
                }
                SetStatus("已是最新，可以启动游戏。");
                ShowToast("无需更新", cleaned > 0
                    ? $"本地 mods 已和 manifest 一致，并清理了 {cleaned} 个重复 mod。"
                    : "本地 mods 已经和 manifest 保持一致。");
                return;
            }

            var progress = new Progress<DownloadProgress>(UpdateDownloadProgress);
            SetStatus($"发现 {plan.EntriesToDownload.Count} 个文件需要更新，正在下载...");
            AddDetail("开始下载缺失或不一致的文件...");
            await _updater.DownloadAsync(_settings.GameDirectory, plan, progress, _cancellation.Token);
            int removed = await Task.Run(
                () => _updater.CleanDuplicateMods(_settings.GameDirectory, plan.Manifest, _cancellation.Token),
                _cancellation.Token);
            if (removed > 0)
            {
                AddDetail($"已清理 {removed} 个重复/旧版本 mod（移至 backup）。");
            }
            DownloadProgressBar.Value = 100;
            PercentText.Text = "100%";
            SetStatus("更新完成，可以启动游戏。");
            AddDetail("更新完成。");
            ShowToast("更新完成", "缺失或不一致的 jar 已经下载并校验。");
        }
        catch (OperationCanceledException)
        {
            SetStatus("已取消。");
            AddDetail("操作已取消。");
            ShowToast("已取消", "本次检查或下载操作已停止。");
        }
        catch (Exception ex)
        {
            SetStatus("更新失败：" + RootMessage(ex));
            AddDetail(ex.ToString());
            DetailsExpander.Visibility = Visibility.Visible;
            DetailsExpander.IsExpanded = true;
            NavDetailsButton.IsChecked = true;
            ShowToast("更新失败", RootMessage(ex), isError: true);
        }
        finally
        {
            DownloadProgressBar.IsIndeterminate = false;
            SetBusy(false);
            _cancellation?.Dispose();
            _cancellation = null;
        }
    }

    private void UpdateDownloadProgress(DownloadProgress progress)
    {
        DownloadProgressBar.IsIndeterminate = false;
        int percent = progress.TotalBytes <= 0L
            ? 0
            : (int)Math.Clamp(progress.DownloadedBytes * 100L / progress.TotalBytes, 0L, 100L);
        DownloadProgressBar.Value = percent;
        PercentText.Text = percent + "%";
        CurrentFileText.Text = "当前文件：" + progress.FileName;
        RemainingFilesText.Text = progress.RemainingFiles.ToString();
        DownloadedText.Text = UpdaterFormatting.FormatKilobytes(progress.DownloadedBytes);
        RemainingSizeText.Text = UpdaterFormatting.FormatKilobytes(progress.RemainingBytes);
        SpeedText.Text = UpdaterFormatting.FormatKilobytes(progress.BytesPerSecond) + "/s";
    }

    private void ResetProgress()
    {
        DownloadProgressBar.IsIndeterminate = false;
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
        SetBusyVisual(busy);
    }

    private void SetStatus(string message)
    {
        StatusText.Text = message;
        StatusText.BeginAnimation(OpacityProperty, new DoubleAnimation
        {
            From = 0.45,
            To = 1,
            Duration = TimeSpan.FromMilliseconds(180),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        });
    }

    private void AddDetail(string message)
    {
        _details.Add($"[{DateTime.Now:HH:mm:ss}] {message}");
        DetailsTextBox.Text = string.Join(Environment.NewLine, _details);
        DetailsExpander.Visibility = Visibility.Visible;
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

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        WindowMotion.PlayEnter(this);
    }

    private async void Window_Closing(object? sender, CancelEventArgs e)
    {
        if (_allowClose)
        {
            return;
        }

        e.Cancel = true;
        _allowClose = true;
        await WindowMotion.PlayExitAsync(this);
        Close();
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton != MouseButton.Left)
        {
            return;
        }

        if (e.ClickCount == 2)
        {
            ToggleMaximize();
            return;
        }

        DragMove();
    }

    private void MinimizeButton_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void MaximizeButton_Click(object sender, RoutedEventArgs e)
    {
        ToggleMaximize();
    }

    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void Window_StateChanged(object? sender, EventArgs e)
    {
        UpdateCaptionButton();
    }

    private void ToggleMaximize()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void UpdateCaptionButton()
    {
        if (MaximizeButton == null || WindowShell == null)
        {
            return;
        }

        bool maximized = WindowState == WindowState.Maximized;
        MaximizeButton.Content = maximized ? "\uE923" : "\uE922";
        MaximizeButton.ToolTip = maximized ? "还原" : "最大化";
        WindowShell.Margin = maximized ? new Thickness(0) : new Thickness(14);
        WindowShell.CornerRadius = maximized ? new CornerRadius(0) : new CornerRadius(26);
    }

    private void ThemeToggleButton_Checked(object sender, RoutedEventArgs e)
    {
        ApplyTheme(AppTheme.Light);
    }

    private void ThemeToggleButton_Unchecked(object sender, RoutedEventArgs e)
    {
        ApplyTheme(AppTheme.Dark);
    }

    private void ApplyTheme(AppTheme theme)
    {
        ThemeManager.ApplyTheme(System.Windows.Application.Current, theme);
        ThemeNameText.Text = theme == AppTheme.Dark ? "暗色" : "亮色";
    }

    private void NavigationButton_Checked(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement { Tag: string tag })
        {
            return;
        }

        FrameworkElement? page = tag switch
        {
            "Overview" => OverviewPage,
            "Settings" => SettingsPage,
            "Details" => DetailsPage,
            _ => null
        };

        if (page != null)
        {
            ShowPage(page);
        }
    }

    private void ShowPage(FrameworkElement nextPage)
    {
        if (_currentPage == nextPage)
        {
            nextPage.Visibility = Visibility.Visible;
            nextPage.Opacity = 1;
            return;
        }

        FrameworkElement? previousPage = _currentPage;
        _currentPage = nextPage;

        nextPage.Visibility = Visibility.Visible;
        nextPage.Opacity = 0;
        TranslateTransform nextTransform = EnsureTranslateTransform(nextPage);
        nextTransform.X = 18;

        AnimateElement(nextPage, UIElement.OpacityProperty, 1, 230);
        AnimateTransform(nextTransform, TranslateTransform.XProperty, 0, 230);

        if (previousPage == null)
        {
            return;
        }

        TranslateTransform previousTransform = EnsureTranslateTransform(previousPage);
        DoubleAnimation fadeOut = CreateAnimation(0, 150);
        fadeOut.Completed += (_, _) =>
        {
            previousPage.Visibility = Visibility.Collapsed;
            previousTransform.X = 0;
        };
        previousPage.BeginAnimation(OpacityProperty, fadeOut);
        previousTransform.BeginAnimation(TranslateTransform.XProperty, CreateAnimation(-10, 150));
    }

    private void SetBusyVisual(bool busy)
    {
        BusyPill.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;

        if (busy)
        {
            BusyOverlay.Visibility = Visibility.Visible;
            AnimateElement(BusyOverlay, UIElement.OpacityProperty, 1, 180);
            return;
        }

        DoubleAnimation fadeOut = CreateAnimation(0, 160);
        fadeOut.Completed += (_, _) => BusyOverlay.Visibility = Visibility.Collapsed;
        BusyOverlay.BeginAnimation(OpacityProperty, fadeOut);
    }

    private async void ShowToast(string title, string message, bool isError = false)
    {
        _toastCancellation?.Cancel();
        CancellationTokenSource cancellation = new();
        _toastCancellation = cancellation;

        ToastTitleText.Text = title;
        ToastMessageText.Text = message;
        ToastAccentBar.Background = (System.Windows.Media.Brush)FindResource(isError ? "DangerBrush" : "AccentBrush");
        ToastHost.Visibility = Visibility.Visible;
        ToastHost.Opacity = 0;
        TranslateTransform transform = EnsureTranslateTransform(ToastHost);
        transform.X = 18;

        AnimateElement(ToastHost, UIElement.OpacityProperty, 1, 190);
        AnimateTransform(transform, TranslateTransform.XProperty, 0, 190);

        try
        {
            await Task.Delay(isError ? 4600 : 3000, cancellation.Token);
        }
        catch (OperationCanceledException)
        {
            return;
        }

        if (_toastCancellation != cancellation)
        {
            return;
        }

        DoubleAnimation fadeOut = CreateAnimation(0, 180);
        fadeOut.Completed += (_, _) =>
        {
            if (_toastCancellation == cancellation)
            {
                ToastHost.Visibility = Visibility.Collapsed;
            }
        };
        ToastHost.BeginAnimation(OpacityProperty, fadeOut);
        transform.BeginAnimation(TranslateTransform.XProperty, CreateAnimation(18, 180));
    }

    private static TranslateTransform EnsureTranslateTransform(FrameworkElement element)
    {
        if (element.RenderTransform is TranslateTransform existing)
        {
            return existing;
        }

        TranslateTransform transform = new();
        element.RenderTransform = transform;
        return transform;
    }

    private static void AnimateElement(UIElement target, DependencyProperty property, double to, int milliseconds)
    {
        target.BeginAnimation(property, CreateAnimation(to, milliseconds), HandoffBehavior.SnapshotAndReplace);
    }

    private static void AnimateTransform(Animatable target, DependencyProperty property, double to, int milliseconds)
    {
        target.BeginAnimation(property, CreateAnimation(to, milliseconds), HandoffBehavior.SnapshotAndReplace);
    }

    private static DoubleAnimation CreateAnimation(double to, int milliseconds)
    {
        return new DoubleAnimation
        {
            To = to,
            Duration = TimeSpan.FromMilliseconds(milliseconds),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
    }
}
