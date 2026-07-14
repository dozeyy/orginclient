package com.origin.client.mods;

/**
 * One row in a mod's settings page. Mirrors the modern ModOption: a kind, a
 * config key, bounds for sliders, choices for dropdowns, and an optional
 * dependsOn parent (child rows indent and only show while the parent toggle
 * is on).
 */
public final class ModOption {

    public enum Kind { TOGGLE, SLIDER, COLOR, KEYBIND, DROPDOWN, HEADER }

    public final Kind kind;
    public final String key;
    public final String label;
    public final String dependsOn;

    // Slider metadata
    public final double min, max, step;
    public final boolean percent;

    // Dropdown metadata
    public final String[] choices;

    // Defaults (by kind)
    public final boolean defBool;
    public final double defNum;
    public final int defColor;
    public final String defMode;

    private ModOption(Kind kind, String key, String label, String dependsOn,
                      double min, double max, double step, boolean percent,
                      String[] choices, boolean defBool, double defNum, int defColor, String defMode) {
        this.kind = kind;
        this.key = key;
        this.label = label;
        this.dependsOn = dependsOn;
        this.min = min;
        this.max = max;
        this.step = step;
        this.percent = percent;
        this.choices = choices;
        this.defBool = defBool;
        this.defNum = defNum;
        this.defColor = defColor;
        this.defMode = defMode;
    }

    public static ModOption header(String label) {
        return new ModOption(Kind.HEADER, "#" + label, label, null, 0, 0, 0, false, null, false, 0, 0, null);
    }

    public static ModOption toggle(String key, String label, boolean def) {
        return new ModOption(Kind.TOGGLE, key, label, null, 0, 0, 0, false, null, def, 0, 0, null);
    }

    public static ModOption toggle(String key, String label, boolean def, String dependsOn) {
        return new ModOption(Kind.TOGGLE, key, label, dependsOn, 0, 0, 0, false, null, def, 0, 0, null);
    }

    public static ModOption slider(String key, String label, double min, double max, double step, double def) {
        return new ModOption(Kind.SLIDER, key, label, null, min, max, step, false, null, false, def, 0, null);
    }

    public static ModOption percentSlider(String key, String label, double min, double max, double step, double def) {
        return new ModOption(Kind.SLIDER, key, label, null, min, max, step, true, null, false, def, 0, null);
    }

    public static ModOption color(String key, String label, int def) {
        return new ModOption(Kind.COLOR, key, label, null, 0, 0, 0, false, null, false, 0, def, null);
    }

    public static ModOption keybind(String key, String label, int defKeyCode) {
        return new ModOption(Kind.KEYBIND, key, label, null, 0, 0, 0, false, null, false, defKeyCode, 0, null);
    }

    public static ModOption dropdown(String key, String label, String def, String... choices) {
        return new ModOption(Kind.DROPDOWN, key, label, null, 0, 0, 0, false, choices, false, 0, 0, def);
    }

    /** Display text for a slider value — mirrors ModOption.format. */
    public String format(double v) {
        if (percent && max <= 1.0) return Math.round(v * 100) + "%";
        if (step >= 1.0) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 100) / 100.0);
    }
}
