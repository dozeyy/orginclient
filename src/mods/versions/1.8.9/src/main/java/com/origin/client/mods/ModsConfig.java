package com.origin.client.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The originclient-mods.json store — same file name, same shape and same
 * crash-safe write discipline as the modern modules:
 *
 *   { "mods": { "<id>": { "enabled": bool, "<key>": value, ... } },
 *     "hud":  { "<elementId>": [anchor, dx, dy, scale, bg] },
 *     "meta": { "panelBacking": bool } }
 *
 * Writes go to <file>.tmp then ATOMIC_MOVE over the real file, so a crash or
 * power loss mid-write can never leave a half-written (NUL-filled) config.
 * A fresh instance seeds from the bundled default-mods-config.json.
 */
public final class ModsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static JsonObject root;
    private static File file;

    private ModsConfig() {}

    public static synchronized JsonObject root() {
        if (root == null) load();
        return root;
    }

    public static JsonObject mods() { return ensureObject(root(), "mods"); }
    public static JsonObject hud()  { return ensureObject(root(), "hud"); }
    public static JsonObject meta() { return ensureObject(root(), "meta"); }

    public static JsonObject mod(String id) { return ensureObject(mods(), id); }

    private static JsonObject ensureObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) parent.add(key, new JsonObject());
        return parent.getAsJsonObject(key);
    }

    private static void load() {
        file = new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "originclient-mods.json");
        try {
            if (file.isFile()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                root = new JsonParser().parse(new String(bytes, UTF8)).getAsJsonObject();
                return;
            }
        } catch (Throwable t) {
            System.err.println("[OriginClient] mods config unreadable, reseeding: " + t);
        }
        root = seedDefaults();
        save();
    }

    private static JsonObject seedDefaults() {
        try {
            InputStream in = ModsConfig.class.getResourceAsStream("/assets/originclient/default-mods-config.json");
            if (in != null) {
                try {
                    return new JsonParser().parse(new InputStreamReader(in, UTF8)).getAsJsonObject();
                } finally {
                    in.close();
                }
            }
        } catch (Throwable t) {
            System.err.println("[OriginClient] default config seed failed: " + t);
        }
        return new JsonObject();
    }

    /** Eager save on every mutation — same policy as the modern modules. */
    public static synchronized void save() {
        if (root == null) return;
        try {
            File dir = file.getParentFile();
            if (!dir.isDirectory()) dir.mkdirs();
            Path tmp = new File(dir, file.getName() + ".tmp").toPath();
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp.toFile()), UTF8);
            try {
                GSON.toJson(root, w);
            } finally {
                w.close();
            }
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomicUnsupported) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            System.err.println("[OriginClient] mods config save failed: " + t);
        }
    }

    // ---- HUD positions: "<id>": [anchor, dx, dy, scale, bg] ----

    public static double[] hudArray(String id) {
        JsonObject hud = hud();
        if (hud.has(id) && hud.get(id).isJsonArray()) {
            JsonArray a = hud.getAsJsonArray(id);
            // Pre-bg files had 4 elements; accept >= 4.
            if (a.size() >= 4) {
                double[] out = new double[5];
                for (int i = 0; i < 4; i++) out[i] = a.get(i).getAsDouble();
                out[4] = a.size() >= 5 ? a.get(4).getAsDouble() : 0.0;
                return out;
            }
        }
        return null;
    }

    public static void setHudArray(String id, double anchor, double dx, double dy, double scale, double bg) {
        JsonArray a = new JsonArray();
        a.add(GSON.toJsonTree(anchor));
        a.add(GSON.toJsonTree(dx));
        a.add(GSON.toJsonTree(dy));
        a.add(GSON.toJsonTree(scale));
        a.add(GSON.toJsonTree(bg));
        hud().add(id, a);
        save();
    }

    public static void resetHud(String id) {
        hud().remove(id);
        save();
    }
}
