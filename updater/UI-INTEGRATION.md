# WPF UI Integration Notes

The updater UI is now split into theme dictionaries, shared control templates, and small behavior helpers.

## Resource Dictionaries

- `Resources/Themes/DarkTheme.xaml` contains the default dark Fluent-style palette.
- `Resources/Themes/LightTheme.xaml` contains the companion light palette with the same resource keys.
- `Resources/Styles/Controls.xaml` contains the shared typography, cards, buttons, inputs, lists, toggles, sliders, progress bars, scrollbars, expanders, and tooltips.

`App.xaml` should merge only the shared styles. The active theme is applied at startup by `UI/ThemeManager.cs`, and can be switched at runtime from the main window.

## Window Base

`MainWindow.xaml` uses a custom title bar with `WindowChrome`, so the window keeps resizing and maximize behavior while replacing the stock frame. If this UI is moved to another WPF window, copy the `WindowChrome.WindowChrome` block and wire the same title-bar handlers:

- `TitleBar_MouseLeftButtonDown`
- `MinimizeButton_Click`
- `MaximizeButton_Click`
- `CloseButton_Click`
- `Window_Closing`
- `Window_StateChanged`

## Behaviors

- `UI/SmoothScrollViewer.cs` provides `ui:SmoothScrollViewer.IsEnabled="True"` for animated wheel scrolling.
- `UI/AnimatedProgress.cs` provides `ui:AnimatedProgress.IsEnabled="True"` for eased progress changes.
- `UI/WindowMotion.cs` provides the window open and close transitions.

The updater's download, manifest validation, SHA-256 checks, and settings persistence remain in the existing service and settings classes.
