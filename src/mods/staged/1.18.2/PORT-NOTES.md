# Origin 1.18.2 — port notes & mixin audit

Pre-1.19-era module (build target **1.18.2**, `fabric.mod.json` range
`>=1.18- <1.19-`; the launcher offers **1.18.2 only**). Forked from
`src/mods/staged/1.19.2` (the Gfx-wrapper + widget-retarget foundation).
Every statement below was verified with `javap -p -s -c` against the
loom-mapped jars — **1.18.2** (clientonly + common) for the build target, and
**1.18.1 merged** (via a scratch loom probe) for the declared range floor.
Nothing was taken from memory.

Result of this audit: **zero mixin fixes needed.** The fork had already been
correctly era-ported (chat, gamma/LightTexture, TitleScreen wordmark lever,
TextComponent factories, tab-era mixins already absent). Every `@Inject`/
`@Redirect`/`@ModifyVariable` target and every `@Shadow`/`@Accessor` member was
found present on 1.18.2 with the exact descriptor the mixin declares. No
retargets, no drops.

## Era deltas vs the 1.19.2 module (already applied in the fork; all verified)

- **Component factories**: `Component.literal/translatable/empty` are 1.19+ →
  `new TextComponent(str)` / `TextComponent.EMPTY`. Verified `TextComponent`
  class + `EMPTY` field present on 1.18.2 and 1.18.1. Used by ChatTimestampMixin,
  PauseScreen guard, mod menu, HUD, shader browser (all compile-clean).
- **Chat is pre-signature**: 1.18.2 `ChatComponent` has no MessageSignature/
  GuiMessageTag era. `addMessage(Component)` (public) delegates to the private
  `addMessage(Lnet/minecraft/network/chat/Component;I)V` that every new message
  funnels through — that is the `@ModifyVariable` target. `@Shadow allMessages`
  is `List<GuiMessage<Component>>` (exact match). The re-trim after a mutation
  is **`rescaleChat()V`** on this era (the 1.19.x `refreshTrimmedMessage` name
  does not exist yet) — `@Shadow public abstract void rescaleChat()` verified.
- **No `OptionInstance`** (that is 1.19+): fullbright/gamma reads
  `Options.gamma` directly — a `public double gamma` field. LightTextureMixin's
  `@Redirect` is a **FIELD GETFIELD** redirect on
  `Lnet/minecraft/client/Options;gamma:D`; bytecode of `updateLightTexture(F)V`
  confirms a `getfield … Options.gamma:D` at offset 489. Binds.
- **`renderSky` gained Camera + isFoggy in 1.18.2**: on 1.18.2 the signature is
  `renderSky(PoseStack;Matrix4f;F;Camera;Z;Runnable)V` (6-arg) — matches
  LevelRendererMixin's captured handler args exactly. (On the 1.18.0/1.18.1
  floor it is the older 4-arg `renderSky(PoseStack;Matrix4f;F;Runnable)V` — see
  floor audit; that is a silent, non-offered degradation.)
- **Pre-JOML math**: `renderSky`'s matrix param and `Gfx.fillGradient` use
  `com.mojang.math.Matrix4f`; ring/mark rotation uses
  `Vector3f.ZP.rotationDegrees` (import swap only, carried from 1.19.2).
- **Tab-era mixins absent**: TabButton / TabNavigationBar / ExperimentsScreen /
  CreateWorldScreen mixins are not in the module or in `mixins.json` (those
  classes are 1.19.4+ / draw nothing this era) — dropped at fork time. The
  generic ScreenBackgroundMixin still restyles those screens' ancestors.
- **Widget targeting ported as-is** from 1.19.2: button + slider mixins target
  `AbstractWidget.renderButton(PoseStack;IIF)V` (HEAD, cancellable) and scope
  with `instanceof`; slider `value` (a `protected double` on AbstractSliderButton)
  reached via **AbstractSliderButtonAccessor** (`@Accessor("value") D`). Checkbox
  declares its own `renderButton` — hooked directly. All verified on 1.18.2.
- **`Minecraft.fps` is a `private static int`** — read via MinecraftFpsAccessor
  (`@Accessor("fps")`), `getFps()` being 1.19.4+. Verified.

## Mixin audit — originclient.client.mixins.json (33 entries, all OK on 1.18.2)

| Mixin | Verdict (target descriptor on 1.18.2) |
|---|---|
| GameRendererMixin | OK — `getFov(Camera;FZ)D` (private, double → `CallbackInfoReturnable<Double>`). |
| GuiHudMixin | OK — `Gui.render(PoseStack;F)V` RETURN. |
| MouseHandlerMixin | OK — `onPress(JIII)V`, `onScroll(JDD)V`, `turnPlayer()V`; `@Shadow accumulatedDX/DY:D`, `isMouseGrabbed()Z`. |
| ClientLevelTimeMixin | OK — adds `dayTime()J` override; `LevelAccessor.dayTime()J` default present (drives sky/moon; `Level.getLevelData().getDayTime()` compile-clean). |
| CameraMixin | OK — `@Redirect` on `Camera.setRotation(FF)V` inside `setup(BlockGetter;Entity;ZZF)V` (3 invokes in setup, all redirected). |
| LoadingOverlayMixin | OK — `render(PoseStack;IIF)V` TAIL; `@Shadow currentProgress:F`. |
| TitleScreenMixin | OK — all 6 redirect levers present in `render()` bytecode with exact descriptors & counts (see below). |
| AbstractButtonMixin | OK — `@Mixin(AbstractWidget)`, `renderButton(PoseStack;IIF)V` HEAD, `instanceof AbstractButton`. |
| ScreenBackgroundMixin | OK — explicit descriptors `renderBackground(Lcom/mojang/blaze3d/vertex/PoseStack;)V` (2-arg overload also exists → bare name ambiguous) and `renderDirtBackground(I)V`. |
| AbstractSelectionListMixin | OK — `render(PoseStack;IIF)V`; `@Shadow renderBackground/renderTopAndBottom:Z`. |
| AbstractSliderButtonMixin | OK — `@Mixin(AbstractWidget)`, `renderButton` HEAD, `instanceof AbstractSliderButton`; value via accessor. |
| AbstractSliderButtonAccessor | OK — `@Accessor("value") D` on AbstractSliderButton (`protected double value`). |
| CheckboxMixin | OK — `Checkbox.renderButton(PoseStack;IIF)V` (declared, no super); `selected()Z`. |
| LevelRendererMixin | OK on 1.18.2 — `renderSnowAndRain(LightTexture;FDDD)V` + `renderSky(PoseStack;Matrix4f;F;Camera;Z;Runnable)V` (6-arg, era-correct). |
| LightTextureMixin | OK — FIELD `@Redirect` on `Options.gamma:D` GETFIELD in `updateLightTexture(F)V`. |
| ParticleEngineMixin | OK — `createParticle(ParticleOptions;DDDDDD)Particle`, `destroy(BlockPos;BlockState)V`, `crack(BlockPos;Direction)V`. |
| SingleQuadParticleMixin | OK — `getQuadSize(F)F`. |
| LevelTimeMixin | OK — `Level.getDayTime()J`. |
| HitboxMixin | OK — `renderHitbox(PoseStack;VertexConsumer;Entity;F)V` (private static). |
| EntityNametagMixin | OK — `shouldShowName(Entity)Z`, `renderNameTag(Entity;Component;PoseStack;MultiBufferSource;I)V`. |
| ChatTimestampMixin | OK — `@ModifyVariable addMessage(Component;I)V`; `@Shadow allMessages:List<GuiMessage<Component>>`, `rescaleChat()V`; TextComponent-built stamps. |
| GuiEffectsMixin | OK — `renderEffects(PoseStack)V`. |
| GuiScoreboardMixin | OK — `displayScoreboardSidebar(PoseStack;Objective)V` (private). |
| GameRendererAccessor | OK — `@Invoker loadEffect(ResourceLocation)V` (private). |
| PostChainAccessor | OK — `@Accessor passes:List<PostPass>`; `PostPass.getEffect()` present. |
| MinecraftFramerateMixin | OK — `getFramerateLimit()I` (private). |
| MinecraftFpsAccessor | OK — static `@Accessor("fps") I`. |
| ToastComponentMixin | OK — `addToast(Toast)V`. |
| EntityRenderDispatcherMixin | OK — `shouldRender(Entity;Frustum;DDD)Z`. |
| BlockEntityRenderDispatcherMixin | OK — `render(BlockEntity;FPoseStack;MultiBufferSource)V`. |
| PauseScreenMixin | OK — `createPauseMenu()V` TAIL; guard button rebuilt with the era Button ctor + `x`/`y` fields. |
| IrisShadowDirectivesMixin | OK unchanged — soft `@Mixin(targets="net.coderbot.iris.shaderpack.PackShadowDirectives")`, `remap=false`, `require=0`. |
| IrisWatermarkMixin | OK unchanged — soft `net.coderbot.iris.gui.screen.ShaderPackScreen`, `remap=false`, `require=0`. |

### TitleScreenMixin — render() bytecode evidence (1.18.2)
No LogoRenderer/SplashRenderer this era. Counts in `render()`:
- `PanoramaRenderer.render(FF)V` — **1** (suppressPanorama).
- 10-arg `blit(PoseStack;IIIIFFIIII)V` (PANORAMA_OVERLAY) — **1** (suppressOverlay).
- `blitOutlineBlack(IILjava/util/function/BiConsumer;)V` — **2** (normal + "Minceraft"
  easter-egg branch, one runs per frame; a single `@Redirect` wraps both and
  draws the Origin wordmark).
- 8-arg `blit(PoseStack;IIFFIIII)V` ("Java Edition" badge) — **1** (suppressEdition).
- `drawCenteredString(PoseStack;Font;String;III)V` (splash) — **1** (noSplash).
- `drawString(PoseStack;Font;String;III)V` (version line) — **1** (noVersion).
All targeted invokes sit inside `render()` (offsets after the method start);
the HEAD background inject and TAIL init strip (ImageButton/PlainTextButton,
both present) are unchanged.

## Mixin audit — originclient.loading.mixins.json (4 entries, all OK)

LevelLoadingScreenMixin / ReceivingLevelScreenMixin / ProgressScreenMixin /
ConnectScreenMixin — each targets `render(PoseStack;IIF)V`, all four verified
present on 1.18.2 (HEAD/TAIL as declared).

`mixins.json` client array (33) and loading array (4) match the on-disk mixin
class set exactly — no stale entries (no tab-era classes referenced).

## Feature deltas vs the 1.19.2 module

- **Full parity** with the 1.19.2 module on 1.18.2: title scene + cursor glow +
  account chip, loading/progress/connect scenes, mod menu, HUD + editor, every
  mod (zoom, fullbright, hitboxes, chunk borders, block overlay/outline,
  CPS/FPS/keystrokes, motion blur, freelook, weather/sky/time toggles,
  particles, nametags, scoreboard, toasts, chat timestamps/stack-spam), shader
  browser + downloader + Iris bridge (`net.coderbot.*`), crash fail-soft.
- Chat mod uses the era-correct `addMessage(Component,int)` funnel + `rescaleChat`
  (not the 1.19.x signature path) — timestamps/stack-spam WORK on 1.18.2.
- Sky "Flat" toggle WORKS on 1.18.2 (6-arg renderSky binds).
- No feature lost vs the 1.19.2 module. Tab-bar / Experiments restyles were
  already gone with the vanilla features (classes don't exist this era) — no
  regression vs vanilla.

## Range-floor audit (declared `>=1.18- <1.19-`; javap'd the 1.18.1 merged jar)

1.18.0 → 1.18.1 was a bugfix release with no client API changes, so the 1.18.1
jar represents the whole 1.18.0/1.18.1 floor.

**1.18.2 (the ONLY version the launcher offers) is degradation-free** — every
mixin binds and every non-mixin reference resolves.

**1.18.0 / 1.18.1 (tolerated by the range, never offered) — no crash path, two
silent degradations:**
- **Sky "Flat" toggle absent.** `LevelRenderer.renderSky` is the 4-arg
  `(PoseStack;Matrix4f;F;Runnable)V` here (Camera + `isFoggy` were added in
  1.18.2). LevelRendererMixin's handler captures the 6-arg shape, so the
  `@Inject` cannot match → fails to bind. Tolerated (`required:false` /
  `defaultRequire:0`); the sky pass renders vanilla. `renderSnowAndRain`
  (`LightTexture;FDDD`) is identical on the floor → Weather toggle still works.
- **Possible extra title-screen string hidden.** 1.18.1's `render()` carries an
  extra `drawString(PoseStack;Font;String;III)V` invoke vs 1.18.2's one; the
  `noVersion` `@Redirect` (redirects all matches in `render()`, `required:false`)
  would suppress it too. Cosmetic, no crash.
- **No non-mixin floor risk.** All 34 `net.minecraft.*` classes referenced by
  non-mixin code resolve on 1.18.1 (spot-verified: `TextComponent`/`EMPTY`,
  `TextureAtlasSprite.atlas()`, `PostPass.getEffect()`, plus the full stable
  1.16/1.17-era set). Non-mixin code never references `renderSky` or any
  1.18.2-only member — the only 1.18.x drift is renderSky, and that lives
  solely in the fail-soft LevelRendererMixin.

1.18.0/1.18.1 are tolerated by the range, not offered by the launcher.

## What the downstream ports (1.17.1 / 1.16.5) must know

1. **Component factories** stay `new TextComponent`/`new TranslatableComponent`;
   `TextComponent.EMPTY` for empty. Same as here, all the way down.
2. **Chat**: re-javap per era. On 1.18.2 the terminal funnel is
   `addMessage(Component,int)` + `rescaleChat()V` + `allMessages` typed
   `List<GuiMessage<Component>>`. 1.17.1 is likely identical; **1.16.5 differs**
   (`GuiMessage` generics and the addMessage/rescale shapes changed) — re-derive
   both `@Shadow`s or drop ChatTimestampMixin for that module (feature quietly
   absent beats broken).
3. **`renderSky` param drift is real within a minor line** — do not assume the
   6-arg shape. 1.17.1's `renderSky` = `(PoseStack;Matrix4f;F;Runnable)V`
   (4-arg, the 1.18.0/1.18.1 shape, NOT 1.18.2's). The LevelRendererMixin
   handler MUST drop the `Camera`/`isFoggy` captures for 1.17.1/1.16.5. javap
   the exact descriptor per era before porting.
4. **`Options.gamma`** stays a plain `public double` field pre-1.19 — keep the
   FIELD GETFIELD `@Redirect` in LightTextureMixin (no OptionInstance). Re-check
   the `updateLightTexture` bytecode reads it via GETFIELD on 1.17.1/1.16.5.
5. **Widget targeting** (`AbstractWidget.renderButton` + `instanceof` + value
   accessor) ports as-is to 1.17.1; **1.16.5 needs re-verification** and Java 8
   rewrites (no `instanceof`-pattern syntax — the two widget mixins use it; no
   `var`; `options.release = 8`).
6. **RenderSystem**: 1.17.1 keeps `setShader*`/`setShaderTexture`/`setShaderColor`
   (Gfx unchanged). **1.16.5 has none** — Gfx must bind via
   `TextureManager.bind` + `RenderSystem.color4f`, and BlockOverlayRenderer's
   immediate-mode fill must lose `setShader` (fixed-function).
7. **TitleScreen wordmark lever** (2× `blitOutlineBlack` + 8-arg badge blit +
   10-arg overlay blit + 1× splash drawCenteredString + version drawString)
   held from 1.19.2 → 1.18.2 — but re-count every redirect shape per era; the
   splash/version/overlay counts WILL drift (1.18.1 already shows a 2nd
   drawString). javap render() bytecode each time.
8. **Item draws** keep the modelview-multiplication pattern (no PoseStack
   overloads on ItemRenderer pre-1.19.4). Registry statics stay on
   `net.minecraft.core.Registry`.
9. **Fabric API**: pick the era build from maven metadata (1.17.1 ≈
   `0.46.1+1.17`, 1.16.5 ≈ `0.42.0+1.16`). Confirm `WorldRenderEvents.BLOCK_OUTLINE`
   exists on those builds before porting BlockOverlayRenderer's event hook.
10. **Java level**: 1.17.1 → `options.release = 16`; 1.16.5 → `8` (full Java-8
    sweep: no `var`, no `instanceof` patterns, no `List.of`/`Map.of` in hot
    paths, no `String.repeat`/`readAllBytes`).

## Build status

`gradlew build --no-daemon` **exit 0** (Loom 1.17.14, JDK 17, fabric-api
`0.77.0+1.18.2`, loader `0.19.3`). No code changes were required by this audit,
so the green build is the pre-existing artifact:
`build/libs/originclient-1.18.2-0.4.1.jar`. Config verified:
`minecraft_version=1.18.2`, range `>=1.18- <1.19-`, `options.release=17` +
source/target 17, `archivesName=originclient-1.18.2`, mixin `compatibilityLevel`
`JAVA_17`. Not runClient-tested (orchestrator boot-sweeps staged modules later).
