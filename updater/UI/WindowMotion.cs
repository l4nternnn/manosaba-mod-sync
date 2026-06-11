using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace ManosabaUpdater.UI;

public static class WindowMotion
{
    public static void PlayEnter(Window window)
    {
        ScaleTransform scale = EnsureScaleTransform(window);
        window.Opacity = 0;
        scale.ScaleX = 0.965;
        scale.ScaleY = 0.965;

        Storyboard storyboard = CreateScaleOpacityStoryboard(1, 1, 1, 280);
        storyboard.Begin(window, true);
    }

    public static Task PlayExitAsync(Window window)
    {
        EnsureScaleTransform(window);
        TaskCompletionSource completion = new();
        Storyboard storyboard = CreateScaleOpacityStoryboard(0.965, 0.965, 0, 190);
        storyboard.Completed += (_, _) => completion.TrySetResult();
        storyboard.Begin(window, true);
        return completion.Task;
    }

    private static Storyboard CreateScaleOpacityStoryboard(double scaleX, double scaleY, double opacity, int milliseconds)
    {
        CubicEase easing = new() { EasingMode = EasingMode.EaseOut };
        Storyboard storyboard = new();

        DoubleAnimation opacityAnimation = new()
        {
            To = opacity,
            Duration = TimeSpan.FromMilliseconds(milliseconds),
            EasingFunction = easing
        };
        Storyboard.SetTargetProperty(opacityAnimation, new PropertyPath(UIElement.OpacityProperty));
        storyboard.Children.Add(opacityAnimation);

        DoubleAnimation scaleXAnimation = new()
        {
            To = scaleX,
            Duration = TimeSpan.FromMilliseconds(milliseconds),
            EasingFunction = easing
        };
        Storyboard.SetTargetProperty(scaleXAnimation, new PropertyPath("RenderTransform.ScaleX"));
        storyboard.Children.Add(scaleXAnimation);

        DoubleAnimation scaleYAnimation = new()
        {
            To = scaleY,
            Duration = TimeSpan.FromMilliseconds(milliseconds),
            EasingFunction = easing
        };
        Storyboard.SetTargetProperty(scaleYAnimation, new PropertyPath("RenderTransform.ScaleY"));
        storyboard.Children.Add(scaleYAnimation);

        return storyboard;
    }

    private static ScaleTransform EnsureScaleTransform(Window window)
    {
        if (window.RenderTransform is ScaleTransform existing)
        {
            return existing;
        }

        ScaleTransform scale = new(1, 1);
        window.RenderTransformOrigin = new System.Windows.Point(0.5, 0.5);
        window.RenderTransform = scale;
        return scale;
    }
}
