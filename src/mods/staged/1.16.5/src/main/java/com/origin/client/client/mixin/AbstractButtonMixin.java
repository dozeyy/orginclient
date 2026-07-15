package com.origin.client.client.mixin;

import com.origin.client.client.gui.OriginButtonRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.origin.client.client.gui.Gfx;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Restyles every button in the Origin look, on every screen: cancels the
// vanilla button drawing and draws the Origin style instead. Restyling
// happens in-place (no widgets added/removed) -- buttons keep their
// positions, actions, and clicks; only the vanilla paint is replaced.
//
// 1.18.2 era: the widget draw method is renderButton, declared ONLY on
// AbstractWidget (javap-verified — AbstractButton declares no override, so a
// mixin there would have nothing to bind to). This mixin therefore targets
// AbstractWidget.renderButton and scopes itself with an instanceof check.
// Coverage matches the 1.19.4+ modules' AbstractButton hook: plain Button
// funnels through here (its own renderButton is just super + tooltip,
// bytecode-verified) and CycleButton has no override at all; subclasses that
// repaint WITHOUT calling super (ImageButton, PlainTextButton, Checkbox,
// LockIconButton via the renderer's own skip) keep vanilla rendering until
// each gets its own styled pass. Sliders are not AbstractButtons — they get
// the same treatment in AbstractSliderButtonMixin. Disabled buttons
// (active=false) render the dimmed Origin style and skip hover.
// priority 2000: Origin's widget restyle wins over other UI mods.
@Mixin(value = AbstractWidget.class, priority = 2000)
public class AbstractButtonMixin {

	@Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
	private void originclient$originStyle(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!((Object) this instanceof AbstractButton)) {
			return;
		}
		AbstractButton button = (AbstractButton) (Object) this;
		// Only cancel vanilla when Origin actually drew -- if the styled draw
		// ever fails (e.g. on a different game version), vanilla buttons return.
		if (OriginButtonRenderer.render(new Gfx(poseStack), button)) {
			ci.cancel();
		}
	}
}
