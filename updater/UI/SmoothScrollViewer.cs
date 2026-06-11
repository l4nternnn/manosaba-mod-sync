using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;

namespace ManosabaUpdater.UI;

public static class SmoothScrollViewer
{
    public static readonly DependencyProperty IsEnabledProperty =
        DependencyProperty.RegisterAttached(
            "IsEnabled",
            typeof(bool),
            typeof(SmoothScrollViewer),
            new PropertyMetadata(false, OnIsEnabledChanged));

    public static readonly DependencyProperty AnimatedVerticalOffsetProperty =
        DependencyProperty.RegisterAttached(
            "AnimatedVerticalOffset",
            typeof(double),
            typeof(SmoothScrollViewer),
            new PropertyMetadata(0.0, OnAnimatedVerticalOffsetChanged));

    public static bool GetIsEnabled(DependencyObject element)
    {
        return (bool)element.GetValue(IsEnabledProperty);
    }

    public static void SetIsEnabled(DependencyObject element, bool value)
    {
        element.SetValue(IsEnabledProperty, value);
    }

    public static double GetAnimatedVerticalOffset(DependencyObject element)
    {
        return (double)element.GetValue(AnimatedVerticalOffsetProperty);
    }

    public static void SetAnimatedVerticalOffset(DependencyObject element, double value)
    {
        element.SetValue(AnimatedVerticalOffsetProperty, value);
    }

    private static void OnIsEnabledChanged(DependencyObject element, DependencyPropertyChangedEventArgs args)
    {
        if (element is not ScrollViewer viewer)
        {
            return;
        }

        if ((bool)args.NewValue)
        {
            viewer.PreviewMouseWheel += OnPreviewMouseWheel;
        }
        else
        {
            viewer.PreviewMouseWheel -= OnPreviewMouseWheel;
        }
    }

    private static void OnAnimatedVerticalOffsetChanged(DependencyObject element, DependencyPropertyChangedEventArgs args)
    {
        if (element is ScrollViewer viewer)
        {
            viewer.ScrollToVerticalOffset((double)args.NewValue);
        }
    }

    private static void OnPreviewMouseWheel(object sender, MouseWheelEventArgs args)
    {
        if (sender is not ScrollViewer viewer || viewer.ScrollableHeight <= 0)
        {
            return;
        }

        args.Handled = true;
        double target = Math.Clamp(viewer.VerticalOffset - args.Delta * 0.48, 0, viewer.ScrollableHeight);
        SetAnimatedVerticalOffset(viewer, viewer.VerticalOffset);

        DoubleAnimation animation = new()
        {
            From = viewer.VerticalOffset,
            To = target,
            Duration = TimeSpan.FromMilliseconds(260),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        viewer.BeginAnimation(AnimatedVerticalOffsetProperty, animation, HandoffBehavior.SnapshotAndReplace);
    }
}
