using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media.Animation;

namespace ManosabaUpdater.UI;

public static class AnimatedProgress
{
    public static readonly DependencyProperty IsEnabledProperty =
        DependencyProperty.RegisterAttached(
            "IsEnabled",
            typeof(bool),
            typeof(AnimatedProgress),
            new PropertyMetadata(false, OnIsEnabledChanged));

    private static readonly DependencyProperty IsAnimatingProperty =
        DependencyProperty.RegisterAttached(
            "IsAnimating",
            typeof(bool),
            typeof(AnimatedProgress),
            new PropertyMetadata(false));

    public static bool GetIsEnabled(DependencyObject element)
    {
        return (bool)element.GetValue(IsEnabledProperty);
    }

    public static void SetIsEnabled(DependencyObject element, bool value)
    {
        element.SetValue(IsEnabledProperty, value);
    }

    private static bool GetIsAnimating(DependencyObject element)
    {
        return (bool)element.GetValue(IsAnimatingProperty);
    }

    private static void SetIsAnimating(DependencyObject element, bool value)
    {
        element.SetValue(IsAnimatingProperty, value);
    }

    private static void OnIsEnabledChanged(DependencyObject element, DependencyPropertyChangedEventArgs args)
    {
        if (element is not System.Windows.Controls.ProgressBar progressBar)
        {
            return;
        }

        if ((bool)args.NewValue)
        {
            progressBar.ValueChanged += OnValueChanged;
            progressBar.Unloaded += OnUnloaded;
        }
        else
        {
            progressBar.ValueChanged -= OnValueChanged;
            progressBar.Unloaded -= OnUnloaded;
        }
    }

    private static void OnUnloaded(object sender, RoutedEventArgs args)
    {
        if (sender is System.Windows.Controls.ProgressBar progressBar)
        {
            progressBar.ValueChanged -= OnValueChanged;
            progressBar.Unloaded -= OnUnloaded;
        }
    }

    private static void OnValueChanged(object sender, RoutedPropertyChangedEventArgs<double> args)
    {
        if (sender is not System.Windows.Controls.ProgressBar progressBar || GetIsAnimating(progressBar))
        {
            return;
        }

        SetIsAnimating(progressBar, true);
        DoubleAnimation animation = new()
        {
            From = args.OldValue,
            To = args.NewValue,
            Duration = TimeSpan.FromMilliseconds(260),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        animation.Completed += (_, _) =>
        {
            progressBar.BeginAnimation(RangeBase.ValueProperty, null);
            SetIsAnimating(progressBar, false);
        };
        progressBar.BeginAnimation(RangeBase.ValueProperty, animation, HandoffBehavior.SnapshotAndReplace);
    }
}
