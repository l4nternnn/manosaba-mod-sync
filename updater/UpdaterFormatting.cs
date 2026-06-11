using System;

namespace ManosabaUpdater;

public static class UpdaterFormatting
{
    public static string FormatKilobytes(long bytes)
    {
        double kilobytes = Math.Max(0L, bytes) / 1024.0;
        return $"{kilobytes:F1} KB";
    }
}
