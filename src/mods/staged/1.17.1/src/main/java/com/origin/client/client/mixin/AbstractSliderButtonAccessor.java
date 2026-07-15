package com.origin.client.client.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// `value` is protected on AbstractSliderButton; AbstractSliderButtonMixin
// injects at the AbstractWidget level (where renderButton is declared on
// 1.18.2) so it can't @Shadow the subclass field — this accessor bridges it.
@Mixin(AbstractSliderButton.class)
public interface AbstractSliderButtonAccessor {

	@Accessor("value")
	double originclient$getValue();
}
