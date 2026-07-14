package com.origin.client.hud;

import com.origin.client.gui.OriginUi;
import com.origin.client.mods.ModsConfig;
import com.origin.client.render.OriginScreenRenderer;
import com.origin.client.util.Gl;
import com.origin.client.theme.OriginTheme;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The HUD layout editor, drawn over the LIVE game (no dim, no blur — the
 * player positions elements against real gameplay). Every enabled element
 * renders in preview mode with a selection outline; drag to move (with
 * center snapping), corner handle or scroll wheel to scale, R to reset.
 *
 * quick==true is the pause-adjacent entry: it stacks the Origin mark,
 * wordmark and a MODS button in the lower third so the editor doubles as the
 * in-game menu front door.
 *
 * Drags/resizes mutate an in-memory HudPos published through
 * HudElements.overridePos and persist ONCE on release — a disk write per
 * mouse-move (the old eager-save path) made dragging visibly choppy.
 */
public class HudEditorScreen extends GuiScreen {

    private static final double SCALE_MIN = 0.5, SCALE_MAX = 2.5;

    private final boolean quick;

    private HudElements.Element dragging;
    private double grabDX, grabDY;           // mouse offset inside the element at grab
    private boolean snapX, snapY;            // center guides showing this frame

    private HudElements.Element resizing;
    private double grabScale, grabDist;
    private boolean resizeEngaged;            // 3px deadzone before scaling starts

    // The live in-memory pos for the active drag/resize (see class comment).
    private HudPos livePos;

    private HudElements.Element selected;

    /** Hover/selection outline animation per element: {progress, lastMs}. */
    private final Map<String, double[]> anims = new HashMap<String, double[]>();

    private boolean failed;

    public HudEditorScreen(boolean quick) {
        this.quick = quick;
    }

    private void fail(Throwable t) {
        if (!failed) System.err.println("[OriginClient] HUD editor failed, closing: " + t);
        failed = true;
        mc.displayGuiScreen(null);
    }

    @Override
    public void initGui() {
        HudElements.editorOpen = true; // game-overlay pass stands down
    }

    @Override
    public void onGuiClosed() {
        HudElements.editorOpen = false;
        commitLive();
    }

    /** Persist and clear the live drag override (idempotent). */
    private void commitLive() {
        HudElements.Element active = dragging != null ? dragging : resizing;
        if (livePos != null && active != null) livePos.save(active.id);
        livePos = null;
        HudElements.overrideId = null;
        HudElements.overridePos = null;
    }

    private void beginLive(HudElements.Element e) {
        livePos = e.pos();
        HudElements.overrideId = e.id;
        HudElements.overridePos = livePos;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private double anim(String key, boolean target, double durMs) {
        double[] st = anims.get(key);
        long now = System.currentTimeMillis();
        if (st == null) { st = new double[]{target ? 1 : 0, now}; anims.put(key, st); }
        double dt = now - st[1];
        st[1] = now;
        st[0] = OriginTheme.clamp01(st[0] + (target ? dt : -dt) / durMs);
        return OriginTheme.easeOut(st[0]);
    }

    private static boolean in(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Outer selection box (element bounds inset -4) as {x, y, w, h}.
     */
    private static double[] outer(int[] b) {
        return new double[]{b[0] - 4, b[1] - 4, b[2] + 8, b[3] + 8};
    }

    /**
     * Resize handle center on the outer box — the corner diagonally opposite
     * the anchored corner, so scaling grows away from the pin. Anchor col 0
     * pins left (handle right), col 2 pins right (handle left), col 1 gets
     * the right side; rows mirror that for top/bottom.
     */
    private static double[] handleCenter(HudPos pos, double[] o) {
        double hx = pos.col() == 2 ? o[0] : o[0] + o[2];
        double hy = pos.row() == 2 ? o[1] : o[1] + o[3];
        return new double[]{hx, hy};
    }

    /** The anchored corner — scale distances are measured from here. */
    private static double[] anchorCorner(HudPos pos, double[] o) {
        double ax = pos.col() == 2 ? o[0] + o[2] : o[0];
        double ay = pos.row() == 2 ? o[1] + o[3] : o[1];
        return new double[]{ax, ay};
    }

    // ------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            drawInner(mouseX, mouseY);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawInner(int mouseX, int mouseY) {
        ScaledResolution sr = new ScaledResolution(mc);

        for (HudElements.Element e : HudElements.all()) {
            if (!e.on()) continue;
            int[] b = HudElements.bounds(e, sr, true);
            double[] o = outer(b);
            boolean hover = in(mouseX, mouseY, o[0], o[1], o[2], o[3]);
            double t = anim(e.id, hover || selected == e, 120);

            // Hover backing goes down first so the element draws over it.
            if (t > 0.01)
                Gl.fill(o[0], o[1], o[0] + o[2], o[1] + o[3],
                        OriginTheme.withAlpha(0x303030, (int) (0x40 * t)));

            HudElements.drawScaled(e, sr, true);

            int line = OriginTheme.lerpColor(OriginTheme.STROKE_STRONG, 0xF0FFFFFF, t);
            Gl.fill(o[0], o[1], o[0] + o[2], o[1] + 1, line);
            Gl.fill(o[0], o[1] + o[3] - 1, o[0] + o[2], o[1] + o[3], line);
            Gl.fill(o[0], o[1] + 1, o[0] + 1, o[1] + o[3] - 1, line);
            Gl.fill(o[0] + o[2] - 1, o[1] + 1, o[0] + o[2], o[1] + o[3] - 1, line);

            HudPos hp = livePos != null && (e == dragging || e == resizing) ? livePos : e.pos();
            double[] hc = handleCenter(hp, o);
            Gl.fill(hc[0] - 2.5, hc[1] - 2.5, hc[0] + 2.5, hc[1] + 2.5, line);
        }

        // Center-snap guides while dragging.
        if (dragging != null && snapX)
            Gl.fill(sr.getScaledWidth() / 2.0, 0,
                    sr.getScaledWidth() / 2.0 + 1, sr.getScaledHeight(), 0xC8FFFFFF);
        if (dragging != null && snapY)
            Gl.fill(0, sr.getScaledHeight() / 2.0,
                    sr.getScaledWidth(), sr.getScaledHeight() / 2.0 + 1, 0xC8FFFFFF);

        if (quick) drawQuickHeader(mouseX, mouseY);
    }

    // The quick-header chrome is drawn in the screen's native GUI units and
    // centered EXACTLY like the modern module: button top at screen vertical
    // center (height-28)/2, everything stacked above it, all on cx=width/2.
    // The old eff-2 / lower-third math was what made it read off-center.
    private static final int BTN_W = 132, BTN_H = 28;
    private int quickBtnX() { return (width - BTN_W) / 2; }
    private int quickBtnY() { return (height - BTN_H) / 2; }

    private void drawQuickHeader(int mouseX, int mouseY) {
        double cx = width / 2.0;
        int bt = quickBtnY();
        OriginUi.glow(cx, bt - 76, 104, 0.16);
        OriginUi.logo(cx, bt - 76, 46, 1.0);
        OriginScreenRenderer.drawWordmark(cx, bt - 44, 13, 1f);

        boolean hover = in(mouseX, mouseY, quickBtnX(), bt, BTN_W, BTN_H);
        OriginUi.panel(quickBtnX(), bt, BTN_W, BTN_H, 9,
                       hover ? 0xE6181818 : 0xD0101010,
                       hover ? OriginTheme.STROKE_HOVER : OriginTheme.STROKE_STRONG);
        OriginUi.label("MODS", cx, bt + 14, OriginTheme.TEXT);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private HudElements.Element hovered(int mouseX, int mouseY, ScaledResolution sr) {
        // Last hit wins — matches draw order (later elements draw on top).
        HudElements.Element hit = null;
        for (HudElements.Element e : HudElements.all()) {
            if (!e.on()) continue;
            double[] o = outer(HudElements.bounds(e, sr, true));
            if (in(mouseX, mouseY, o[0], o[1], o[2], o[3])) hit = e;
        }
        return hit;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        try {
            if (mouseButton != 0) return;
            ScaledResolution sr = new ScaledResolution(mc);

            if (quick && in(mouseX, mouseY, quickBtnX(), quickBtnY(), BTN_W, BTN_H)) {
                mc.displayGuiScreen(new com.origin.client.gui.OriginModMenuScreen());
                return;
            }

            // Resize handles first — they sit on the outline, outside bounds.
            for (HudElements.Element e : HudElements.all()) {
                if (!e.on()) continue;
                HudPos pos = e.pos();
                double[] o = outer(HudElements.bounds(e, sr, true));
                double[] hc = handleCenter(pos, o);
                if (Math.abs(mouseX - hc[0]) <= 6 && Math.abs(mouseY - hc[1]) <= 6) {
                    resizing = e;
                    selected = e;
                    beginLive(e);
                    grabScale = pos.scale;
                    double[] ac = anchorCorner(pos, o);
                    grabDist = Math.max(1, Math.hypot(mouseX - ac[0], mouseY - ac[1]));
                    resizeEngaged = false;
                    return;
                }
            }

            HudElements.Element hit = hovered(mouseX, mouseY, sr);
            if (hit != null) {
                int[] b = HudElements.bounds(hit, sr, true);
                dragging = hit;
                selected = hit;
                beginLive(hit);
                grabDX = mouseX - b[0];
                grabDY = mouseY - b[1];
            } else {
                selected = null;
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        try {
            ScaledResolution sr = new ScaledResolution(mc);
            int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

            if (resizing != null && livePos != null) {
                double[] o = outer(HudElements.bounds(resizing, sr, true));
                double[] ac = anchorCorner(livePos, o);
                double distNow = Math.hypot(mouseX - ac[0], mouseY - ac[1]);
                if (!resizeEngaged && Math.abs(distNow - grabDist) < 3) return;
                resizeEngaged = true;
                livePos.scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, grabScale * (distNow / grabDist)));
                // Growing must never push the element off-screen: shrink the
                // scale until the recomputed bounds fit.
                for (int guard = 0; guard < 8; guard++) {
                    int[] nb = HudElements.bounds(resizing, sr, true);
                    if (nb[0] >= 0 && nb[1] >= 0 && nb[0] + nb[2] <= sw && nb[1] + nb[3] <= sh) break;
                    livePos.scale = Math.max(SCALE_MIN, livePos.scale - 0.05);
                    if (livePos.scale <= SCALE_MIN) break;
                }
                return;
            }

            if (dragging != null && livePos != null) {
                int[] b = HudElements.bounds(dragging, sr, true);
                double absX = mouseX - grabDX;
                double absY = mouseY - grabDY;
                // Assistive center snap (6px) with a visible guide line.
                snapX = Math.abs(absX + b[2] / 2.0 - sw / 2.0) < 6;
                snapY = Math.abs(absY + b[3] / 2.0 - sh / 2.0) < 6;
                if (snapX) absX = sw / 2.0 - b[2] / 2.0;
                if (snapY) absY = sh / 2.0 - b[3] / 2.0;
                // Elements stay fully on-screen — off-screen HUD is never useful.
                absX = Math.max(0, Math.min(absX, sw - b[2]));
                absY = Math.max(0, Math.min(absY, sh - b[3]));
                livePos.setFromAbsolute(absX, absY, b[2], b[3], sw, sh);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        try {
            commitLive(); // the ONE disk write for the whole gesture
            dragging = null;
            resizing = null;
            snapX = snapY = false;
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        try {
            int wheel = Mouse.getEventDWheel();
            if (wheel == 0) return;
            ScaledResolution sr = new ScaledResolution(mc);
            int mx = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
            int my = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
            HudElements.Element hit = hovered(mx, my, sr);
            if (hit == null) return;
            HudPos pos = hit.pos();
            pos.scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, pos.scale + (wheel > 0 ? 0.05 : -0.05)));
            pos.save(hit.id);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        try {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
                mc.displayGuiScreen(null);
                return;
            }
            if (keyCode == Keyboard.KEY_R) {
                ScaledResolution sr = new ScaledResolution(mc);
                // GuiScreen doesn't hand keyTyped a mouse position; derive it
                // the same way vanilla does for drawScreen.
                int mx = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
                int my = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
                HudElements.Element hit = hovered(mx, my, sr);
                if (hit != null) ModsConfig.resetHud(hit.id);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }
}
