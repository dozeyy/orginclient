namespace OriginLauncher.App.Core.Loaders;

public sealed record VersionPerfProfile(
    string McVersion,
    PerfStackTier Tier,
    PerfMod? Sodium,
    PerfMod? Indium,
    PerfMod? Lithium,
    PerfMod? Ferrite,
    PerfMod? Krypton,
    // Iris shader loader — only present on profiles whose Sodium build it is
    // version-compatible with (Iris hard-requires Sodium). Idle overhead with
    // no shaderpack active is ~zero, so it never costs FPS by default.
    PerfMod? Iris = null)
{
    public IEnumerable<PerfMod> Mods()
    {
        if (Sodium != null) yield return Sodium;
        if (Indium != null) yield return Indium;
        if (Lithium != null) yield return Lithium;
        if (Ferrite != null) yield return Ferrite;
        if (Krypton != null) yield return Krypton;
        if (Iris != null) yield return Iris;
    }
}
