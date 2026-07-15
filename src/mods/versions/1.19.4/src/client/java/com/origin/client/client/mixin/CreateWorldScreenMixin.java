package com.origin.client.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.origin.client.client.render.OriginScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// CreateWorldScreen blits a footer-separator line above its Create/Cancel
// buttons, matching the header-separator TabNavigationBarMixin already
// suppresses. On an Origin scene it reads as a stray vanilla bar over the
// rings, so it's skipped the same way. Fail-soft: if Origin rendering is
// unhealthy, the vanilla line returns. Pre-1.20 the draw is the inherited
// GuiComponent STATIC blit (owner = CreateWorldScreen in the bytecode) — the
// only blit of that shape in render() (bytecode-verified).
@Mixin(value = CreateWorldScreen.class, priority = 2000)
public class CreateWorldScreenMixin {

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIFFIIII)V"))
	private void originclient$noFooterSeparator(PoseStack poseStack, int x, int y,
												float u, float v, int width, int height, int texWidth, int texHeight) {
		if (Minecraft.getInstance().level == null && OriginScreenRenderer.isActive()) {
			return; // skip the footer-separator line
		}
		GuiComponent.blit(poseStack, x, y, u, v, width, height, texWidth, texHeight);
	}
}
