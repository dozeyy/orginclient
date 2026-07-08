package com.origin.client.client.mixin;

import com.origin.client.client.render.OriginScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Origin background (charcoal + rings + grain) behind every out-of-world
// menu -- options, world select, server list, create world, etc. -- so the
// whole menu tree reads as one interface with the main menu.
//
// Gated on Minecraft.level == null: in-game screens (pause, in-game options)
// keep vanilla's blurred-world backdrop -- replacing that would hide the
// world the player is standing in, which is worse, not more consistent.
//
// Two hooks (both javap-confirmed against the mapped 1.21.1 Screen):
//  - renderBackground(GuiGraphics,int,int,float): the screen-level backdrop
//    (panorama + blur + menu texture). Cancelled and replaced wholesale.
//    TitleScreen overrides this method, so its own mixin path is unaffected.
//  - renderMenuBackgroundTexture(...): the static helper option/selection
//    LISTS use to tile their darker strip behind rows. Cancelled so lists sit
//    transparently on the Origin background instead of vanilla's texture.
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void originclient$originBackdrop(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (Minecraft.getInstance().level == null) {
			OriginScreenRenderer.renderTitleBackground(guiGraphics);
			ci.cancel();
		}
	}

	@Inject(method = "renderMenuBackgroundTexture", at = @At("HEAD"), cancellable = true)
	private static void originclient$noListTexture(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, float uOffset, float vOffset, int width, int height, CallbackInfo ci) {
		if (Minecraft.getInstance().level == null) {
			ci.cancel();
		}
	}
}
