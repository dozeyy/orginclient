using System.IO;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json.Serialization;

namespace OriginLauncher.App.Core.Loaders;

// Simple Voice Chat (henkelmax) is auto-added to every Fabric instance the same
// way Fabric API is: a live Modrinth query for the build matching the EXACT MC
// version being installed, downloaded standalone into the mods folder. It is NOT
// a PerformanceModCatalog entry (that list is the hand-pinned perf stack) and it
// is deliberately NOT bundled jar-in-jar on most versions: SVC publishes narrow
// per-version builds (e.g. fabric-1.21.4 declares game_versions ["1.21.4"] only),
// so a single nested jar cannot span an Origin version group (the 1.21.4 build
// also serves 1.21.2/1.21.3) — Fabric would refuse to boot the off-versions.
// Installing the exact-version jar per instance sidesteps that entirely.
//
// The one exception is 1.21.1, whose Origin build bundles SVC jar-in-jar; the
// caller skips this installer for that build (see VersionManager's
// originBundlesPerfStack gate) so Fabric never sees two copies of the mod id.
// Fail-soft: offline, or no SVC build for this MC version → the instance is left
// without voice chat rather than failing the whole install.
public static class SimpleVoiceChatInstaller
{
    private const string ProjectSlug = "simple-voice-chat";
    private const string FilePrefix = "voicechat-"; // SVC jars are named voicechat-fabric-<mc>-<ver>.jar
    private static readonly HttpClient Http = CreateClient();

    private static HttpClient CreateClient()
    {
        var http = new HttpClient();
        // Modrinth asks every client to send a descriptive User-Agent — untagged
        // traffic is more likely to be rate-limited.
        http.DefaultRequestHeaders.UserAgent.ParseAdd("OriginLauncher/1.0 (+will@willhenry.me)");
        return http;
    }

    public static async Task InstallAsync(
        string mcVersion, string modsFolder, IProgress<string>? progress = null, CancellationToken ct = default)
    {
        Directory.CreateDirectory(modsFolder);

        // Skip-if-present (same as FabricApiInstaller): no repeat network call on
        // every subsequent launch of an already-provisioned instance. Match the
        // SVC filename prefix in either state (".jar" or a player-disabled
        // ".jar.disabled") so a mod the player deliberately turned off is respected.
        if (Directory.EnumerateFiles(modsFolder).Any(f =>
        {
            var n = Path.GetFileName(f);
            return n.StartsWith(FilePrefix, StringComparison.OrdinalIgnoreCase)
                && (n.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
                    || n.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase));
        }))
            return;

        progress?.Report("Installing Simple Voice Chat...");

        // Brackets/quotes must be percent-encoded or Modrinth returns 400.
        var gameVersions = Uri.EscapeDataString($"[\"{mcVersion}\"]");
        var loaders = Uri.EscapeDataString("[\"fabric\"]");
        var url = $"https://api.modrinth.com/v2/project/{ProjectSlug}/version" +
                  $"?game_versions={gameVersions}&loaders={loaders}";

        List<ModrinthVersion>? versions;
        try
        {
            versions = await Http.GetFromJsonAsync<List<ModrinthVersion>>(url, ct);
        }
        catch (HttpRequestException)
        {
            return; // offline or Modrinth unreachable — leave the instance without it rather than fail the install
        }

        var latest = versions?.OrderByDescending(v => v.DatePublished).FirstOrDefault();
        var file = latest?.Files.FirstOrDefault(f => f.Primary) ?? latest?.Files.FirstOrDefault();
        if (file == null)
            return; // no SVC build published for this exact MC version

        var destPath = Path.Combine(modsFolder, file.Filename);
        using var response = await Http.GetAsync(file.Url, HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();

        var tempPath = destPath + ".download";
        await using (var fileStream = File.Create(tempPath))
            await response.Content.CopyToAsync(fileStream, ct);
        File.Move(tempPath, destPath, overwrite: true);
    }

    private sealed record ModrinthVersion(
        [property: JsonPropertyName("date_published")] DateTimeOffset DatePublished,
        [property: JsonPropertyName("files")] List<ModrinthFile> Files);

    private sealed record ModrinthFile(
        [property: JsonPropertyName("url")] string Url,
        [property: JsonPropertyName("filename")] string Filename,
        [property: JsonPropertyName("primary")] bool Primary);
}
