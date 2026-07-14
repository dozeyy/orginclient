package com.origin.client.theme;

/**
 * The Origin design system, ported verbatim from the modern (1.20+) modules —
 * pure math and constants, no Minecraft dependencies, so the values here are
 * byte-identical to what every other Origin version renders with. Do not
 * "adjust" anything in this file for 1.12.2; visual parity across versions is
 * mandate #2.
 */
public final class OriginTheme {

    // ---- Colors (ARGB) ----
    public static final int BG                = 0xFF050505;
    public static final int BG_ALT            = 0xFF0A0A0A;
    public static final int PANEL             = 0xFF101010;
    public static final int PANEL_TRANSLUCENT = 0x8C101010;
    public static final int PANEL_ALT         = 0xFF161616;
    public static final int STROKE            = 0x14FFFFFF;
    public static final int STROKE_STRONG     = 0x2EFFFFFF;
    public static final int STROKE_HOVER      = 0x9EFFFFFF;
    public static final int TEXT              = 0xFFF5F5F5;
    public static final int TEXT_DIM          = 0xFF9A9A9A;
    public static final int MUTED             = 0xFF616161;
    public static final int ACCENT            = 0xFFFFFFFF;
    public static final int ACCENT_GLOW       = 0x59FFFFFF;
    public static final int ACCENT_DIM        = 0x8CFFFFFF;

    // Toggle switch — the only two non-gray tones in the system.
    public static final int SWITCH_ON     = 0xFF2F7D53;
    public static final int SWITCH_OFF    = 0xFFA33A33;
    public static final int SWITCH_KNOB   = 0xFFF0F0F0;
    public static final int SWITCH_STROKE = 0x40000000;

    // Mod-card ENABLED/DISABLED button tones.
    public static final int GREEN_TEXT = 0xFF7FA98F;
    public static final int GREEN_EDGE = 0xB32F7D53;
    public static final int GREEN_FILL = 0x2E2F7D53;
    public static final int RED_TEXT   = 0xFFC77A73;
    public static final int RED_EDGE   = 0xB3B23A33;
    public static final int RED_FILL   = 0x2EB23A33;

    // Widget restyle tones.
    public static final int FILL_NORMAL     = 0x07FFFFFF;
    public static final int FILL_HOVER      = 0x0FFFFFFF;
    public static final int BORDER_NORMAL   = 0x1CFFFFFF;
    public static final int FILL_DISABLED   = 0x04FFFFFF;
    public static final int BORDER_DISABLED = 0x10FFFFFF;

    // Color-picker preset palette (white-first).
    public static final int[] PALETTE = {
        0xFFFFFFFF, 0xFFE05555, 0xFF55E055, 0xFF5599FF,
        0xFFFFD855, 0xFF55DDDD, 0xFFFF9944, 0xFFCC66FF
    };

    // ---- Spacing (8px grid) ----
    public static final int SPACE_1 = 8, SPACE_2 = 16, SPACE_3 = 24, SPACE_4 = 32,
                            SPACE_6 = 48, SPACE_8 = 64, SPACE_10 = 96;

    // ---- Corner radii ----
    public static final int RADIUS_SM = 6, RADIUS_MD = 10, RADIUS_LG = 14;

    // ---- Motion ----
    public static final double DURATION_FAST_MS = 150.0;
    public static final double DURATION_MED_MS  = 300.0;
    public static final double HALO_LERP_FACTOR = 0.12;

    private OriginTheme() {}

    public static double easeOut(double t) { return cubicBezier(0.16, 1.0, 0.3, 1.0, clamp01(t)); }

    public static double spring(double t) { return cubicBezier(0.34, 1.56, 0.64, 1.0, clamp01(t)); }

    public static double clamp01(double t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public static int lerpColor(int a, int b, double t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) Math.round(lerp(aa, ba, t));
        int rr = (int) Math.round(lerp(ar, br, t));
        int rg = (int) Math.round(lerp(ag, bg, t));
        int rb = (int) Math.round(lerp(ab, bb, t));
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Replace a color's alpha byte, keeping RGB. */
    public static int withAlpha(int argb, int alpha0to255) {
        return (clampByte(alpha0to255) << 24) | (argb & 0x00FFFFFF);
    }

    private static int clampByte(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /**
     * Cubic-bezier easing, Newton-Raphson solve for the bezier parameter —
     * the exact port of OriginTheme.cubicBezier from the modern modules.
     */
    public static double cubicBezier(double x1, double y1, double x2, double y2, double t) {
        double u = t;
        for (int i = 0; i < 8; i++) {
            double x = bezierComponent(u, x1, x2) - t;
            if (Math.abs(x) < 1e-6) break;
            double dx = bezierDerivative(u, x1, x2);
            if (Math.abs(dx) < 1e-6) break;
            u -= x / dx;
        }
        return bezierComponent(u, y1, y2);
    }

    private static double bezierComponent(double u, double p1, double p2) {
        double v = 1.0 - u;
        return 3.0 * v * v * u * p1 + 3.0 * v * u * u * p2 + u * u * u;
    }

    private static double bezierDerivative(double u, double p1, double p2) {
        double v = 1.0 - u;
        return 3.0 * v * v * p1 + 6.0 * v * u * (p2 - p1) + 3.0 * u * u * (1.0 - p2);
    }
}
