package com.origin.client.client.loading;

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

// Draws the custom Origin loading screen: charcoal background, the four
// pre-rendered orbital-ring textures (each rotating at its own speed, mirroring
// the launcher's OriginBackground), a subtle grain tile, the "ORIGIN" wordmark
// in Minecraft's own font (kept vanilla, per the settled font decision), and a
// clean progress bar. Everything is centered off the live GUI-scaled window
// size every frame, so it stays dead-center at any window size / fullscreen.
//
// Texture/blit/DynamicTexture usage here mirrors what already compiled + ran
// clean in the M3 pass, so those APIs are confirmed. Textures are loaded via
// the classloader (not the resource manager) so this is safe to call during
// the earliest loading overlay, before/while resources are (re)loading.
public final class OriginLoadingRenderer {
	private static final Gson GSON = new Gson();
	private static final int TEX = 768;
	private static final int BG_COLOR = OriginTheme.BG;

	private static boolean loaded = false;
	private static boolean loadFailed = false;
	private static final List<Ring> rings = new ArrayList<>();
	private static ResourceLocation grainId;

	// Wordmark texture (baked "Origin" in the website's Inter font, so it shows
	// instantly instead of Minecraft's not-yet-loaded font rendering tofu boxes
	// during the first resource load). Null -> fall back to vanilla drawString.
	private static ResourceLocation wordmarkId;
	private static int wmTexW, wmTexH, wmInkX, wmInkY, wmInkW, wmInkH;

	private record Ring(ResourceLocation texture, double widthFrac, float opacity,
						double angle0, double periodSeconds, boolean reverse) {
	}

	private OriginLoadingRenderer() {
	}

	public static void render(GuiGraphics guiGraphics, float progress) {
		ensureLoaded();

		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();

		// Opaque charcoal base — covers whatever vanilla drew underneath.
		guiGraphics.fill(0, 0, w, h, BG_COLOR);

		if (!loadFailed) {
			drawRings(guiGraphics, w, h);
			drawGrain(guiGraphics, w, h);
		}

		// Wordmark ink centered on the screen center; bar sits just below it.
		int wordmarkBottom = drawWordmark(guiGraphics, w, h);
		drawProgressBar(guiGraphics, w, h, wordmarkBottom, Math.max(0f, Math.min(1f, progress)));
	}

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

	/** Draws the wordmark with its ink box centered on the screen center. Returns the ink bottom (screen Y). */
	private static int drawWordmark(GuiGraphics guiGraphics, int w, int h) {
		double cx = w / 2.0;
		double cy = h / 2.0;

		if (wordmarkId != null) {
			// Scale so the ink (letters, excluding the glow padding) is a set fraction of screen height.
			float scale = (float) (h * 0.15 / wmInkH);
			double inkCenterX = (wmInkX + wmInkW / 2.0) * scale;
			double inkCenterY = (wmInkY + wmInkH / 2.0) * scale;
			double topLeftX = cx - inkCenterX;
			double topLeftY = cy - inkCenterY;

			PoseStack pose = guiGraphics.pose();
			pose.pushPose();
			pose.translate(topLeftX, topLeftY, 0);
			pose.scale(scale, scale, 1f);
			RenderSystem.enableBlend();
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			guiGraphics.blit(wordmarkId, 0, 0, 0, 0, wmTexW, wmTexH, wmTexW, wmTexH);
			pose.popPose();

			return (int) Math.round(cy + (wmInkH * scale) / 2.0);
		}

		// Fallback (texture missing): vanilla font, centered.
		Font font = Minecraft.getInstance().font;
		String mark = "ORIGIN";
		float scale = 4.0f;
		PoseStack pose = guiGraphics.pose();
		pose.pushPose();
		pose.translate(cx, cy, 0);
		pose.scale(scale, scale, 1f);
		int textW = font.width(mark);
		guiGraphics.drawString(font, mark, -textW / 2, -4, OriginTheme.TEXT, false);
		pose.popPose();
		return (int) Math.round(cy + 5 * scale);
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
				ResourceLocation id = registerTexture(mc, "loading_ring_" + index,
						"/assets/originclient/textures/ui/ring-" + index + ".png");
				rings.add(new Ring(id,
						r.get("widthFrac").getAsDouble(),
						r.get("opacity").getAsFloat(),
						r.get("angle0").getAsDouble(),
						r.get("periodSeconds").getAsDouble(),
						r.get("reverse").getAsBoolean()));
			}
			grainId = registerTexture(mc, "loading_grain", "/assets/originclient/textures/ui/grain.png");
		} catch (Exception e) {
			loadFailed = true;
			com.origin.client.OriginClient.LOGGER.warn("Origin loading-screen textures failed to load; falling back to plain background", e);
		}

		// Wordmark loads separately: if it fails, fall back to vanilla-font text
		// without disabling the ring/grain background above.
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
			wordmarkId = registerTexture(mc, "loading_wordmark", "/assets/originclient/textures/ui/wordmark.png");
		} catch (Exception e) {
			wordmarkId = null;
			com.origin.client.OriginClient.LOGGER.warn("Origin loading-screen wordmark failed to load; using vanilla font", e);
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
		InputStream in = OriginLoadingRenderer.class.getResourceAsStream(classpathResource);
		if (in == null) {
			throw new java.io.FileNotFoundException("Missing Origin loading asset: " + classpathResource);
		}
		return in;
	}
}
