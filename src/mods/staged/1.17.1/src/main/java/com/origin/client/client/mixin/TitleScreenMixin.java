package com.origin.client.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.origin.client.client.gui.Gfx;
import com.origin.client.client.mods.Mods;
import com.origin.client.client.render.OriginScreenRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Re-skins the main menu: the Origin background (charcoal + rotating rings +
// grain) replaces the panorama, the "ORIGIN" wordmark replaces the vanilla
// "Minecraft" logo, and the splash/version text + the language/accessibility/
// copyright buttons are hidden -- leaving just the real menu buttons + header.
//
// Strategy (all targets confirmed via javap/bytecode against the mapped 1.18.2
// jar -- this era predates GuiGraphics AND TitleScreen.renderPanorama, so the
// suppression shape differs from the 1.20 module):
//  - Draw the Origin background at render() HEAD -- render() is guaranteed to
//    run every frame, so the background is always painted, under the logo/
//    buttons that draw afterward.
//  - 1.18.2's render() calls this.panorama.render(partialTick, alpha) DIRECTLY
//    (no renderPanorama method to cancel), then blits the PANORAMA_OVERLAY
//    vignette texture on top. Both are @Redirect-ed to no-ops so neither can
//    paint over the Origin backdrop; each is the only call of its shape in
//    render() (bytecode-verified: one PanoramaRenderer.render, one 10-arg blit).
//  - No LogoRenderer class in this era (it's 1.19.4+): the "Minecraft" wordmark
//    is TWO blitOutlineBlack(int,int,BiConsumer) calls -- one per branch of the
//    "Minceraft" easter-egg check, exactly one executing per frame -- plus the
//    8-arg "Java Edition" badge blit right after. One @Redirect wraps both
//    blitOutlineBlack invocations (same shape) and draws the Origin wordmark
//    instead; a second suppresses the edition badge (the only 8-arg blit in
//    render()).
//  - No SplashRenderer class in this era: the splash is a String field drawn
//    via the one drawCenteredString call in render() -- redirected to a no-op
//    (its pose push/rotate/pop around the call stays balanced). 1.17.1 draws
//    BOTH the version line AND the copyright line via drawString in render()
//    (two calls, identical shape; 1.18.2 made copyright a PlainTextButton
//    widget instead) -- one @Redirect on that shape suppresses both.
//  - After init(), hide the two ImageButtons (language, accessibility) via
//    visible/active -- the only ImageButtons; the real options are plain
//    Button, left intact. (No copyright widget this era -- see drawString.)
// priority 2000 (default 1000): if another mod also modifies TitleScreen (e.g.
// redirects the logo/background), Origin's re-skin wins the conflict. Scoped to
// the UI mixins only — perf/render mixins stay at default so Sodium/Iris
// application ordering is left undisturbed.
@Mixin(value = TitleScreen.class, priority = 2000)
public class TitleScreenMixin {

	// SETTINGS > General > Main Menu Style. "Vanilla" turns the entire re-skin
	// off — every inject below no-ops so the stock title screen shows through.
	private static boolean originclient$origin() {
		return Mods.mode(Mods.GENERAL_ID, "mainMenuStyle").equals("Origin");
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void originclient$background(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!originclient$origin()) {
			return;
		}
		Gfx g = new Gfx(poseStack);
		OriginScreenRenderer.renderTitleBackground(g);
		// The website's mouse-follow spotlight: over the rings, under the
		// widgets (this HEAD inject runs before the widget pass). Blooms while
		// any visible button is hovered, like the site's hover targets.
		boolean hoveringClickable = false;
		Screen self = (Screen) (Object) this;
		for (GuiEventListener child : self.children()) {
			if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
				hoveringClickable = true;
				break;
			}
		}
		OriginScreenRenderer.renderTitleCursorGlow(g, mouseX, mouseY, hoveringClickable);
		// Account chip (player head + username) in the top-left frame corner.
		OriginScreenRenderer.renderTitleAccountChip(g);
	}

	// Both suppressions are gated on the renderer's health: if the Origin
	// backdrop ever fails (fail-soft contract), vanilla's panorama comes back
	// instead of leaving a black screen.
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
	private void originclient$suppressPanorama(PanoramaRenderer instance, float partialTick, float alpha) {
		if (!originclient$origin() || !OriginScreenRenderer.isActive()) {
			instance.render(partialTick, alpha);
		}
	}

	// The PANORAMA_OVERLAY vignette blit right after the panorama — the only
	// 10-arg blit in render().
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIFFIIII)V"))
	private void originclient$suppressOverlay(PoseStack poseStack, int x, int y, int w, int h,
											  float u, float v, int uW, int vH, int texW, int texH) {
		if (!originclient$origin() || !OriginScreenRenderer.isActive()) {
			GuiComponent.blit(poseStack, x, y, w, h, u, v, uW, vH, texW, texH);
		}
	}

	// The vanilla logo: both blitOutlineBlack calls (normal + "Minceraft"
	// easter-egg branch) share one shape, so this redirect wraps both; only one
	// runs per frame, so the wordmark draws exactly once. The enclosing render()
	// args are appended to the handler (Mixin arg capture) to reach the frame's
	// PoseStack — blitOutlineBlack itself doesn't carry one.
	// Fail-soft: if the wordmark can't draw (or the style is Vanilla), restore
	// vanilla's own logo so the title never loses its centerpiece.
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;blitOutlineBlack(IILjava/util/function/BiConsumer;)V"))
	private void originclient$logo(TitleScreen instance, int x, int y, java.util.function.BiConsumer<Integer, Integer> draw,
								   PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (!originclient$origin() || !OriginScreenRenderer.renderTitleWordmark(new Gfx(poseStack))) {
			instance.blitOutlineBlack(x, y, draw);
		}
	}

	// The "Java Edition" badge blit straight after the logo — the only 8-arg
	// blit in render(). Gated like the logo so it returns with vanilla's logo
	// whenever the wordmark path is off or unhealthy.
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIFFIIII)V"))
	private void originclient$suppressEdition(PoseStack poseStack, int x, int y, float u, float v,
											  int w, int h, int texW, int texH) {
		if (!originclient$origin() || !OriginScreenRenderer.isActive()) {
			GuiComponent.blit(poseStack, x, y, u, v, w, h, texW, texH);
		}
	}

	// Remove the yellow splash text (Origin style only). Pre-1.20 there is no
	// SplashRenderer — the splash String is drawn by the single
	// drawCenteredString in render().
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;drawCenteredString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void originclient$noSplash(PoseStack poseStack, Font font, String text, int x, int y, int color) {
		if (!originclient$origin()) {
			GuiComponent.drawCenteredString(poseStack, font, text, x, y, color);
		}
	}

	// Remove the bottom version line AND the copyright line -- 1.17.1 draws both
	// via drawString in render() (two calls, same shape). @Redirect binds every
	// matching invocation, so this one handler suppresses both on the Origin
	// menu and leaves both drawing on the Vanilla menu.
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;drawString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void originclient$noVersion(PoseStack poseStack, Font font, String text, int x, int y, int color) {
		if (!originclient$origin()) {
			GuiComponent.drawString(poseStack, font, text, x, y, color);
		}
	}

	// Hide the language + accessibility icons (ImageButton). visible=false stops
	// both rendering and clicks; re-run on every (re)init so it survives window
	// resizes. The copyright line is NOT a widget in 1.17.1 (it is a drawString
	// in render(), suppressed by originclient$noVersion) -- nothing to strip here.
	@Inject(method = "init", at = @At("TAIL"))
	private void originclient$stripExtraButtons(CallbackInfo ci) {
		if (!originclient$origin()) {
			return;
		}
		Screen self = (Screen) (Object) this;
		for (GuiEventListener child : self.children()) {
			if (child instanceof ImageButton widget) {
				widget.visible = false;
				widget.active = false;
			}
		}
	}
}
