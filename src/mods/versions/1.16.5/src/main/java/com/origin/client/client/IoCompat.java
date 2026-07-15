package com.origin.client.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Java 8 has no InputStream.readAllBytes() (added in Java 9). This reads a whole
// stream into a byte[] via a buffer loop — the drop-in the render/config/shader
// code uses when slurping bundled JSON and preview images on 1.16.5.
public final class IoCompat {
	private IoCompat() {
	}

	public static byte[] readAllBytes(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) != -1) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	// gson 2.8.0 (bundled with MC 1.16.5) has no JsonObject.keySet() — that
	// arrived in gson 2.8.1. Derive the key set from entrySet() so the config/
	// UI JSON walks the same way it does on newer versions.
	public static java.util.Set<String> keys(com.google.gson.JsonObject obj) {
		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
			out.add(e.getKey());
		}
		return out;
	}
}
