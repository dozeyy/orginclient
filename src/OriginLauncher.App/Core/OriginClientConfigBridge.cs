using System.IO;
using System.Text.Json;
using System.Text.Json.Nodes;
using OriginLauncher.App.Core.Versions;

namespace OriginLauncher.App.Core;

// Reads/writes just the "originUiEnabled" flag in the Origin Client mod's own
// originclient.json — the same file OriginConfig.java (Gson, no naming
// policy) owns as its primary reader/writer. Parses as a loose JsonNode tree
// rather than a fixed-shape object: the launcher only ever touches this one
// field, so any other settings the player changed in-game (zoom FOV, HUD
// toggle, etc.) are read back and rewritten completely untouched instead of
// risking being dropped or reordered by a round-trip through a narrower C#
// model that doesn't know about them.
public static class OriginClientConfigBridge
{
    private const string FileName = "originclient.json";
    private const string FieldName = "originUiEnabled";

    // Every Origin-supported version has its own instance + originclient.json.
    // The toggle is a single global preference, so it is written to ALL of them
    // and read as "on unless some instance was explicitly turned off" — sourced
    // from VersionManager so there is one list to keep in sync.
    private static IEnumerable<string> InstanceVersions => VersionManager.OriginSupportedVersions;

    private static string ConfigPathFor(string version) =>
        Path.Combine(OriginPaths.Instances, version, "config", FileName);

    // Defaults to true (matching OriginFeatures.java's own default) when no
    // instance has been launched yet, so the toggle reflects reality instead of
    // guessing. If any existing instance config says false, the toggle shows off.
    public static bool IsOriginUiEnabled()
    {
        foreach (var version in InstanceVersions)
        {
            var path = ConfigPathFor(version);
            if (!File.Exists(path))
                continue;

            try
            {
                var node = JsonNode.Parse(File.ReadAllText(path));
                if (node?[FieldName]?.GetValue<bool>() == false)
                    return false;
            }
            catch (JsonException)
            {
                // treat an unreadable config as default-on
            }
        }

        return true;
    }

    public static void SetOriginUiEnabled(bool enabled)
    {
        foreach (var version in InstanceVersions)
            WriteFlag(ConfigPathFor(version), enabled);
    }

    private static void WriteFlag(string configPath, bool enabled)
    {
        JsonObject root;
        if (File.Exists(configPath))
        {
            try
            {
                root = JsonNode.Parse(File.ReadAllText(configPath)) as JsonObject ?? new JsonObject();
            }
            catch (JsonException)
            {
                root = new JsonObject();
            }
        }
        else
        {
            root = new JsonObject();
        }

        root[FieldName] = enabled;

        Directory.CreateDirectory(Path.GetDirectoryName(configPath)!);
        File.WriteAllText(configPath, root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
    }
}
