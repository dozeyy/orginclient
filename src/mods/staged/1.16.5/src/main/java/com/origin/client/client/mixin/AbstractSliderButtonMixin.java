package com.origin.client.client.mixin;

import com.origin.client.client.gui.OriginButtonRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.origin.client.client.gui.Gfx;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Restyles every slider (FOV, volumes, mouse sensitivity, ...) in the Origin
// look, in place: vanilla's drag/click/keyboard logic is untouched, only the
// drawing is replaced.
//
// 1.18.2 era: AbstractSliderButton declares NO renderButton of its own
// (javap-verified — sliders draw through AbstractWidget.renderButton plus a
// renderBg hook), so the inject targets AbstractWidget.renderButton and
// scopes itself with an instanceof check; cancelling at HEAD also skips the
// renderBg handle pass. The protected `value` field can't be @Shadow-ed from
// an AbstractWidget mixin, so it's read through AbstractSliderButtonAccessor.
// priority 2000: Origin's slider restyle wins over other UI mods.
@Mixin(value = AbstractWidget.class, priority = 2000)
public class AbstractSliderButtonMixin {

	@Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
	private void originclient$originStyle(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!((Object) this instanceof AbstractSliderButton)) {
			return;
		}
		AbstractSliderButton slider = (AbstractSliderButton) (Object) this;
		double value = ((AbstractSliderButtonAccessor) slider).originclient$getValue();
		// Only cancel vanilla when Origin actually drew (fail-soft contract).
		if (OriginButtonRenderer.renderSlider(new Gfx(poseStack), slider, value)) {
			ci.cancel();
		}
	}
}
