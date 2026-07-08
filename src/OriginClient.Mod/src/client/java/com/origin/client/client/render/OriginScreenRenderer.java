package com.origin.client.client.render;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.origin.client.client.theme.OriginTheme;

// Shared Origin screen rendering, used by both the loading screen
// (LoadingOverlayMixin) and the main menu (TitleScreenMixin): charcoal
// background, the pre-rendered orbital rings (mirroring the launcher's
// OriginBackground), subtle grain, and the "Origin" wordmark in the website's
// Inter font (baked to a texture so it shows instantly and carries no
// custom-glyph-rendering risk).
//
// Textures load via the classloader (not the resource manager), so this is
// safe during the earliest loading overlay while resources are still loading,
// and degrades gracefully if any asset fails rather than crashing.
public final class OriginScreenRenderer {
	private static final Gson GSON = new Gson();
	private static final int TEX = 768;
	private static final int BG_COLOR = OriginTheme.BG;

	private static boolean loaded = false;
	private static boolean ringsFailed = false;
	private static final List<Ring> rings = new ArrayList<>();
	private static ResourceLocation grainId;

	// Baked "Origin" wordmark (Inter). Null -> fall back to vanilla drawString.
	private static ResourceLocation wordmarkId;
	private static int wmTexW, wmTexH, wmInkX, wmInkY, wmInkW, wmInkH;

	private record Ring(ResourceLocation texture, double widthFrac, float opacity,
						double angle0, double periodSeconds, boolean reverse) {
	}

	private OriginScreenRenderer() {
	}

	// ---- Public entry points ----

	/** Loading screen: charcoal + grain + centered wordmark + progress bar. No rings. */
	public static void renderLoading(GuiGraphics guiGraphics, float progress) {
		ensureLoaded();
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();

		guiGraphics.fill(0, 0, w, h, BG_COLOR);
		if (!ringsFailed) {
			drawGrain(guiGraphics, w, h);
		}
		int wordmarkBottom = drawWordmark(guiGraphics, w / 2.0, h / 2.0, h * 0.15);
		drawProgressBar(guiGraphics, w, h, wordmarkBottom, Math.max(0f, Math.min(1f, progress)));
	}

	/** Main menu background: charcoal + rotating rings + grain (behind vanilla's logo/buttons). */
	public static void renderTitleBackground(GuiGraphics guiGraphics) {
		ensureLoaded();
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();

		guiGraphics.fill(0, 0, w, h, BG_COLOR);
		if (!ringsFailed) {
			drawRings(guiGraphics, w, h);
			drawGrain(guiGraphics, w, h);
		}
	}

	/** Main menu: draw the "Origin" wordmark where the vanilla Minecraft logo sits (top-center). */
	public static void renderTitleWordmark(GuiGraphics guiGraphics) {
		ensureLoaded();
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		drawWordmark(guiGraphics, w / 2.0, h * 0.18, h * 0.12);
	}

	// ---- Primitives ----

	private static void drawRings(GuiGraphics guiGraphics, int w, int h) {
		double cx = w / 2.0;
		double cy = h / 2.0;
		long now = System.currentTimeMillis();
		PoseStack pose = guiGraphics.pose();

		RenderSystem.enableBlend();
		for (Ring ring : rings) {
			double revs = (now / 1000.0) / ring.periodSeconds();
			double angle = ring.angle0() + (ring.reverse() ? -revs : revs) * 360.0;
			float scale = (float) (ring.widthFrac() * w * 1.1 / TEX); // *1.1: ellipse fills 0.9 of its square texture

			pose.pushPose();
			pose.translate(cx, cy, 0);
			pose.mulPose(Axis.ZP.rotationDegrees((float) angle));
			pose.scale(scale, scale, 1f);
			pose.translate(-TEX / 2f, -TEX / 2f, 0);
			RenderSystem.setShaderColor(1f, 1f, 1f, ring.opacity());
			guiGraphics.blit(ring.texture(), 0, 0, 0, 0, TEX, TEX, TEX, TEX);
			pose.popPose();
		}
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private static void drawGrain(GuiGraphics guiGraphics, int w, int h) {
		if (grainId == null) {
			return;
		}
		int tile = 128;
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1f, 1f, 1f, 0.028f);
		for (int y = 0; y < h; y += tile) {
			for (int x = 0; x < w; x += tile) {
				guiGraphics.blit(grainId, x, y, 0, 0, tile, tile, tile, tile);
			}
		}
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** Draws the wordmark with its ink box centered on (inkCenterX, inkCenterY), ink scaled to targetInkHeight. Returns ink bottom (screen Y). */
	private static int drawWordmark(GuiGraphics guiGraphics, double inkCenterX, double inkCenterY, double targetInkHeight) {
		if (wordmarkId != null) {
			float scale = (float) (targetInkHeight / wmInkH);
			double icx = (wmInkX + wmInkW / 2.0) * scale;
			double icy = (wmInkY + wmInkH / 2.0) * scale;

			PoseStack pose = guiGraphics.pose();
			pose.pushPose();
			pose.translate(inkCenterX - icx, inkCenterY - icy, 0);
			pose.scale(scale, scale, 1f);
			RenderSystem.enableBlend();
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			guiGraphics.blit(wordmarkId, 0, 0, 0, 0, wmTexW, wmTexH, wmTexW, wmTexH);
			pose.popPose();

			return (int) Math.round(inkCenterY + (wmInkH * scale) / 2.0);
		}

		// Fallback (texture missing): vanilla font, centered on the point.
		Font font = Minecraft.getInstance().font;
		String mark = "ORIGIN";
		float scale = 4.0f;
		PoseStack pose = guiGraphics.pose();
		pose.pushPose();
		pose.translate(inkCenterX, inkCenterY, 0);
		pose.scale(scale, scale, 1f);
		int textW = font.width(mark);
		guiGraphics.drawString(font, mark, -textW / 2, -4, OriginTheme.TEXT, false);
		pose.popPose();
		return (int) Math.round(inkCenterY + 5 * scale);
	}

	private static void drawProgressBar(GuiGraphics guiGraphics, int w, int h, int wordmarkBottom, float progress) {
		int barW = Math.max(120, (int) (w * 0.22));
		int barH = 3;
		int bx = (w - barW) / 2;
		int by = wordmarkBottom + Math.max(14, (int) (h * 0.05)); // right under the wordmark

		guiGraphics.fill(bx, by, bx + barW, by + barH, OriginTheme.STROKE);       // track
		int fillW = Math.round(barW * progress);
		if (fillW > 0) {
			guiGraphics.fill(bx - 1, by - 1, bx + fillW + 1, by + barH + 1, OriginTheme.ACCENT_GLOW); // soft glow
			guiGraphics.fill(bx, by, bx + fillW, by + barH, OriginTheme.ACCENT);  // fill
		}
	}

	// ---- Loading ----

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Minecraft mc = Minecraft.getInstance();
			JsonObject meta;
			try (InputStream in = open("/assets/originclient/textures/ui/rings.json")) {
				meta = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
			}
			var arr = meta.getAsJsonArray("rings");
			for (int i = 0; i < arr.size(); i++) {
				JsonObject r = arr.get(i).getAsJsonObject();
				int index = r.get("index").getAsInt();
				ResourceLocation id = registerTexture(mc, "origin_ring_" + index,
						"/assets/originclient/textures/ui/ring-" + index + ".png");
				rings.add(new Ring(id,
						r.get("widthFrac").getAsDouble(),
						r.get("opacity").getAsFloat(),
						r.get("angle0").getAsDouble(),
						r.get("periodSeconds").getAsDouble(),
						r.get("reverse").getAsBoolean()));
			}
			grainId = registerTexture(mc, "origin_grain", "/assets/originclient/textures/ui/grain.png");
		} catch (Exception e) {
			ringsFailed = true;
			com.origin.client.OriginClient.LOGGER.warn("Origin screen ring/grain textures failed to load; using plain background", e);
		}

		// Wordmark loads separately: a failure here falls back to vanilla-font
		// text without disabling the ring/grain background.
		try {
			Minecraft mc = Minecraft.getInstance();
			JsonObject wm;
			try (InputStream in = open("/assets/originclient/textures/ui/wordmark.json")) {
				wm = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
			}
			wmTexW = wm.get("width").getAsInt();
			wmTexH = wm.get("height").getAsInt();
			wmInkX = wm.get("inkX").getAsInt();
			wmInkY = wm.get("inkY").getAsInt();
			wmInkW = wm.get("inkWidth").getAsInt();
			wmInkH = wm.get("inkHeight").getAsInt();
			wordmarkId = registerTexture(mc, "origin_wordmark", "/assets/originclient/textures/ui/wordmark.png");
		} catch (Exception e) {
			wordmarkId = null;
			com.origin.client.OriginClient.LOGGER.warn("Origin wordmark failed to load; using vanilla font", e);
		}
	}

	private static ResourceLocation registerTexture(Minecraft mc, String name, String path) throws Exception {
		NativeImage image;
		try (InputStream in = open(path)) {
			image = NativeImage.read(in);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("originclient", name);
		DynamicTexture texture = new DynamicTexture(image);
		texture.setFilter(true, false); // GL_LINEAR, no mipmap
		mc.getTextureManager().register(id, texture);
		return id;
	}

	private static InputStream open(String classpathResource) throws Exception {
		InputStream in = OriginScreenRenderer.class.getResourceAsStream(classpathResource);
		if (in == null) {
			throw new java.io.FileNotFoundException("Missing Origin asset: " + classpathResource);
		}
		return in;
	}
}
