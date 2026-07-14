package com.origin.client.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Origin mod registry for the legacy (1.12.2) port. Ids, option keys and
 * value shapes match the modern modules exactly so originclient-mods.json is
 * format-compatible across every Origin version. The curated set here is the
 * subset implementable without mixins on legacy Forge; everything reads live
 * from ModsConfig each frame.
 */
public final class Mods {

    /** Synthetic ids for the SETTINGS tab pages (same as modern). */
    public static final String GENERAL_ID = "@general";

    public static final class Def {
        public final String id;
        public final String name;
        public final String description;
        public final List<ModOption> options;

        Def(String id, String name, String description, ModOption... options) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.options = Collections.unmodifiableList(Arrays.asList(options));
        }
    }

    private static final Map<String, Def> REGISTRY = new LinkedHashMap<String, Def>();

    public static final int[] PALETTE = com.origin.client.theme.OriginTheme.PALETTE;

    static {
        register(new Def("fps", "FPS", "Shows your frames per second.",
            ModOption.toggle("showBrackets", "Brackets", false),
            ModOption.toggle("reverseOrder", "FPS before number", true),
            ModOption.toggle("textShadow", "Text shadow", true),
            ModOption.toggle("showBackground", "Background", false),
            ModOption.slider("scale", "Scale", 0.5, 2.0, 0.05, 1.0),
            ModOption.color("color", "Color", 0xFFFEFEFE)));

        register(new Def("cps", "CPS", "Shows your clicks per second.",
            ModOption.toggle("rightClick", "Show right click", true),
            ModOption.toggle("reverseText", "CPS before number", false),
            ModOption.toggle("showText", "Show 'CPS' text", false),
            ModOption.toggle("showBackground", "Background", false),
            ModOption.slider("scale", "Scale", 0.5, 2.5, 0.05, 1.0),
            ModOption.color("color", "Color", 0xFFFFFFFF)));

        register(new Def("coords", "Coordinates", "Shows your position, direction and biome.",
            ModOption.dropdown("listMode", "Layout", "Horizontal", "Horizontal", "Vertical"),
            ModOption.toggle("direction", "Facing", false),
            ModOption.toggle("biome", "Biome", false),
            ModOption.toggle("decimal", "Decimals", false),
            ModOption.toggle("textShadow", "Text shadow", true),
            ModOption.toggle("showBackground", "Background", false),
            ModOption.slider("scale", "Scale", 0.5, 2.0, 0.05, 1.0)));

        register(new Def("keystrokes", "Keystrokes", "Shows WASD, mouse buttons and space bar.",
            ModOption.toggle("showMovement", "WASD keys", true),
            ModOption.toggle("showClicks", "Mouse buttons", true),
            ModOption.toggle("showSpace", "Space bar", true),
            ModOption.toggle("border", "Border", true),
            ModOption.toggle("textShadow", "Text shadow", true),
            ModOption.slider("scale", "Scale", 0.5, 2.0, 0.05, 1.1),
            ModOption.slider("boxSize", "Box size", 0.5, 1.5, 0.05, 0.9),
            ModOption.slider("keyFadeDelay", "Fade time (ms)", 0, 1000, 10, 350),
            ModOption.slider("spacebarThickness", "Space bar thickness", 1, 10, 1, 10),
            ModOption.slider("borderThickness", "Border thickness", 0, 4, 1, 0),
            ModOption.color("color", "Text", 0xFFFFFFFF),
            ModOption.color("textColorPressed", "Text (pressed)", 0xFF121212),
            ModOption.color("bgColor", "Box", 0xAE000000),
            ModOption.color("bgColorPressed", "Box (pressed)", 0x66FFFFFF),
            ModOption.color("borderColor", "Border color", 0x66000000)));

        register(new Def("potionhud", "Potion HUD", "Shows your active potion effects.",
            ModOption.toggle("uppercase", "Uppercase names", false),
            ModOption.toggle("blink", "Blink when expiring", true),
            ModOption.toggle("formattedDurations", "Formatted durations", true),
            ModOption.toggle("minimal", "Minimal (no names)", false),
            ModOption.toggle("textShadow", "Text shadow", true),
            ModOption.toggle("showBackground", "Background", false)));

        register(new Def("armorhud", "Armor HUD", "Shows your armor and held item durability.",
            ModOption.dropdown("listMode", "Layout", "Vertical", "Vertical", "Horizontal"),
            ModOption.dropdown("durabilityPos", "Durability", "Right", "Right", "Left", "Below", "Hidden"),
            ModOption.dropdown("damageDisplay", "Damage as", "Value", "Value", "Percent"),
            ModOption.color("textColor", "Text", 0xFFFFFFFF),
            ModOption.color("damageColor", "Low durability", 0xFFB80C0C),
            ModOption.toggle("showBackground", "Background", false)));

        register(new Def("serveraddress", "Server Address", "Shows the server you're playing on.",
            ModOption.toggle("serverIcon", "Server icon", true),
            ModOption.toggle("textShadow", "Text shadow", true),
            ModOption.toggle("showBackground", "Background", true)));

        register(new Def("togglesprint", "Toggle Sprint", "Sprint and sneak without holding the keys.",
            ModOption.dropdown("mode", "Sprint mode", "Toggle", "Toggle", "Hold"),
            ModOption.toggle("sneak", "Toggle sneak", false),
            ModOption.toggle("hud", "Show state on HUD", true)));

        register(new Def("zoom", "Zoom", "Zoom your view with a key (C by default).",
            ModOption.slider("fov", "Zoom FOV", 1, 60, 1, 30),
            ModOption.toggle("smoothZoom", "Smooth camera", true),
            ModOption.toggle("toggleZoom", "Toggle instead of hold", false)));

        register(new Def("fullbright", "Fullbright", "See in the dark — maximum brightness.",
            ModOption.slider("gamma", "Intensity", 1, 20, 0.5, 10)));

        register(new Def("timechanger", "Time Changer", "Change the visual time of day (client-side).",
            ModOption.slider("time", "Time of day", 0, 24000, 100, 0),
            ModOption.toggle("timePassage", "Let time pass", false)));

        register(new Def("weather", "Weather Changer", "Change the visual weather (client-side).",
            ModOption.dropdown("mode", "Weather", "Clear", "Clear", "Rain")));

        register(new Def("hitboxes", "Hitboxes", "Shows entity hitboxes without F3+B.",
            ModOption.color("lineColor", "Line color", 0xFFFFFFFF),
            ModOption.slider("lineWidth", "Line width", 1, 5, 0.5, 1),
            ModOption.slider("maxDistance", "Max distance", 8, 128, 1, 72),
            ModOption.toggle("showLookVector", "Look vector", false)));

        register(new Def("blockoverlay", "Block Overlay", "Restyle the block selection outline.",
            ModOption.color("color", "Outline color", 0xFFFFFFFF),
            ModOption.slider("thickness", "Outline thickness", 1, 5, 0.5, 1),
            ModOption.toggle("overlay", "Fill face", false),
            ModOption.color("overlayColor", "Fill color", 0x40F6F6F6)));

        register(new Def("chat", "Chat", "Chat additions.",
            ModOption.toggle("timestamps", "Timestamps", false)));

        register(new Def("chunkborders", "Chunk Borders", "Shows chunk boundaries.",
            ModOption.color("color", "Color", 0xFFFDFA7C),
            ModOption.slider("thickness", "Line width", 1, 4, 0.5, 1),
            ModOption.toggle("grid", "Full grid", true)));
    }

    /** SETTINGS tab — GENERAL page options (stored under "@general"). */
    public static final List<ModOption> GENERAL_OPTIONS = Collections.unmodifiableList(Arrays.asList(
        ModOption.dropdown("mainMenuStyle", "Main menu style", "Origin", "Origin", "Vanilla"),
        ModOption.toggle("confirmDisconnect", "Confirm before disconnecting", true)
    ));

    private Mods() {}

    private static void register(Def def) { REGISTRY.put(def.id, def); }

    public static Iterable<Def> all() { return REGISTRY.values(); }

    public static Def byId(String id) { return REGISTRY.get(id); }

    // ---- Accessors: read live, write-through with eager save ----

    public static boolean isOn(String id) {
        JsonObject m = ModsConfig.mod(id);
        return m.has("enabled") && m.get("enabled").getAsBoolean();
    }

    public static void setOn(String id, boolean on) {
        ModsConfig.mod(id).add("enabled", new JsonPrimitive(on));
        ModsConfig.save();
    }

    public static boolean bool(String id, String key) {
        ModOption opt = option(id, key);
        JsonObject m = ModsConfig.mod(id);
        if (m.has(key)) {
            try { return m.get(key).getAsBoolean(); } catch (Throwable ignored) {}
        }
        return opt != null && opt.defBool;
    }

    public static void setBool(String id, String key, boolean v) {
        ModsConfig.mod(id).add(key, new JsonPrimitive(v));
        ModsConfig.save();
    }

    public static double num(String id, String key) {
        ModOption opt = option(id, key);
        JsonObject m = ModsConfig.mod(id);
        if (m.has(key)) {
            try { return m.get(key).getAsDouble(); } catch (Throwable ignored) {}
        }
        return opt != null ? opt.defNum : 0;
    }

    public static void setNum(String id, String key, double v) {
        ModsConfig.mod(id).add(key, new JsonPrimitive(v));
        ModsConfig.save();
    }

    public static String mode(String id, String key) {
        ModOption opt = option(id, key);
        JsonObject m = ModsConfig.mod(id);
        if (m.has(key)) {
            try { return m.get(key).getAsString(); } catch (Throwable ignored) {}
        }
        return opt != null && opt.defMode != null ? opt.defMode : "";
    }

    public static void setMode(String id, String key, String v) {
        ModsConfig.mod(id).add(key, new JsonPrimitive(v));
        ModsConfig.save();
    }

    /** Stored color (signed 32-bit ARGB int, same encoding as modern). */
    public static int color(String id, String key) {
        ModOption opt = option(id, key);
        JsonObject m = ModsConfig.mod(id);
        if (m.has(key)) {
            try { return m.get(key).getAsInt(); } catch (Throwable ignored) {}
        }
        return opt != null ? opt.defColor : 0xFFFFFFFF;
    }

    public static void setColor(String id, String key, int argb) {
        ModsConfig.mod(id).add(key, new JsonPrimitive(argb));
        ModsConfig.save();
    }

    /**
     * The color to DRAW with right now — honors the chroma sibling keys
     * (key#chroma / key#speed), cycling hue at speed*0.004 deg/ms like modern.
     */
    public static int liveColor(String id, String key) {
        int stored = color(id, key);
        JsonObject m = ModsConfig.mod(id);
        boolean chroma = m.has(key + "#chroma") && m.get(key + "#chroma").getAsBoolean();
        if (!chroma) return stored;
        double speed = m.has(key + "#speed") ? m.get(key + "#speed").getAsDouble() : 50.0;
        float hue = (float) (((System.currentTimeMillis() * speed * 0.004) % 360.0) / 360.0);
        int rgb = Color.HSBtoRGB(hue, 1f, 1f);
        return (stored & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    public static ModOption option(String id, String key) {
        List<ModOption> opts = optionsFor(id);
        for (ModOption o : opts) if (o.key.equals(key)) return o;
        return null;
    }

    public static List<ModOption> optionsFor(String id) {
        if (GENERAL_ID.equals(id)) return GENERAL_OPTIONS;
        Def def = REGISTRY.get(id);
        return def != null ? def.options : new ArrayList<ModOption>();
    }

    // ---- Meta ----

    public static boolean panelBacking() {
        JsonObject meta = ModsConfig.meta();
        return !meta.has("panelBacking") || meta.get("panelBacking").getAsBoolean();
    }

    public static void setPanelBacking(boolean v) {
        ModsConfig.meta().add("panelBacking", new JsonPrimitive(v));
        ModsConfig.save();
    }

    /** Title-screen style gate — "Origin" (default) or "Vanilla". */
    public static boolean originTitleStyle() {
        String v = mode(GENERAL_ID, "mainMenuStyle");
        return !"Vanilla".equals(v);
    }

    public static boolean confirmDisconnect() {
        JsonObject m = ModsConfig.mod(GENERAL_ID);
        return !m.has("confirmDisconnect") || m.get("confirmDisconnect").getAsBoolean();
    }
}
