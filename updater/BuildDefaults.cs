using System.Linq;
using System.Reflection;

namespace ManosabaUpdater;

public static class BuildDefaults
{
    public static string DefaultManifestUrl =>
        Assembly.GetExecutingAssembly()
            .GetCustomAttributes<AssemblyMetadataAttribute>()
            .FirstOrDefault(attribute => attribute.Key == "DefaultManifestUrl")
            ?.Value
        ?? "";
}
