package com.origin.client.client.mixin;

import com.origin.client.client.gui.OriginModsListScreen;
import com.origin.client.client.mods.Mods;
import com.origin.client.client.render.OriginScreenRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// Re-skins the main menu: the Origin background replaces the panorama, the
// "ORIGIN" wordmark replaces the vanilla "Minecraft" logo, the splash/version
// text + copyright line are hidden, and the button column becomes the Origin
// stacked layout -- Singleplayer / Multiplayer / Realms / Mods, then the
// Options/Quit half-row flanked by the language/accessibility icon squares
// (2026-07-20 redesign from Will's reference shots; the icons used to be
// hidden entirely — button faces come from AbstractButtonMixin's
// OriginButtonRenderer restyle, same as every other menu).
//
// Strategy (all targets confirmed via javap against the mapped 1.21.10 jar):
//  - Draw the Origin background at render() HEAD -- render() is guaranteed to
//    run every frame, so the background is always painted, under the logo/
//    buttons that draw afterward.
//  - Cancel both renderPanorama and renderBackground so vanilla's own backdrop
//    (whichever path render() uses) never paints over ours. Both are
//    background-only on TitleScreen; widgets draw in the separate widget pass.
//  - Redirect renderLogo -> Origin wordmark; no-op the splash + version draws.
//  - After init(), hide the PlainTextButton (copyright), insert the Mods row
//    (opens the read-only OriginModsListScreen -- settings stay in-game via
//    Right Shift), and shift the half-row + icons down one 24px pitch.
// priority 2000 (default 1000): if another mod also modifies TitleScreen (e.g.
// redirects the logo/background), Origin's re-skin wins the conflict. Scoped to
// the UI mixins only — perf/render mixins stay at default so Sodium/Iris
// application ordering is left undisturbed.
@Mixin(value = TitleScreen.class, priority = 2000)
public abstract class TitleScreenMixin extends Screen {

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	// SETTINGS > General > Main Menu Style. "Vanilla" turns the entire re-skin
	// off — every inject below no-ops so the stock title screen shows through.
	private static boolean originclient$origin() {
		return Mods.mode(Mods.GENERAL_ID, "mainMenuStyle").equals("Origin");
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void originclient$background(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!originclient$origin()) {
			return;
		}
		OriginScreenRenderer.renderTitleBackground(guiGraphics);
		// The website's mouse-follow spotlight: over the rings, under the
		// widgets (this HEAD inject runs before the widget pass). Blooms while
		// any visible button is hovered, like the site's hover targets.
		boolean hoveringClickable = false;
		Screen self = (Screen) (Object) this;
		for (GuiEventListener child : self.children()) {
			if (child instanceof AbstractWidget widget && widget.visible && widget.isHovered()) {
				hoveringClickable = true;
				break;
			}
		}
		OriginScreenRenderer.renderTitleCursorGlow(guiGraphics, mouseX, mouseY, hoveringClickable);
		// Account chip (player head + username) in the top-left frame corner.
		OriginScreenRenderer.renderTitleAccountChip(guiGraphics);
	}

	// Both suppressions are gated on the renderer's health: if the Origin
	// backdrop ever fails (fail-soft contract), vanilla's panorama comes back
	// instead of leaving a black screen.
	@Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
	private void originclient$suppressPanorama(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
		if (originclient$origin() && OriginScreenRenderer.isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void originclient$suppressBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (originclient$origin() && OriginScreenRenderer.isActive()) {
			ci.cancel();
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
	private void originclient$logo(LogoRenderer instance, GuiGraphics guiGraphics, int screenWidth, float alpha) {
		// Fail-soft: if the wordmark can't draw (or the style is Vanilla),
		// restore vanilla's own logo so the title never loses its centerpiece.
		if (!originclient$origin() || !OriginScreenRenderer.renderTitleWordmark(guiGraphics)) {
			instance.renderLogo(guiGraphics, screenWidth, alpha);
		}
	}

	// Remove the yellow splash text (Origin style only).
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;F)V"))
	private void originclient$noSplash(SplashRenderer instance, GuiGraphics guiGraphics, int screenWidth, Font font, float color) {
		if (!originclient$origin()) {
			instance.render(guiGraphics, screenWidth, font, color);
		}
	}

	// Remove the bottom version line (the only drawString in render()).
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void originclient$noVersion(GuiGraphics instance, Font font, String text, int x, int y, int color) {
		if (!originclient$origin()) {
			instance.drawString(font, text, x, y, color);
		}
	}

	// The Origin stacked layout (Will's reference shots): keep vanilla's
	// Singleplayer/Multiplayer/Realms rows where they are, insert a "Mods" row
	// beneath them, and push the Options/Quit half-row + the two icon squares
	// down one 24px pitch to make room. Copyright (PlainTextButton) stays
	// hidden. Widgets are matched by TYPE and WIDTH (200 = stack row, 98 =
	// half row, SpriteIconButton = icon square) rather than translation key,
	// so the match is locale-proof; anchoring on the first stack row's own y
	// tracks any per-version layout anchor instead of hardcoding one. Re-runs
	// on every (re)init, and init() rebuilds widgets from scratch, so resizes
	// never double the Mods button. An unexpected widget census (another mod
	// rebuilt the menu) leaves vanilla's arrangement alone — fail-soft.
	@Inject(method = "init", at = @At("TAIL"))
	private void originclient$originLayout(CallbackInfo ci) {
		if (!originclient$origin()) {
			return;
		}
		List<AbstractWidget> stack = new ArrayList<>();
		List<AbstractWidget> halfRow = new ArrayList<>();
		List<AbstractWidget> icons = new ArrayList<>();
		for (GuiEventListener child : this.children()) {
			if (child instanceof PlainTextButton copyright) {
				copyright.visible = false;
				copyright.active = false;
			} else if (child instanceof SpriteIconButton icon) {
				icons.add(icon);
			} else if (child instanceof AbstractWidget widget && widget.getWidth() == 200) {
				stack.add(widget);
			} else if (child instanceof AbstractWidget widget && widget.getWidth() == 98) {
				halfRow.add(widget);
			}
		}
		if (stack.isEmpty()) {
			return;
		}
		int modsY = stack.get(0).getY() + 24 * stack.size();
		int rowY = modsY + 24 + 12; // vanilla's own 12px gap before the half row
		for (AbstractWidget widget : halfRow) {
			widget.setY(rowY);
		}
		for (AbstractWidget widget : icons) {
			widget.setY(rowY);
		}
		Screen self = (Screen) (Object) this;
		addRenderableWidget(Button.builder(Component.translatable("originclient.menu.mods"),
						b -> this.minecraft.setScreen(new OriginModsListScreen(self)))
				.bounds(this.width / 2 - 100, modsY, 200, 20)
				.build());
	}
}
