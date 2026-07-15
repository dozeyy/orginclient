package com.origin.client.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// LevelRenderer.countRenderedChunks() is protected on 1.16.5 (no public
// getter), so the "C:" rendered-chunk count in the Coords HUD reaches it via
// this @Invoker. Caller wraps the call in catch(Throwable) — fail-soft.
@Mixin(LevelRenderer.class)
public interface LevelRendererCountAccessor {
	@Invoker("countRenderedChunks")
	int originclient$countRenderedChunks();
}
