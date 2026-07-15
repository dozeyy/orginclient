package com.origin.client.client.mixin;

import com.origin.client.client.gui.OriginButtonRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.origin.client.client.gui.Gfx;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Restyles every button and slider in the Origin look, on every screen:
// cancels the vanilla widget drawing and draws the Origin style instead.
// Restyling happens in-place (no widgets added/removed) -- widgets keep
// their positions, actions, and clicks; only the paint is replaced.
//
// 1.19.3 is the renderButton era: AbstractButton and AbstractSliderButton
// declare NO render override (javap-verified) -- both draw through the one
// AbstractWidget.renderButton(PoseStack,int,int,float) base implementation,
// so this single mixin dispatches on the concrete type where the 1.19.4
// module has separate AbstractButton/AbstractSliderButton renderWidget
// mixins. Coverage is identical: subclasses with their OWN renderButton
// override (ImageButton, PlainTextButton, EditBox, Checkbox, ...) bypass
// this method entirely and keep vanilla rendering until each gets its own
// styled pass (Checkbox has one). Plain Button and CycleButton (the
// "Something: Value" toggles all over the Options menus) don't override it,
// so they're covered here. Dynamic labels with no baked Inter texture fall
// back to vanilla font inside the Origin shell, per the settled font
// decision. Disabled buttons (active=false, e.g. Telemetry Data) render the
// dimmed Origin style and skip hover. The slider's protected `value` is read
// via AbstractSliderButtonAccessor (declared-field @Accessor, the safe case).
// priority 2000: Origin's widget restyle wins over other UI mods.
@Mixin(value = AbstractWidget.class, priority = 2000)
public class AbstractWidgetMixin {

	@Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
	private void originclient$originStyle(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		// Only cancel vanilla when Origin actually drew -- if the styled draw
		// ever fails (e.g. on a different game version), vanilla widgets return.
		Object self = this;
		if (self instanceof AbstractSliderButton slider) {
			if (OriginButtonRenderer.renderSlider(new Gfx(poseStack), slider,
					((AbstractSliderButtonAccessor) slider).originclient$getValue())) {
				ci.cancel();
			}
		} else if (self instanceof AbstractButton button) {
			if (OriginButtonRenderer.render(new Gfx(poseStack), button)) {
				ci.cancel();
			}
		}
	}
}
