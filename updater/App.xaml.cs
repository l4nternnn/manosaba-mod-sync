namespace ManosabaUpdater;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        UI.ThemeManager.ApplyTheme(this, UI.AppTheme.Light);
        base.OnStartup(e);
    }
}
