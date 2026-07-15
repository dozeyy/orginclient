package com.origin.client.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 1.19.2 has no Minecraft.getFps() (that accessor is 1.19.4+); the frame
// counter is the private static `fps` field — bridged here for the FPS HUD.
@Mixin(Minecraft.class)
public interface MinecraftFpsAccessor {

	@Accessor("fps")
	static int originclient$fps() {
		throw new AssertionError();
	}
}
