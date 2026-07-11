using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace OriginLauncher.App.Core.Loaders;

// Iris ships its own "a new version is available" check (net.coderbot.iris.
// UpdateChecker), surfaced as clickable text in its shader-pack screen that
// opens a browser link — no in-launcher update flow exists for a bundled mod
// like this, and Origin owns the whole update experience via its own
// UpdateService, so the nag is just noise. Iris already ships an official
// off switch for it (IrisConfig.disableUpdateMessage, read from its own
// config/iris.properties) — this seeds that one key rather than patching
// Iris's code.
public static class IrisConfigSeeder
{
    private const string FileName = "iris.properties";
    private const string Key = "disableUpdateMessage";

    /// <summary>
    /// Ensures Iris's own config disables its update-check text. Idempotent
    /// and additive: reads whatever properties already exist (Iris's own
    /// defaults, or a player's prior edits) and only touches this one key, so
    /// nothing else Iris has written -- active shaderpack, video settings --
    /// is ever lost or reordered.
    /// </summary>
    public static void DisableUpdateMessage(string configFolder)
    {
        var path = Path.Combine(configFolder, FileName);
        var lines = File.Exists(path) ? File.ReadAllLines(path).ToList() : new List<string>();

        int existing = lines.FindIndex(l => l.TrimStart().StartsWith(Key + "="));
        if (existing >= 0)
        {
            if (lines[existing].Trim() == $"{Key}=true")
                return; // already set; avoid rewriting the file (and its mtime) every launch
            lines[existing] = $"{Key}=true";
        }
        else
        {
            lines.Add($"{Key}=true");
        }

        Directory.CreateDirectory(configFolder);
        File.WriteAllLines(path, lines);
    }
}
