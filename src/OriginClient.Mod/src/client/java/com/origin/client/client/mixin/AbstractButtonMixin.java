package com.origin.client.client.mixin;

import com.origin.client.client.gui.OriginButtonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Restyles buttons in the Origin look, but only on the main menu: cancels the
// vanilla button drawing and draws the Origin style instead. Gated to
// TitleScreen so buttons on every other screen stay vanilla, and restyling
// happens in-place (no widgets added/removed) -- the buttons keep their
// positions, actions, and clicks. Invisible buttons (the language/accessibility/
// copyright ones TitleScreenMixin hides) never reach renderWidget, so they're
// naturally excluded.
@Mixin(AbstractButton.class)
public class AbstractButtonMixin {

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void originclient$originStyle(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (Minecraft.getInstance().screen instanceof TitleScreen) {
			OriginButtonRenderer.render(guiGraphics, (AbstractButton) (Object) this);
			ci.cancel();
		}
	}
}
