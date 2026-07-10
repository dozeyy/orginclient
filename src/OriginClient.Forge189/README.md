# Origin Client — Forge classics (1.8.9 & 1.12.2)

Origin on the two classic pillars, running on **Forge + OptiFine** (the only
shader path on these versions). Sibling module: `../OriginClient.Forge1122`.

## Build (needs a Java 8 JDK)

These use legacy ForgeGradle, which requires Java 8 to run Gradle:

```bash
# from this module dir (or ../OriginClient.Forge1122)
JAVA_HOME=/path/to/jdk8 ./gradlew build --no-daemon
# -> build/libs/originclient-1.8.9-forge-0.4.1.jar
```

A portable Temurin 8 lives at `C:\Users\Will\.jdks\jdk8u492-b09` on the dev box.
CI installs Java 8 and builds both modules before the launcher publish
(`.github/workflows/launcher-release.yml`).

## Frozen toolchains (the combos that actually work)

| | 1.8.9 (`Forge189`) | 1.12.2 (`Forge1122`) |
|---|---|---|
| Gradle | 3.1 | 4.10.3 |
| ForgeGradle | 2.1 | 2.3 |
| Mixin | 0.7.10 | 0.7.11 |
| MCP mappings | stable_22 | snapshot_20171003 |
| Forge | 1.8.9-11.15.1.2318 | 1.12.2-14.23.5.2847 |

Mixins bootstrap via an `FMLCorePlugin` coremod (`com.origin.client.forge.MixinLoader`)
+ `MixinTweaker` in the jar manifest; the jar is shadowed then reobfuscated.

## What's implemented (compile-verified)

- **Branded main menu** — Origin backdrop (near-black, rotating orbital rings,
  grain, vignette, corner brackets) + the ORIGIN wordmark replace the vanilla
  panorama/logo; buttons kept. Uses the *same* baked assets as the Fabric build.
- **HUD** — FPS / coords / facing readout + a **keystrokes** overlay (WASD +
  LMB/RMB + jump), independently toggleable.
- **Right-Shift mod menu** — non-pausing, Deskify-styled toggle list.
- **Features** — zoom (FOV), fullbright (gamma), toggle sprint/sneak
  (client-side, creative-safe). Config persists as `originclient.json`, same
  schema shape as Fabric.
- Everything is **fail-soft**: a load/draw failure falls back to vanilla, never
  a crash.

## ⚠️ Not yet verified in-game

Compile-clean proves the mappings/mixin targets resolve — NOT that it looks and
feels right. A real 1.8.9 / 1.12.2 launch is needed to confirm:

1. The branded menu renders (the `GuiMainMenu.drawScreen` HEAD-cancel +
   `super.drawScreen` approach — buttons should draw over the Origin backdrop).
2. **OptiFine + the Origin coremod coexist** (both are coremods; expected to,
   but unverified here).
3. Feature behaviour (zoom amount, fullbright ceiling, toggle sprint/sneak feel).

## Next pass toward full 1.21.1 parity

Not yet ported (render/camera-mixin heavy — better done with in-game iteration):
freelook, CPS counter, more HUD readouts (armor/potion/ping), motion blur,
nametag/scoreboard tweaks, weather/time changer, block outline, chunk borders,
hitboxes, particle changer, chat tweaks. The shared theme/feature code is copied
verbatim into each module (the three toolchains can't share one Gradle source set).
