package com.origin.client.client.waypoints;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.origin.client.client.mods.Mods;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// Draws every enabled waypoint in the CURRENT dimension: a vertical see-through beam
// at the block, and a billboarded name + distance label above it. Hooked into
// Fabric's WorldRenderEvents.AFTER_TRANSLUCENT (registered in OriginClientMod) so it
// composes with the world. All camera-relative (the matrix stack is at camera space).
public final class WaypointRenderer {
	private WaypointRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		if (!Mods.on("waypoints")) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		if (pose == null) {
			return;
		}
		String dim = mc.level.dimension().location().toString();
		Camera cam = ctx.camera();
		Vec3 c = cam.getPosition();
		MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
		Font font = mc.font;

		for (Waypoints.Waypoint w : Waypoints.all()) {
			if (!w.enabled || !dim.equals(w.dimension)) {
				continue;
			}
			double dx = (w.x + 0.5) - c.x;
			double dz = (w.z + 0.5) - c.z;
			double dyBlock = w.y - c.y;
			double dist = Math.sqrt(dx * dx + (w.y + 1 - c.y) * (w.y + 1 - c.y) + dz * dz);

			if (w.showBeam) {
				drawBeam(pose, buf, (float) dx, (float) dz,
						(float) (w.y - 3 - c.y), (float) (w.y + 320 - c.y), w.color);
			}
			if (w.showText || w.showDistance) {
				String label = w.showText ? w.name : "";
				if (w.showDistance) {
					label += (label.isEmpty() ? "" : "  ") + (int) Math.round(dist) + "m";
				}
				drawLabel(pose, buf, cam, font, (float) dx, (float) (dyBlock + 1.6), (float) dz,
						label, w.color, (float) w.scale);
			}
		}
		buf.endBatch();
	}

	private static void drawBeam(PoseStack pose, MultiBufferSource buf, float x, float z,
								 float y0, float y1, int color) {
		VertexConsumer vc = buf.getBuffer(RenderType.lines());
		var last = pose.last();
		Matrix4f m = last.pose();
		int argb = (color >>> 24) == 0 ? (0xFF000000 | (color & 0xFFFFFF)) : color;
		vc.addVertex(m, x, y0, z).setColor(argb).setNormal(last, 0f, 1f, 0f);
		vc.addVertex(m, x, y1, z).setColor(argb).setNormal(last, 0f, 1f, 0f);
	}

	private static void drawLabel(PoseStack pose, MultiBufferSource buf, Camera cam, Font font,
								  float x, float y, float z, String text, int color, float scale) {
		if (text.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(cam.rotation());                 // billboard toward the camera
		float s = -0.025f * Math.max(0.3f, scale);
		pose.scale(s, s, s);
		Matrix4f m = pose.last().pose();
		int argb = (color >>> 24) == 0 ? (0xFF000000 | (color & 0xFFFFFF)) : color;
		float tx = -font.width(text) / 2f;
		font.drawInBatch(text, tx, 0f, argb, false, m, buf,
				Font.DisplayMode.SEE_THROUGH, 0x40000000, 0xF000F0);
		pose.popPose();
	}
}
