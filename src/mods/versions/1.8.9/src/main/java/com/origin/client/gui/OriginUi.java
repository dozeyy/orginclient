package com.origin.client.gui;

import com.origin.client.theme.OriginTheme;
import com.origin.client.util.Gl;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * Origin's mid-level drawing kit — rounded panels (9-slice tinted alpha
 * masks), glows, the ring mark, switches, sliders and the mod-icon atlas.
 * Mirrors OriginUi from the modern modules; the baked textures are the SAME
 * bytes those modules ship, so shapes and softness match exactly.
 *
 * Fail-soft: any Throwable in here latches {@link #ok} false and every helper
 * degrades to flat fills (square corners) for the rest of the session —
 * vanilla-legibility beats a crash, per the never-broken mandate.
 */
public final class OriginUi {

    // buttons.json: {texSize:96, corner:24, borderPx:4}
    private static final int TEX = 96;
    private static final int CORNER = 24;

    private static final ResourceLocation FILL   = new ResourceLocation("originclient", "textures/ui/button_fill.png");
    private static final ResourceLocation BORDER = new ResourceLocation("originclient", "textures/ui/button_border.png");
    private static final ResourceLocation GLOW   = new ResourceLocation("originclient", "textures/ui/radial_glow.png");
    private static final ResourceLocation LOGO   = new ResourceLocation("originclient", "textures/ui/origin_logo.png");
    private static final ResourceLocation KNOB   = new ResourceLocation("originclient", "textures/ui/switch_knob.png");
    private static final ResourceLocation ICONS  = new ResourceLocation("originclient", "textures/ui/mod_icons.png");

    public static volatile boolean ok = true;

    private OriginUi() {}

    public static void fail(Throwable t) {
        ok = false;
        System.err.println("[OriginClient] UI kit disabled for this session: " + t);
    }

    /** Rounded panel: fill + optional 1px-look border, display corner radius. */
    public static void panel(double x, double y, double w, double h, int corner, int fill, int border) {
        try {
            if (!ok) { Gl.fill(x, y, x + w, y + h, fill); return; }
            if ((fill >>> 24) > 0) nine(FILL, x, y, w, h, corner, fill);
            if ((border >>> 24) > 0) nine(BORDER, x, y, w, h, corner, border);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * 9-slice blit of a 96x96 alpha mask (corner 24), tinted by an ARGB color.
     * Port of OriginUi.nine from the modern modules: 4 native corners, 4
     * stretched edges, 1 stretched center.
     */
    private static void nine(ResourceLocation mask, double x, double y, double w, double h, int corner, int argb) {
        double cd = Math.min(corner, Math.min(w, h) / 2.0);
        if (cd <= 0 || w <= 0 || h <= 0) return;
        float a = (argb >>> 24 & 0xFF) / 255f;
        float r = (argb >>> 16 & 0xFF) / 255f;
        float g = (argb >>> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        double c = CORNER / (double) TEX;         // corner UV span
        double m = (TEX - 2.0 * CORNER) / TEX;    // middle UV span

        Gl.bindLinear(mask);
        double x1 = x + cd, x2 = x + w - cd, y1 = y + cd, y2 = y + h - cd;
        // corners
        Gl.texQuadUv(x,  y,  cd, cd, 0,     0,     c,     c,     r, g, b, a);
        Gl.texQuadUv(x2, y,  cd, cd, c + m, 0,     1,     c,     r, g, b, a);
        Gl.texQuadUv(x,  y2, cd, cd, 0,     c + m, c,     1,     r, g, b, a);
        Gl.texQuadUv(x2, y2, cd, cd, c + m, c + m, 1,     1,     r, g, b, a);
        // edges
        if (x2 > x1) {
            Gl.texQuadUv(x1, y,  x2 - x1, cd, c, 0,     c + m, c, r, g, b, a);
            Gl.texQuadUv(x1, y2, x2 - x1, cd, c, c + m, c + m, 1, r, g, b, a);
        }
        if (y2 > y1) {
            Gl.texQuadUv(x,  y1, cd, y2 - y1, 0,     c, c,     c + m, r, g, b, a);
            Gl.texQuadUv(x2, y1, cd, y2 - y1, c + m, c, 1,     c + m, r, g, b, a);
        }
        // center
        if (x2 > x1 && y2 > y1)
            Gl.texQuadUv(x1, y1, x2 - x1, y2 - y1, c, c, c + m, c + m, r, g, b, a);
    }

    /** Soft radial glow centered at (cx, cy) with the given diameter. */
    public static void glow(double cx, double cy, double diameter, double alpha) {
        try {
            if (!ok || alpha <= 0) return;
            Gl.bindLinear(GLOW);
            Gl.texQuad(cx - diameter / 2.0, cy - diameter / 2.0, diameter, diameter, (float) OriginTheme.clamp01(alpha));
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** The crisp baked 3-ring Origin mark, centered. */
    public static void logo(double cx, double cy, double size, double alpha) {
        try {
            if (!ok || alpha <= 0) return;
            Gl.bindLinear(LOGO);
            Gl.texQuad(cx - size / 2.0, cy - size / 2.0, size, size, (float) OriginTheme.clamp01(alpha));
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * iOS-style toggle switch, exact port of OriginUi.switchAt. Track is a
     * rounded rect lerping SWITCH_OFF -> SWITCH_ON by knob progress; knob is a
     * near-white rounded square sliding left -> right.
     *
     * @param p eased knob progress 0..1 (0 = off, 1 = on)
     */
    public static void switchAt(double x, double y, double w, double p, boolean enabled) {
        double h = w * 8.0 / 15.0;
        int trackCorner = Math.max(3, (int) Math.round(h * 0.30));
        int track = enabled
            ? OriginTheme.lerpColor(OriginTheme.SWITCH_OFF, OriginTheme.SWITCH_ON, p)
            : OriginTheme.lerpColor(0xFF3C3C3C, 0xFF565656, p);
        panel(x, y, w, h, trackCorner, track, OriginTheme.SWITCH_STROKE);
        double pad = Math.max(2, Math.round(h * 0.15));
        double knob = h - pad * 2;
        double kx = OriginTheme.lerp(x + pad, x + w - pad - knob, p);
        panel(kx, y + pad, knob, knob, Math.max(2, trackCorner - 1),
              enabled ? OriginTheme.SWITCH_KNOB : 0xFFB8B8B8, 0);
    }

    /**
     * Pill slider. Returns nothing; caller computes value from mouse.
     *
     * @param t normalized value 0..1 (knob position)
     */
    public static void slider(double x, double y, double w, double t, boolean active) {
        double trackH = 6;
        panel(x, y - trackH / 2.0, w, trackH, (int) (trackH / 2), 0x30FFFFFF, 0);
        double kcx = x + w * OriginTheme.clamp01(t);
        double fillW = kcx - x;
        if (fillW > 0)
            panel(x, y - trackH / 2.0, fillW, trackH, (int) (trackH / 2), active ? 0xE6E0E0E0 : 0xA8D8D8D8, 0);
        double dia = active ? 14 : 12;
        try {
            if (ok) {
                Gl.bindLinear(KNOB);
                Gl.texQuad(kcx - dia / 2.0, y - dia / 2.0, dia, dia, 1f);
                return;
            }
        } catch (Throwable t2) {
            fail(t2);
        }
        Gl.fill(kcx - dia / 2.0, y - dia / 2.0, kcx + dia / 2.0, y + dia / 2.0, 0xFFE8E8E8);
    }

    /**
     * Mod icon from the 576x384 atlas (6 cols, 96px cells), drawn white.
     * Unknown ids fall back to the ring logo.
     */
    public static void icon(String id, double x, double y, double size, double alpha) {
        int[] cell = ModIcons.cell(id);
        if (cell == null) {
            logo(x + size / 2.0, y + size / 2.0, size, alpha);
            return;
        }
        try {
            if (!ok) return;
            Gl.bindLinear(ICONS);
            double u0 = cell[0] / 576.0, v0 = cell[1] / 384.0;
            double u1 = (cell[0] + 96) / 576.0, v1 = (cell[1] + 96) / 384.0;
            Gl.texQuadUv(x, y, size, size, u0, v0, u1, v1, 1f, 1f, 1f, (float) OriginTheme.clamp01(alpha));
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Chip fill per the mod-menu spec (backed vs clear panel mode). */
    public static int chipFill(boolean hover, boolean backed) {
        if (backed) return hover ? 0x2EFFFFFF : 0x16FFFFFF;
        return hover ? 0xE0181818 : 0xC8101010;
    }

    /** Standard restyled button visuals (fill+border lerped by eased hover). */
    public static void button(double x, double y, double w, double h, double hoverT, boolean enabled) {
        int fill = enabled
            ? OriginTheme.lerpColor(OriginTheme.FILL_NORMAL, OriginTheme.FILL_HOVER, hoverT)
            : OriginTheme.FILL_DISABLED;
        int border = enabled
            ? OriginTheme.lerpColor(OriginTheme.BORDER_NORMAL, OriginTheme.STROKE_HOVER, hoverT)
            : OriginTheme.BORDER_DISABLED;
        panel(x, y, w, h, OriginTheme.RADIUS_SM, fill, border);
    }

    /** Centered vanilla-font label, no shadow — the Origin text style. */
    public static void label(String text, double cx, double cy, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int tw = mc.fontRendererObj.getStringWidth(text);
        mc.fontRendererObj.drawString(text, (float) (cx - tw / 2.0), (float) (cy - 4), color, false);
    }

    /** Vanilla-font label anchored left, no shadow. */
    public static void labelLeft(String text, double x, double cy, int color) {
        Minecraft.getMinecraft().fontRendererObj.drawString(text, (float) x, (float) (cy - 4), color, false);
    }
}
