package com.origin.client.gui;

import com.origin.client.mods.Mods;
import com.origin.client.mods.ModsConfig;
import com.origin.client.theme.OriginTheme;
import com.origin.client.util.Gl;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/**
 * Modal color picker overlay — NOT a GuiScreen. The mod menu owns it: draws
 * it last and routes input to it first, so it can live over any page without
 * a screen switch. Every change writes through immediately (Mods.setColor /
 * the chroma sibling keys) — the swatch behind it updates live.
 *
 * Chroma state rides sibling keys next to the color ("key#chroma" bool,
 * "key#speed" 1..100, "key#type" Wave/Spread/Solid Cycle) — same encoding the
 * modern modules and Mods.liveColor() use.
 */
public final class OriginColorPicker {

    private static final int PANEL_W = 234, PANEL_H = 236;
    private static final String[] CHROMA_TYPES = {"Wave", "Spread", "Solid Cycle"};

    private final String modId;
    private final String key;

    // Working HSV + alpha (alpha kept separate; HSBtoRGB is RGB-only).
    private float hue, sat, val;
    private int alpha;

    // 0 = none, 1 = SV field, 2 = hue bar, 3 = alpha bar, 4 = speed slider.
    private int dragging;

    private boolean closeRequested;
    private boolean failed;

    // Panel origin, recomputed from screen size each layout() call so draw
    // and hit tests always agree.
    private double x, y;

    // Chroma switch animation: {progress, lastFrameMs}.
    private final double[] chromaAnim;

    public OriginColorPicker(String modId, String key) {
        this.modId = modId;
        this.key = key;
        int argb = Mods.color(modId, key);
        alpha = (argb >>> 24) & 0xFF;
        float[] hsb = Color.RGBtoHSB((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, null);
        hue = hsb[0];
        sat = hsb[1];
        val = hsb[2];
        chromaAnim = new double[]{chromaOn() ? 1 : 0, System.currentTimeMillis()};
    }

    private void fail(Throwable t) {
        if (!failed) System.err.println("[OriginClient] color picker failed, closing: " + t);
        failed = true;
        closeRequested = true;
    }

    public boolean closeRequested() { return closeRequested; }

    // ---- Chroma sibling keys: raw reads with local defaults ----

    private boolean chromaOn() {
        JsonObject m = ModsConfig.mod(modId);
        String k = key + "#chroma";
        try { return m.has(k) && m.get(k).getAsBoolean(); } catch (Throwable t) { return false; }
    }

    private double chromaSpeed() {
        JsonObject m = ModsConfig.mod(modId);
        String k = key + "#speed";
        try { return m.has(k) ? m.get(k).getAsDouble() : 50.0; } catch (Throwable t) { return 50.0; }
    }

    private String chromaType() {
        JsonObject m = ModsConfig.mod(modId);
        String k = key + "#type";
        try { return m.has(k) ? m.get(k).getAsString() : "Wave"; } catch (Throwable t) { return "Wave"; }
    }

    // ---- Value plumbing ----

    private int currentArgb() {
        int rgb = Color.HSBtoRGB(hue, sat, val);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private void write() { Mods.setColor(modId, key, currentArgb()); }

    // The host mod menu draws in fixed-eff-2 units (displaySize/2), not GUI
    // units — the picker lives inside that space, so it must center with the
    // same math or it lands off-screen at GUI scales other than 2.
    private static double hostW() { return Math.max(320, Minecraft.getMinecraft().displayWidth / 2.0); }
    private static double hostH() { return Math.max(240, Minecraft.getMinecraft().displayHeight / 2.0); }

    private void layout() {
        x = (hostW() - PANEL_W) / 2.0;
        y = (hostH() - PANEL_H) / 2.0;
    }

    private static boolean in(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------

    public void draw(int mouseX, int mouseY) {
        try {
            drawInner(mouseX, mouseY);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawInner(int mouseX, int mouseY) {
        layout();
        Gl.fill(0, 0, hostW(), hostH(), 0x66000000);
        OriginUi.panel(x, y, PANEL_W, PANEL_H, 12, 0xF2101010, OriginTheme.STROKE_STRONG);

        // SV field: white -> pure hue left-to-right, then a transparent ->
        // black vertical gradient for value. 1px strips keep it exact.
        int hueRgb = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
        for (int i = 0; i < 150; i++) {
            int c = OriginTheme.lerpColor(0xFFFFFFFF, hueRgb, i / 149.0);
            Gl.fill(x + 12 + i, y + 12, x + 13 + i, y + 120, c);
        }
        Gl.fillGradient(x + 12, y + 12, x + 162, y + 120, 0x00000000, 0xFF000000);
        // SV cursor: 7x7 ring drawn as 4 one-px fills.
        double svx = x + 12 + sat * 150, svy = y + 12 + (1 - val) * 108;
        Gl.fill(svx - 3, svy - 3, svx + 3, svy - 2, 0xFFFFFFFF);
        Gl.fill(svx - 3, svy + 2, svx + 3, svy + 3, 0xFFFFFFFF);
        Gl.fill(svx - 3, svy - 2, svx - 2, svy + 2, 0xFFFFFFFF);
        Gl.fill(svx + 2, svy - 2, svx + 3, svy + 2, 0xFFFFFFFF);

        // Hue bar: 54 x 2px rainbow strips.
        for (int i = 0; i < 54; i++) {
            int c = Color.HSBtoRGB(i / 54f, 1f, 1f) | 0xFF000000;
            Gl.fill(x + 170, y + 12 + i * 2, x + 184, y + 12 + (i + 1) * 2, c);
        }
        double hy = y + 12 + hue * 108;
        Gl.fill(x + 169, hy - 1, x + 185, hy + 1, 0xFFFFFFFF);

        // Alpha bar: checkerboard, then transparent -> opaque of the color.
        for (int r = 0; r < 27; r++) {
            for (int cIdx = 0; cIdx < 4; cIdx++) {
                int c = ((r + cIdx) % 2 == 0) ? 0xFF3A3A3A : 0xFF6A6A6A;
                double cx0 = x + 192 + cIdx * 4;
                Gl.fill(cx0, y + 12 + r * 4, Math.min(cx0 + 4, x + 206), y + 12 + (r + 1) * 4, c);
            }
        }
        int rgb = Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
        Gl.fillGradient(x + 192, y + 12, x + 206, y + 120, rgb, 0xFF000000 | rgb);
        double ay = y + 12 + (alpha / 255.0) * 108;
        Gl.fill(x + 191, ay - 1, x + 207, ay + 1, 0xFFFFFFFF);

        // Chroma toggle row.
        boolean chroma = chromaOn();
        long now = System.currentTimeMillis();
        double dt = now - chromaAnim[1];
        chromaAnim[1] = now;
        chromaAnim[0] = OriginTheme.clamp01(chromaAnim[0] + (chroma ? dt : -dt) / 170.0);
        OriginUi.labelLeft("Chroma", x + 12, y + 138, OriginTheme.TEXT);
        OriginUi.switchAt(x + 192, y + 130, 30, OriginTheme.easeOut(chromaAnim[0]), true);

        if (chroma) {
            OriginUi.labelLeft("Speed", x + 12, y + 160, OriginTheme.TEXT_DIM);
            double t = (chromaSpeed() - 1) / 99.0;
            OriginUi.slider(x + 112, y + 160, 90, OriginTheme.clamp01(t), dragging == 4);

            OriginUi.labelLeft("Type", x + 12, y + 183, OriginTheme.TEXT_DIM);
            boolean hover = in(mouseX, mouseY, x + 112, y + 174, 90, 18);
            OriginUi.panel(x + 112, y + 174, 90, 18, 7, hover ? 0x1EFFFFFF : 0x10FFFFFF,
                           hover ? OriginTheme.STROKE_HOVER : OriginTheme.STROKE);
            OriginUi.label("< " + chromaType() + " >", x + 157, y + 183,
                           hover ? OriginTheme.TEXT : OriginTheme.TEXT_DIM);
        }

        // Preset palette (applies RGB, keeps current alpha).
        for (int i = 0; i < OriginTheme.PALETTE.length; i++) {
            double sx = x + 12 + i * 18;
            OriginUi.panel(sx, y + 198, 12, 12, 5, OriginTheme.PALETTE[i], 0x40FFFFFF);
        }

        String hex = String.format("#%08X", currentArgb());
        OriginUi.labelLeft(hex, x + PANEL_W - 12 - Minecraft.getMinecraft().fontRenderer.getStringWidth(hex),
                           y + PANEL_H - 14, OriginTheme.TEXT_DIM);
    }

    // ------------------------------------------------------------------
    // Input (all return "consumed"; the picker is modal so everything is)
    // ------------------------------------------------------------------

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        try {
            layout();
            if (!in(mouseX, mouseY, x, y, PANEL_W, PANEL_H)) {
                closeRequested = true;
                return true; // still consume — no click-through under the scrim
            }
            if (button != 0) return true;

            if (in(mouseX, mouseY, x + 12, y + 12, 150, 108)) { dragging = 1; drag(mouseX, mouseY); return true; }
            if (in(mouseX, mouseY, x + 170, y + 12, 14, 108)) { dragging = 2; drag(mouseX, mouseY); return true; }
            if (in(mouseX, mouseY, x + 192, y + 12, 14, 108)) { dragging = 3; drag(mouseX, mouseY); return true; }

            boolean chroma = chromaOn();
            if (in(mouseX, mouseY, x + 192, y + 128, 30, 20)) {
                Mods.setBool(modId, key + "#chroma", !chroma);
                return true;
            }
            if (chroma && in(mouseX, mouseY, x + 105, y + 152, 104, 16)) {
                dragging = 4;
                drag(mouseX, mouseY);
                return true;
            }
            if (chroma && in(mouseX, mouseY, x + 112, y + 174, 90, 18)) {
                String cur = chromaType();
                int idx = 0;
                for (int i = 0; i < CHROMA_TYPES.length; i++) if (CHROMA_TYPES[i].equals(cur)) { idx = i; break; }
                int dir = mouseX < x + 157 ? -1 : 1;
                Mods.setMode(modId, key + "#type",
                             CHROMA_TYPES[((idx + dir) % CHROMA_TYPES.length + CHROMA_TYPES.length) % CHROMA_TYPES.length]);
                return true;
            }
            for (int i = 0; i < OriginTheme.PALETTE.length; i++) {
                if (in(mouseX, mouseY, x + 12 + i * 18, y + 198, 12, 12)) {
                    int p = OriginTheme.PALETTE[i];
                    float[] hsb = Color.RGBtoHSB((p >>> 16) & 0xFF, (p >>> 8) & 0xFF, p & 0xFF, null);
                    hue = hsb[0];
                    sat = hsb[1];
                    val = hsb[2];
                    write();
                    return true;
                }
            }
            return true;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    public boolean mouseDragged(int mouseX, int mouseY) {
        try {
            if (dragging == 0) return false;
            layout();
            drag(mouseX, mouseY);
            return true;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    private void drag(int mouseX, int mouseY) {
        switch (dragging) {
            case 1:
                sat = (float) OriginTheme.clamp01((mouseX - (x + 12)) / 150.0);
                val = (float) (1.0 - OriginTheme.clamp01((mouseY - (y + 12)) / 108.0));
                write();
                break;
            case 2:
                hue = (float) OriginTheme.clamp01((mouseY - (y + 12)) / 108.0);
                write();
                break;
            case 3:
                alpha = (int) Math.round(OriginTheme.clamp01((mouseY - (y + 12)) / 108.0) * 255);
                write();
                break;
            case 4: {
                double t = OriginTheme.clamp01((mouseX - (x + 112)) / 90.0);
                Mods.setNum(modId, key + "#speed", Math.round(1 + t * 99));
                break;
            }
            default:
                break;
        }
    }

    public void mouseReleased() { dragging = 0; }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) closeRequested = true;
        return true; // modal — swallow all keys so the menu doesn't also act
    }
}
