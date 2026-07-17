package com.origin.client.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.origin.client.client.gui.OriginColorPicker;
import com.origin.client.client.mods.Mods;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.HitboxFeatureRenderer;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hitbox mod on the 1.21.10 deferred-render ("submit") era.
//
// EntityRenderDispatcher.renderHitbox/renderHitboxes are GONE here: extraction
// happens in EntityRenderer.extractHitboxes (into HitboxesRenderState on the
// entity's render state), and drawing happens in HitboxFeatureRenderer.render,
// which walks SubmitNodeStorage.HitboxSubmit records {pose, entityRenderState,
// hitboxesRenderState}. The old renderHitbox mixin silently never applied
// (defaultRequire 0), which is why Hitboxes did nothing on this version.
//
// We own the whole pass: at render() HEAD, when the mod is on, walk the same
// submit list vanilla would, apply the Origin filters/styling, draw, cancel.
// Mod off = vanilla untouched. Like 1.21.5's state-based hook, the state has
// no live Entity, so Show Damaged / Show Hittable / Look Vector can't apply;
// type filter, distance, color, width and pattern all do.
@Mixin(HitboxFeatureRenderer.class)
public class HitboxMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
	private void originclient$renderStyled(SubmitNodeCollection collection,
			MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
		if (!Mods.on("hitboxes")) {
			return;
		}
		double max = Mods.num("hitboxes", "maxDistance");
		// Colour and width apply to EVERY entity, not just players -- matching
		// the 1.21.1 rework: player-only styling made Line Color and Line Width
		// silently do nothing on mobs, and the whole mod read as broken.
		int col = OriginColorPicker.liveColor("hitboxes", "lineColor");
		float r = ((col >> 16) & 0xFF) / 255f;
		float g = ((col >> 8) & 0xFF) / 255f;
		float b = (col & 0xFF) / 255f;
		float a = ((col >>> 24) & 0xFF) / 255f;
		if (a <= 0f) {
			a = 1f;
		}
		int passes = (int) Math.max(1, Math.round(Mods.num("hitboxes", "lineWidth")));
		String pattern = Mods.mode("hitboxes", "linePattern");
		VertexConsumer lines = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.lines());
		// The alpha guard above reassigns `a`, so the lambdas capture copies.
		float fr = r, fg = g, fb = b, fa = a;
		for (SubmitNodeStorage.HitboxSubmit submit : collection.getHitboxSubmits()) {
			var state = submit.entityRenderState();
			if (max > 0 && state.distanceToCameraSq > max * max) {
				continue;
			}
			if (!Mods.bool("hitboxes", categoryKey(state.entityType))) {
				continue;
			}
			// The submit carries a baked Matrix4f, not a PoseStack -- rebuild one
			// so the shared drawing helpers (and ThickLine) keep their Pose shape.
			PoseStack ps = new PoseStack();
			ps.mulPose(submit.pose());
			PoseStack.Pose pose = ps.last();
			for (Object o : submit.hitboxesRenderState().hitboxes()) {
				var hb = (net.minecraft.client.renderer.entity.state.HitboxRenderState) o;
				AABB box = new AABB(hb.x0(), hb.y0(), hb.z0(), hb.x1(), hb.y1(), hb.z1())
						.move(hb.offsetX(), hb.offsetY(), hb.offsetZ());
				// ONE thick line per edge, exactly like the block outline -- not a
				// stack of thin ones (more lines is not a thicker line; see
				// ThickLine). Every pattern gets width: patternSegments decides
				// where the pieces are, ThickLine decides how thick each piece is.
				// At width 1 there's nothing to thicken -- cheaper line path.
				if (passes > 1) {
					VertexConsumer q = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
					double t = (passes - 1) * 0.012;
					double ccx = (box.minX + box.maxX) / 2, ccy = (box.minY + box.maxY) / 2, ccz = (box.minZ + box.maxZ) / 2;
					forEachEdge(box, (ax, ay, az, bx, by, bz) ->
							patternSegments(ax, ay, az, bx, by, bz, pattern,
									(sx, sy, sz, ex, ey, ez) ->
											com.origin.client.client.render.ThickLine.edge(q, pose,
													sx, sy, sz, ex, ey, ez, ccx, ccy, ccz, t, fr, fg, fb, fa)));
				} else {
					forEachEdge(box, (ax, ay, az, bx, by, bz) ->
							patternSegments(ax, ay, az, bx, by, bz, pattern,
									(sx, sy, sz, ex, ey, ez) ->
											seg(lines, pose, sx, sy, sz, ex, ey, ez, fr, fg, fb, fa)));
				}
			}
		}
		ci.cancel();
	}

	/** Receives each of an AABB's 12 edges as a pair of points. */
	@FunctionalInterface
	private interface EdgeSink {
		void accept(double ax, double ay, double az, double bx, double by, double bz);
	}

	/** Walks an AABB's 12 edges. */
	private static void forEachEdge(AABB box, EdgeSink sink) {
		double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
		for (int yi = 0; yi < 2; yi++) {
			for (int zi = 0; zi < 2; zi++) {
				sink.accept(box.minX, ys[yi], zs[zi], box.maxX, ys[yi], zs[zi]);
			}
		}
		for (int xi = 0; xi < 2; xi++) {
			for (int zi = 0; zi < 2; zi++) {
				sink.accept(xs[xi], box.minY, zs[zi], xs[xi], box.maxY, zs[zi]);
			}
		}
		for (int xi = 0; xi < 2; xi++) {
			for (int yi = 0; yi < 2; yi++) {
				sink.accept(xs[xi], ys[yi], box.minZ, xs[xi], ys[yi], box.maxZ);
			}
		}
	}

	/**
	 * Cuts an edge into the pattern's visible pieces and hands each to `sink`.
	 * The pattern decides WHERE the pieces are, the renderer decides how thick
	 * they are -- which is what lets Dashed/Dotted honour Line Width.
	 */
	private static void patternSegments(double ax, double ay, double az,
										double bx, double by, double bz, String pattern, EdgeSink sink) {
		if (pattern == null || pattern.equals("Solid")) {
			sink.accept(ax, ay, az, bx, by, bz);
			return;
		}
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len <= 0) {
			return;
		}
		double dash = pattern.equals("Dotted") ? 0.08 : 0.22;
		double period = dash * 2;
		for (double s = 0; s < len; s += period) {
			double e = Math.min(len, s + dash);
			double t0 = s / len, t1 = e / len;
			sink.accept(ax + dx * t0, ay + dy * t0, az + dz * t0,
					ax + dx * t1, ay + dy * t1, az + dz * t1);
		}
	}

	private static void seg(VertexConsumer c, PoseStack.Pose pose, double ax, double ay, double az,
							double bx, double by, double bz, float r, float g, float b, float a) {
		float nx = (float) (bx - ax), ny = (float) (by - ay), nz = (float) (bz - az);
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 0) {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		c.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
		c.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
	}

	private static String categoryKey(net.minecraft.world.entity.EntityType<?> type) {
		if (type == net.minecraft.world.entity.EntityType.PLAYER) return "players";
		if (type == net.minecraft.world.entity.EntityType.ITEM) return "items";
		if (type == net.minecraft.world.entity.EntityType.ITEM_FRAME
				|| type == net.minecraft.world.entity.EntityType.GLOW_ITEM_FRAME) return "itemFrames";
		if (type == net.minecraft.world.entity.EntityType.WITHER_SKULL) return "witherSkulls";
		if (type == net.minecraft.world.entity.EntityType.FIREBALL
				|| type == net.minecraft.world.entity.EntityType.SMALL_FIREBALL
				|| type == net.minecraft.world.entity.EntityType.DRAGON_FIREBALL) return "fireballs";
		if (type == net.minecraft.world.entity.EntityType.FIREWORK_ROCKET) return "fireworks";
		if (type == net.minecraft.world.entity.EntityType.SNOWBALL) return "snowballs";
		if (type == net.minecraft.world.entity.EntityType.ARROW
				|| type == net.minecraft.world.entity.EntityType.SPECTRAL_ARROW) return "arrows";
		if (type == net.minecraft.world.entity.EntityType.EXPERIENCE_ORB) return "expOrbs";
		return "other";
	}
}
