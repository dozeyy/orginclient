# Origin 1.19.4 — port notes & mixin audit

Foundation pre-1.20 port (first PoseStack-era module). Template:
`src/mods/versions/1.20`. Spec: PORT-PRE120-SPEC (Gfx wrapper architecture).
Every statement below was verified with `javap -p -s/-c` against the
loom-mapped jars — **1.19.4** (clientonly) for the build target and
**1.19.3** (merged, via a scratch loom probe) for the declared range floor
(`fabric.mod.json`: `>=1.19.3- <1.20-`). Nothing was taken from memory.

## The Gfx wrapper (the one architectural change)

`com.origin.client.client.gui.Gfx` — final class wrapping a `PoseStack`,
exposing the exact GuiGraphics call shapes the code already used
(fill / fillGradient / 2 blit overloads + sprite blit / drawString with
GuiGraphics' shadow-on default / enableScissor / disableScissor /
guiWidth / guiHeight / renderItem / renderItemDecorations / pose()).
Delegates to the public `GuiComponent` statics; each blit overload binds its
texture via `RenderSystem.setShaderTexture(0, ...)` first (GuiComponent's
blit sets the shader itself but not the texture — bytecode-verified).
`fillGradient` is re-implemented (GuiComponent's is protected static).
`enableScissor(IIII)` exists on GuiComponent in BOTH 1.19.4 and 1.19.3 —
direct delegate, no gui-scale math needed in this module.
Conversion was a type swap (28 files, ~138 call sites); a `new Gfx(poseStack)`
is constructed at every vanilla boundary: mixin handlers, the three Screen
subclasses' `render(PoseStack,...)` overrides, `OriginShaderButton
.renderWidget`, and the Fabric `ScreenEvents.beforeRender/afterRender`
lambdas (which pass PoseStack on 1.19.x fabric-api).

## Mixin audit — originclient.client.mixins.json (all 35 kept, none dropped)

| Mixin | Verdict |
|---|---|
| GameRendererMixin | OK — `getFov(Camera;FZ)D` returns double; `CallbackInfoReturnable<Double>` kept as-is. |
| GuiHudMixin | Retargeted descriptor only — `Gui.render(PoseStack;F)V`; handler wraps Gfx. |
| MouseHandlerMixin | OK — `onPress(JIII)V`, `onScroll(JDD)V`, `turnPlayer()V`, `@Shadow accumulatedDX/DY:D`, `isMouseGrabbed()Z` all match. |
| ClientLevelTimeMixin | OK — added `dayTime()` override; `LevelAccessor.dayTime()J` default exists. |
| CameraMixin | OK — `setup(BlockGetter;Entity;ZZF)V` contains 3 `setRotation(FF)V` invokes (all redirected, same as the 1.20 template; require=1 satisfied). |
| LoadingOverlayMixin | Retargeted descriptor — `render(PoseStack;IIF)V`; `@Shadow currentProgress:F` verified (also on 1.19.3). |
| TitleScreenMixin | **REWORKED** — 1.19.4 has no SplashRenderer and no `renderPanorama`/own `renderBackground`. HEAD background inject kept (PoseStack). Suppression is now four @Redirects, each the only call of its shape in `render()` (bytecode-verified): `PanoramaRenderer.render(FF)V` (panorama), owner-static `TitleScreen.blit(PoseStack;IIIIFFIIII)V` (PANORAMA_OVERLAY vignette), owner-static `drawCenteredString(PoseStack;Font;String;III)V` (the splash — pre-1.20 it's a String field drawn here; the pose push/rotate around it stays balanced), owner-static `drawString(...)V` (version line). Logo redirect retargeted to `LogoRenderer.renderLogo(PoseStack;IF)V` — LogoRenderer EXISTS in 1.19.4. init-TAIL button strip unchanged. |
| AbstractButtonMixin | Retargeted descriptor — `renderWidget(PoseStack;IIF)V` (declared on AbstractButton in 1.19.4). |
| ScreenBackgroundMixin | Retargeted descriptors — `renderBackground(PoseStack)V` + `renderDirtBackground(PoseStack)V`. |
| AbstractSelectionListMixin | OK — `render(PoseStack;IIF)V`; `@Shadow renderBackground/renderTopAndBottom:Z` verified. |
| TabButtonMixin | OK — TabButton exists in 1.19.4 (new that version); `renderWidget(PoseStack;IIF)V`, `isSelected()Z`. |
| TabNavigationBarMixin | Retargeted — pre-1.20 the bar draws via inherited GuiComponent STATICS (bytecode owner = TabNavigationBar): `fill(PoseStack;IIIII)V` + `blit(PoseStack;IIFFIIII)V`. Handlers are arg-only (static call), restore vanilla via `GuiComponent.fill/blit`. |
| CreateWorldScreenMixin | Retargeted — owner-static `CreateWorldScreen.blit(PoseStack;IIFFIIII)V`, the single blit of that shape in `render()` (footer separator, bytecode-verified). |
| ExperimentsScreenMixin | Retargeted — owner-static `ExperimentsScreen.blit(PoseStack;IIFFIIII)V`, single occurrence in `render()`. |
| AbstractSliderButtonMixin | Retargeted descriptor — `renderWidget(PoseStack;IIF)V`; `@Shadow value:D` verified. |
| CheckboxMixin | Retargeted descriptor — `renderWidget(PoseStack;IIF)V`; `selected()Z` verified. |
| LevelRendererMixin | OK — `renderSnowAndRain(LightTexture;FDDD)V` and `renderSky(PoseStack;Lorg/joml/Matrix4f;F;Camera;Z;Runnable)V` identical to the 1.20 module (JOML already in 1.19.3+). |
| LightTextureMixin | OK — `updateLightTexture(F)V`; `OptionInstance.get()` ordinal 1 is still gamma (ordinal 0 = darknessEffectScale; bytecode-verified). |
| ParticleEngineMixin | OK — `createParticle(ParticleOptions;DDDDDD)`, `destroy(BlockPos;BlockState)V`, `crack(BlockPos;Direction)V`. |
| SingleQuadParticleMixin | OK — `getQuadSize(F)F`. |
| LevelTimeMixin | OK — `Level.getDayTime()J`. |
| HitboxMixin | OK — `renderHitbox(PoseStack;VertexConsumer;Entity;F)V` private static, same as template. |
| EntityNametagMixin | OK — `shouldShowName(Entity)Z`, `renderNameTag(Entity;Component;PoseStack;MultiBufferSource;I)V`. |
| ChatTimestampMixin | OK unchanged — `addMessage(Component;MessageSignature;GuiMessageTag)V` exists on 1.19.4 AND 1.19.3; `@Shadow allMessages`, `refreshTrimmedMessage()V` verified. |
| GuiEffectsMixin | Retargeted descriptor — `renderEffects(PoseStack)V`. |
| GuiScoreboardMixin | Retargeted descriptor — `displayScoreboardSidebar(PoseStack;Objective)V`. |
| GameRendererAccessor | OK — `loadEffect(ResourceLocation)V` (package-private) exists. |
| PostChainAccessor | OK — `@Accessor("passes")` field `List<PostPass>` exists; `PostPass.getEffect()` + `EffectInstance.safeGetUniform` verified. |
| MinecraftFramerateMixin | OK — `getFramerateLimit()I`. |
| ToastComponentMixin | OK — `addToast(Toast)V`. |
| EntityRenderDispatcherMixin | OK — `shouldRender(Entity;Frustum;DDD)Z`. |
| BlockEntityRenderDispatcherMixin | OK — `render(BlockEntity;F;PoseStack;MultiBufferSource)V`. |
| PauseScreenMixin | OK — `createPauseMenu()V`; `Button.builder` + `.bounds` + `getX/getY` all exist in 1.19.4 AND 1.19.3 (builder arrived 1.19.3). |
| IrisShadowDirectivesMixin | OK unchanged — pre-1.20 Iris (1.6.x) keeps the `net.coderbot.iris.shaderpack.PackShadowDirectives` path this module already targets. |
| IrisWatermarkMixin | OK unchanged — `net.coderbot.iris.gui.screen.ShaderPackScreen`, `<init>` TAIL, @Pseudo/require 0. |

## Mixin audit — originclient.loading.mixins.json (all 4 kept)

| Mixin | Verdict |
|---|---|
| LevelLoadingScreenMixin | Retargeted descriptor — `render(PoseStack;IIF)V`. |
| ReceivingLevelScreenMixin | Retargeted descriptor — `render(PoseStack;IIF)V`. |
| ProgressScreenMixin | Retargeted descriptor — `render(PoseStack;IIF)V`. |
| ConnectScreenMixin | Retargeted descriptor — `render(PoseStack;IIF)V`. |

## Non-mixin deltas vs the 1.20 module

- `gui/Gfx.java` — new (see above). Everything else that drew via GuiGraphics
  is a mechanical type swap.
- `OriginScreenRenderer.renderTitleAccountChip` — `PlayerFaceRenderer.draw`
  is `(PoseStack,int,int,int)` in 1.19.x and draws whatever skin texture is
  bound, so the skin is bound via `RenderSystem.setShaderTexture(0, skin)`
  first (GuiGraphics-era passed the ResourceLocation in).
- `HudElements` — `BlockPos.containing` (1.19.4+) replaced with
  `player.blockPosition()`; `MobEffectInstance.isInfiniteDuration()` (1.19.4+)
  wrapped in a latched helper that returns false forever after the first
  `NoSuchMethodError` (range floor safety, see below).
- `Gfx.renderItem/renderItemDecorations` — latched fail-soft (the PoseStack
  overloads are 1.19.4+).
- `build.gradle` — archivesName `originclient-1.19.4`; dev-libs guard renamed
  to the 1.19.4-era Sodium/Iris jars (still `.exists()`-guarded, still absent
  from CI); Java release 17 kept.
- `gradle.properties` — minecraft 1.19.4, fabric_api `0.87.2+1.19.4` (newest
  1.19.4 build on maven.fabricmc.net at port time), loader 0.19.3.
- `.no-shared-sync` added; `overrides.txt` deleted (whole-module fork).
- Feature parity is otherwise 1:1 with versions/1.20: title scene + cursor
  glow + account chip, all loading/progress/connect scenes, mod menu, HUD +
  editor, every mod, shader browser/downloader/Iris bridge, crash fail-soft.

## Range-floor behavior (1.19.3, javap'd against its own mapped jar)

No crash path: every 1.19.4-only symbol reachable on 1.19.3 is either
mixin-guarded (`required:false` / `defaultRequire:0`), inside an existing
`catch (Throwable)`, or latched (the two HudElements/Gfx fixes above).
Known degradations when the jar runs on 1.19.3 (all silent):

- Buttons/sliders/checkboxes render VANILLA — the widget render method there
  is `AbstractWidget.renderButton` and AbstractButton/Checkbox/
  AbstractSliderButton declare no override, so the renderWidget injections
  have nothing to bind to.
- Title screen: Origin background/glow/chip draw and the panorama, overlay,
  splash and version line are still suppressed (all four redirect shapes
  match 1.19.3 bytecode), but the vanilla "Minecraft" logo shows — 1.19.3
  draws it via `blitOutlineBlack`, not LogoRenderer.
- `renderDirtBackground` is `(I)V` on 1.19.3 → the dirt-suppression hook
  skips (GenericDirtMessageScreen shows vanilla dirt).
- Armor HUD item icons absent (ItemRenderer PoseStack overloads are 1.19.4+;
  latched off). TabNavigationBar/TabButton/ExperimentsScreen mixins skip
  (classes don't exist — neither do those screens' tab UIs).

The launcher should expose **1.19.4** from this module; 1.19.3 is tolerated
by the range, not a shipped experience.

## What a downstream port (1.19.2 / 1.18.2 / 1.17.1 / 1.16.5) must know

1. **Widget restyle moves to `renderButton`** — ≤1.19.2 AbstractButton/
   Checkbox/AbstractSliderButton declare `renderButton(PoseStack;IIF)V`;
   retarget the three widget mixins (and `OriginShaderButton`'s override).
2. **Button API** — `Button.builder`/`.bounds`/`getX()` are 1.19.3+. ≤1.19.2:
   `new Button(x,y,w,h,component,onPress)` and the public `x`/`y` fields
   (PauseScreenMixin, OriginShaderButton, OriginButtonRenderer accessors).
3. **JOML boundary is 1.19.3** — ≤1.19.2 `com.mojang.math.Axis` doesn't
   exist: `Vector3f.ZP.rotationDegrees(deg)` → `pose.mulPose(Quaternion)`
   (OriginScreenRenderer.drawRings, OriginUi.mark). `renderSky`'s Matrix4f
   param becomes `com.mojang.math.Matrix4f` — LevelRendererMixin descriptor
   changes. Gfx.fillGradient's `org.joml.Matrix4f` likewise.
4. **Scissor** — `GuiComponent.enableScissor(IIII)` exists 1.19.3+ only.
   Older eras: re-implement in Gfx with `RenderSystem.enableScissor` +
   window gui-scale math (per spec).
5. **TitleScreen** — ≤1.19.3 draws the logo via `blitOutlineBlack` + blits;
   the wordmark replacement needs a different lever (redirect
   `blitOutlineBlack(IILjava/util/function/BiConsumer;)V` and the edition
   blit). The four suppression redirects in this module's TitleScreenMixin
   already match 1.19.3's shapes — reuse them.
6. **Delete for ≤1.19.3**: TabNavigationBarMixin, TabButtonMixin,
   ExperimentsScreenMixin (+ their mixins.json entries) — the classes are
   1.19.4-only. Drop the LogoRenderer redirect from TitleScreenMixin.
7. **ChatComponent** — the `(Component,MessageSignature,GuiMessageTag)`
   addMessage holds for 1.19.3/1.19.4 only; 1.19.2's chat-signature shape
   differs and ≤1.18.2 is single-arg. Re-derive per era or drop the mixin.
8. **ItemRenderer** — ≤1.19.3 has no PoseStack GUI-item overloads; the
   no-pose variants draw via the global modelview, so Gfx must multiply the
   RenderSystem modelview stack with `pose.last().pose()` around the call
   (or the armor HUD misplaces under scale).
9. **renderDirtBackground** is `(I)V` pre-1.19.4 — retarget the
   ScreenBackgroundMixin hook accordingly.
10. **Screens.beforeRender/afterRender fabric events already pass PoseStack**
    on every pre-1.20 fabric-api — the OriginClientMod boundary code here
    ports as-is. `WorldRenderEvents`/`BLOCK_OUTLINE` shapes are unchanged.
11. `isInfiniteDuration`/`blockPosition()` fixes here are already
    era-neutral — keep them.
12. Iris stays `net.coderbot.*` for every pre-1.20 build — both @Pseudo
    mixins and IrisBridge port unchanged.
13. Post-effect assets (motion blur) — legacy PostChain schema is unchanged
    back to 1.16; only the 1.16.5 module needs a GLSL-version re-check.

## Build status

`gradlew build` exit 0 (Loom 1.17-SNAPSHOT, Gradle wrapper, JDK 17
auto-provisioned). Artifact: `build/libs/originclient-1.19.4-0.4.1.jar`.
Not runClient-tested (orchestrator boot-sweeps staged modules later).
