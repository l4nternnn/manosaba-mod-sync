namespace ManosabaUpdater.UI;

public static class ThemeManager
{
    private const string ThemeDictionaryKey = "Manosaba.ActiveThemeDictionary";

    public static AppTheme CurrentTheme { get; private set; } = AppTheme.Dark;

    public static void ApplyTheme(System.Windows.Application application, AppTheme theme)
    {
        var dictionaries = application.Resources.MergedDictionaries;
        for (int index = dictionaries.Count - 1; index >= 0; index--)
        {
            if (dictionaries[index].Contains(ThemeDictionaryKey))
            {
                dictionaries.RemoveAt(index);
            }
        }

        string themeName = theme == AppTheme.Dark ? "DarkTheme" : "LightTheme";
        System.Windows.ResourceDictionary dictionary = new()
        {
            Source = new Uri($"Resources/Themes/{themeName}.xaml", UriKind.Relative)
        };
        dictionary[ThemeDictionaryKey] = true;
        dictionaries.Add(dictionary);
        CurrentTheme = theme;
    }
}
