package com.origin.client.hud;

import com.origin.client.mods.ModsConfig;

/**
 * A HUD element's placement: anchor cell (0..8 = TL,TC,TR,ML,MC,MR,BL,BC,BR)
 * plus a dx/dy offset, a scale, and a backing opacity. Identical semantics to
 * the modern HudPos so saved layouts mean the same thing on every version.
 */
public final class HudPos {

    public int anchor;
    public double dx, dy;
    public double scale;
    public double bg;

    public HudPos(int anchor, double dx, double dy, double scale) {
        this.anchor = anchor;
        this.dx = dx;
        this.dy = dy;
        this.scale = scale;
        this.bg = 0;
    }

    public static HudPos load(String id, HudPos def) {
        double[] a = ModsConfig.hudArray(id);
        if (a == null) return new HudPos(def.anchor, def.dx, def.dy, def.scale);
        HudPos p = new HudPos((int) a[0], a[1], a[2], a[3]);
        p.bg = a[4];
        return p;
    }

    public void save(String id) {
        ModsConfig.setHudArray(id, anchor, dx, dy, scale, bg);
    }

    public int col() { return anchor % 3; }
    public int row() { return anchor / 3; }

    /** Resolved X for an element of width w on a screen of width screenW. */
    public int x(int screenW, int w) {
        int base;
        switch (col()) {
            case 1:  base = (screenW - w) / 2; break;
            case 2:  base = screenW - w; break;
            default: base = 0;
        }
        return (int) Math.round(base + dx);
    }

    /** Resolved Y for an element of height h on a screen of height screenH. */
    public int y(int screenH, int h) {
        int base;
        switch (row()) {
            case 1:  base = (screenH - h) / 2; break;
            case 2:  base = screenH - h; break;
            default: base = 0;
        }
        return (int) Math.round(base + dy);
    }

    /**
     * Re-derive anchor + offsets from an absolute position — the anchor is
     * whichever screen third the element's center lands in (drag behavior).
     */
    public void setFromAbsolute(double absX, double absY, int w, int h, int screenW, int screenH) {
        double cx = absX + w / 2.0, cy = absY + h / 2.0;
        int col = cx < screenW / 3.0 ? 0 : (cx < screenW * 2.0 / 3.0 ? 1 : 2);
        int row = cy < screenH / 3.0 ? 0 : (cy < screenH * 2.0 / 3.0 ? 1 : 2);
        anchor = row * 3 + col;
        double baseX = col == 1 ? (screenW - w) / 2.0 : (col == 2 ? screenW - w : 0);
        double baseY = row == 1 ? (screenH - h) / 2.0 : (row == 2 ? screenH - h : 0);
        dx = absX - baseX;
        dy = absY - baseY;
    }
}
