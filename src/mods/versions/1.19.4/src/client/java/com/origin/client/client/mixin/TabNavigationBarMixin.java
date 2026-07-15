package com.origin.client.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.origin.client.client.render.OriginScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The tab header bar (a) fills an opaque black strip across the top, then
// (b) blits a header-separator line texture, before the tabs draw. On an
// Origin scene both read as vanilla bars over the rings, so both are
// suppressed (the restyled tabs from TabButtonMixin sit directly on the
// Origin backdrop instead). Fail-soft: if Origin rendering is unhealthy,
// both vanilla draws return. Pre-1.20 these are the inherited GuiComponent
// STATICS (owner = TabNavigationBar in the bytecode), so the redirect
// handlers take no instance and restore vanilla via GuiComponent directly.
@Mixin(value = TabNavigationBar.class, priority = 2000)
public class TabNavigationBarMixin {

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar;fill(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V"))
	private void originclient$noBar(PoseStack poseStack, int x1, int y1, int x2, int y2, int color) {
		if (Minecraft.getInstance().level == null && OriginScreenRenderer.isActive()) {
			return; // skip the black tab-bar fill
		}
		GuiComponent.fill(poseStack, x1, y1, x2, y2, color);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIFFIIII)V"))
	private void originclient$noHeaderSeparator(PoseStack poseStack, int x, int y,
												float u, float v, int width, int height, int texWidth, int texHeight) {
		if (Minecraft.getInstance().level == null && OriginScreenRenderer.isActive()) {
			return; // skip the header-separator line
		}
		GuiComponent.blit(poseStack, x, y, u, v, width, height, texWidth, texHeight);
	}
}
