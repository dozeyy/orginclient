package com.origin.client.client.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Read access to the slider's fill fraction for AbstractWidgetMixin's Origin
// restyle. `value` is a protected field declared directly on
// AbstractSliderButton (javap-confirmed on 1.19.3) -- a declared-field
// accessor, the safe case. On 1.19.3 sliders render through
// AbstractWidget.renderButton (no own override), so the restyle lives in
// AbstractWidgetMixin and only the field read needs this class.
@Mixin(AbstractSliderButton.class)
public interface AbstractSliderButtonAccessor {

	@Accessor("value")
	double originclient$getValue();
}
