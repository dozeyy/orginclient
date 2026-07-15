package com.origin.client.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.origin.client.client.OriginClientMod;
import com.origin.client.client.mods.Mods;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	// 1.16.5 ONLY -- the post-effect pipeline leaves TEXTURING DISABLED, and this
	// era's GameRenderer.render() never turns it back on (bytecode-verified: it
	// calls RenderSystem.enableTexture() BEFORE PostChain.process, never after).
	// EffectInstance.clear() runs `_activeTexture(unit); _disableTexture();
	// _bindTexture(0);` for EVERY sampler, so once Motion Blur's chain has run,
	// texturing is off for the rest of the frame -- and Gui.render() (hotbar,
	// crosshair) draws AFTER the chain in the same frame, so every textured HUD
	// quad came out flat grey. Vanilla never trips this because nothing in normal
	// play loads a post effect; Origin's Motion Blur is the first to use it.
	// Re-enable right after the chain so the HUD textures normally. Unconditional
	// on purpose: it is a plain state restore that is correct for ANY active post
	// effect (another mod's included), and a no-op when none is loaded.
	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/PostChain;process(F)V", shift = At.Shift.AFTER))
	private void originclient$restoreTexturingAfterPostChain(CallbackInfo ci) {
		RenderSystem.enableTexture();
	}
	// Eased zoom progress + last-frame timestamp. getFov runs every FRAME, so
	// easing here (frame-rate independent, time-based) makes Smooth Zoom glide
	// instead of the old tick-side easing that only updated 20x/sec and looked
	// choppy at high FPS.
	@Unique private static double originclient$zoomAnim = 0;
	@Unique private static long originclient$lastNanos = 0;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void originclient$applyZoom(Camera camera, float partialTick, boolean usePerspective, CallbackInfoReturnable<Double> cir) {
		boolean active = Mods.on("zoom") && OriginClientMod.zoomActive;
		double target = active ? 1.0 : 0.0;

		long now = System.nanoTime();
		double dt = originclient$lastNanos == 0 ? 16.7 : Math.min(64.0, (now - originclient$lastNanos) / 1_000_000.0);
		originclient$lastNanos = now;

		if (Mods.on("zoom") && Mods.bool("zoom", "smoothZoom")) {
			double rate = 1.0 - Math.exp(-dt / 70.0);   // ~70ms time constant, per-frame
			originclient$zoomAnim += (target - originclient$zoomAnim) * rate;
			if (Math.abs(target - originclient$zoomAnim) < 0.0015) {
				originclient$zoomAnim = target;
			}
		} else {
			originclient$zoomAnim = target;
		}

		if (originclient$zoomAnim > 0.001) {
			double vanilla = cir.getReturnValue();
			double tfov = Mods.num("zoom", "fov") * OriginClientMod.zoomScrollFactor;
			tfov = Math.max(1.0, Math.min(vanilla, tfov));   // zoom only narrows the FOV
			cir.setReturnValue(vanilla + (tfov - vanilla) * originclient$zoomAnim);
		}
	}
}
