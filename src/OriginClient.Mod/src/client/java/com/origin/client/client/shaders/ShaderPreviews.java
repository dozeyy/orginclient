package com.origin.client.client.shaders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Lazy per-pack preview thumbnails from Modrinth (featured gallery image, else
// the project icon). Fetched + decoded on a daemon thread, uploaded to GL on
// the render thread, then cached. Only visible rows call get(), so at most a
// handful load at once. Fully guarded — a missing/undecodable image just shows
// the fallback tile.
public final class ShaderPreviews {
	private ShaderPreviews() {
	}

	public record Preview(ResourceLocation id, int w, int h) {
	}

	private static final Map<String, Preview> READY = new ConcurrentHashMap<>();
	private static final Set<String> INFLIGHT = ConcurrentHashMap.newKeySet();
	private static final Gson GSON = new Gson();
	private static final String UA = "OriginClient (will@willhenry.me)";

	/** The cached preview, kicking off a one-time async load on first miss. */
	public static Preview get(String slug) {
		Preview p = READY.get(slug);
		if (p != null) {
			return p;
		}
		if (INFLIGHT.add(slug)) {
			Thread t = new Thread(() -> load(slug), "origin-shader-preview-" + slug);
			t.setDaemon(true);
			t.start();
		}
		return null;
	}

	private static void load(String slug) {
		try {
			JsonObject proj = getJson("https://api.modrinth.com/v2/project/" + slug);
			String url = null;
			JsonArray gallery = proj.getAsJsonArray("gallery");
			if (gallery != null && !gallery.isEmpty()) {
				for (var el : gallery) {
					JsonObject gi = el.getAsJsonObject();
					if (gi.has("featured") && gi.get("featured").getAsBoolean()) {
						url = gi.get("url").getAsString();
						break;
					}
				}
				if (url == null) {
					url = gallery.get(0).getAsJsonObject().get("url").getAsString();
				}
			}
			if (url == null && proj.has("icon_url") && !proj.get("icon_url").isJsonNull()) {
				url = proj.get("icon_url").getAsString();
			}
			if (url == null) {
				return;
			}
			byte[] bytes = getBytes(url);
			NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
			int w = img.getWidth(), h = img.getHeight();
			Minecraft.getInstance().execute(() -> {
				try {
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
							"originclient", "shader_preview/" + slug.replaceAll("[^a-z0-9/._-]", "_"));
					DynamicTexture tex = new DynamicTexture(img);
					tex.setFilter(true, false);
					Minecraft.getInstance().getTextureManager().register(id, tex);
					READY.put(slug, new Preview(id, w, h));
				} catch (Throwable t) {
					img.close();
				}
			});
		} catch (Throwable t) {
			// leave unavailable; INFLIGHT keeps it from retrying in a tight loop
			com.origin.client.OriginClient.LOGGER.debug("preview load failed for {}", slug, t);
		}
	}

	private static JsonObject getJson(String url) throws Exception {
		try (InputStream in = open(url).getInputStream()) {
			return GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
		}
	}

	private static byte[] getBytes(String url) throws Exception {
		try (InputStream in = open(url).getInputStream()) {
			return in.readAllBytes();
		}
	}

	private static HttpURLConnection open(String url) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
		conn.setRequestProperty("User-Agent", UA);
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(30000);
		conn.setInstanceFollowRedirects(true);
		return conn;
	}
}
