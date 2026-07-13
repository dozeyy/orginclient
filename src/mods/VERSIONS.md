# Origin Client — version registry & how this tree works

The in-game mod ships **one build per Minecraft API family** (Minecraft
renames GUI/render internals between versions and mixins bind to exact
signatures, so one jar can't span families). This file is the single
source of truth for what's live, what's staged, and how code is shared.

## The tree

```
src/mods/
  shared/     the version-independent core (79 files today: theme, gui
              layout, hud, mods menu, config, and the few mixins that are
              byte-identical everywhere). Lives ONCE here; copied verbatim
              into every module by tools/shared-sync/sync.py.
  versions/   SHIPPED modules — built by CI, bundled into the launcher.
  staged/     WIP modules — never built by CI, never shipped. Work here
              can't affect players.
```

Every module under `versions/` and `staged/` is a **fully standalone Gradle
build** (own gradlew, build.gradle, complete src/). Nothing in any module
changes unless you change it or deliberately run the sync.

### The sharing rule

- **Shared fix or feature** → edit `shared/src/...`, run
  `python tools/shared-sync/sync.py`, rebuild affected modules.
- **Version-specific fix** → edit that module only. If the file also exists
  in `shared/`, add its path to the module's `overrides.txt` — that marks
  the deliberate fork and sync never touches it again.
- CI (`build-check.yml`) runs `sync.py --check` on every push: any silent
  drift between `shared/` and a module fails the build.

## Live versions (in `versions/`)

| Module | Covers | fabric.mod.json range | Install model | Java |
|--------|--------|----------------------|---------------|------|
| `1.21.1` | 1.21.1 | `~1.21.1` | **Bundles perf stack jar-in-jar** (Sodium/Indium/Iris/Lithium/…) | 21 |
| `1.20` | 1.20, 1.20.1 | `>=1.20- <1.20.2` | Origin jar + standalone catalog stack | 17 (via JDK 21) |
| `1.20.4` | 1.20.3, 1.20.4 | `>=1.20.3- <1.20.5` | standalone stack | 17 (via JDK 21) |
| `1.21` | 1.21 | `>=1.21- <1.21.1` | standalone stack (source byte-identical to 1.21.1 — shared pre-1.21.2 blit API) | 21 |
| `1.21.11` | 1.21.10, 1.21.11 | `>=1.21.10- <1.22` | standalone stack | 21 |

All are boot-verified with zero mixin-apply failures and full shader
integration (Iris + Sodium from the catalog pins). The `1.21.11` module is
the render-pipeline-era port (Identifier/RenderPipelines/input events — its
overrides.txt lists exactly what it forks from `shared/`).

### Why 1.21.2–1.21.9 aren't here yet (the hard truth about the 1.21.x line)

"1.21.2–1.21.11 is one build like 1.21.1" turned out to be false. Across
that range Minecraft rewrote its render/GUI/input system in **stages**, and
each stage introduces a genuinely-new-at-that-version class the compiled jar
references — so a single jar `NoClassDefFoundError`s on the versions below
its build target (proven by a per-version boot sweep through the real
launcher). The verified runtime boundaries:

| Boundary at | What appears |
|---|---|
| 1.21.2 | `GuiGraphics.blit` render-pipeline rework |
| 1.21.5 | hitboxes extracted into `HitboxRenderState` |
| 1.21.6 | GUI transforms → `Matrix3x2fStack`; `setShaderColor` removed |
| 1.21.9 | new input-event API (`MouseButtonEvent`) + typed `KeyMapping.Category` |
| 1.21.10 | Fabric API moved `WorldRenderEvents` into the `.world` subpackage |

So 1.21.2–1.21.9 need **≈4 more sub-family Origin builds** (each a real
port with its own mixin-descriptor work + boot verification), not a config
flip. Until built and boot-verified they stay out of `OriginBuilds`, so the
picker greys them out ("Coming Soon") — shipping a vanilla-menu version would
violate mandate #2. Partial ports for the 1.21.3–1.21.8 sub-families were
scaffolded during this work but are not verified and did not ship.

## Staged versions (in `staged/`)

| Module | Covers | Blocking | Guide |
|--------|--------|----------|-------|
| `26.2` | 26.2 | render layer mid-port to the retained-mode GUI; does not compile (most source parked in `disabled262/`). Java 25. The 1.21.11 module's port (see its overrides.txt) solved many of the same API moves — start there. | `staged/26.2/PORT-262.md` |

## Flipping a staged version live — the 3 coupling points

Promote in ONE commit, after javap-verifying every mixin descriptor against
the mapped jar and a clean `./gradlew runClient` (zero `Mixin apply ... failed`
lines):

1. **`src/OriginLauncher.App/Core/Versions/VersionManager.cs`** — uncomment
   the version's `OriginBuilds` entry (that's what makes the launcher
   install the jar).
2. **`.github/workflows/launcher-release.yml` AND `build-check.yml`** —
   uncomment the module's gradle build step (and `git mv` the module from
   `staged/` to `versions/`; update the step's `working-directory`).
3. **`src/OriginLauncher.App/OriginLauncher.App.csproj`** — the `<Content>`
   bundle entry already exists for every module and is conditional on the
   jar existing; verify its path points at `versions/<ver>` after the move.
   Also add the jar name to the release workflow's
   "Assert bundled mod jars present" list.

## Porting method that works

Copy the nearest API family module, adjust only version-forced deltas,
javap-verify every mixin descriptor against the mapped jar, then
runClient-verify. Compiling clean only proves mixin *targets exist* —
`@Inject` descriptor and `@Shadow` mismatches only surface at mixin apply
time.

Known API breakpoints: `GuiGraphics.blit` reworked in 1.21.2;
`Screen.renderBackground` gained args around 1.20.2; `GuiGraphics` only
exists since 1.20 (before that it's `PoseStack` draws — a much bigger port).

## The fail-soft contract (why "never broken" holds)

- Both mixin configs are `required: false` with `defaultRequire: 0` — a
  missing/renamed target skips that one surface silently; the game boots.
- Every Origin draw entry point catches `Throwable`. First failure flips a
  session-wide health switch: all Origin rendering stops and every
  suppression of vanilla drawing is gated on that flag, so vanilla visuals
  genuinely come back. Worst case is the vanilla look; there is no crash
  case. (This is the safety net, never the intended shipped state — see the
  mandate in the root CLAUDE.md.)

## Menus fit each version automatically

Origin restyles **in place** and never defines menu contents. Whatever
screens/widgets a version has keep their own layout and behavior — Origin
only repaints them. No per-version menu curation is needed.
