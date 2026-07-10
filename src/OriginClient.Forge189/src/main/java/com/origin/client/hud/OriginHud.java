package com.origin.client.hud;

import com.origin.client.OriginFeatures;
import com.origin.client.OriginState;
import com.origin.client.render.OriginGl;
import com.origin.client.theme.OriginTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Origin HUD for the classic versions — an info readout (FPS / coords / facing)
 * and a keystrokes overlay, both Origin-styled and drawn on the Forge overlay
 * event so no HUD mixin is needed. Each element is independently toggleable and
 * fail-soft. Matches the Fabric build's HUD look.
 */
public final class OriginHud {
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        if (mc.gameSettings != null && mc.gameSettings.showDebugInfo) {
            return; // don't stack on the F3 screen
        }
        OriginFeatures f = OriginState.features();
        try {
            if (f.hudInfoEnabled) {
                drawInfo(mc);
            }
            if (f.keystrokesEnabled) {
                drawKeystrokes(mc);
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawInfo(Minecraft mc) {
        EntityPlayer p = mc.thePlayer;
        List<String> lines = new ArrayList<String>();
        lines.add("FPS " + fps(mc));
        lines.add("XYZ " + (int) Math.floor(p.posX) + " " + (int) Math.floor(p.posY) + " " + (int) Math.floor(p.posZ));
        lines.add("Facing " + facing(p.rotationYaw));

        int pad = 6;
        int lineH = OriginGl.fontHeight() + 2;
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, OriginGl.textWidth(s));
        }
        int x = 6, y = 6;
        int panelW = w + pad * 2;
        int panelH = lines.size() * lineH + pad * 2 - 2;

        OriginGl.fill(x, y, x + panelW, y + panelH, OriginTheme.PANEL_TRANSLUCENT);
        OriginGl.fill(x, y, x + 1, y + panelH, OriginTheme.STROKE_STRONG); // left accent

        int ty = y + pad;
        for (String s : lines) {
            OriginGl.text(s, x + pad, ty, OriginTheme.TEXT, false);
            ty += lineH;
        }
    }

    private void drawKeystrokes(Minecraft mc) {
        GameSettings gs = mc.gameSettings;
        if (gs == null) {
            return;
        }
        int box = 22, gap = 2;
        int gridW = box * 3 + gap * 2;
        int x = 6;
        int y = OriginGl.scaledHeight() - (box * 4 + gap * 3) - 6;

        keyBox(x + box + gap, y, box, box, "W", gs.keyBindForward.isKeyDown());
        int r2 = y + box + gap;
        keyBox(x, r2, box, box, "A", gs.keyBindLeft.isKeyDown());
        keyBox(x + box + gap, r2, box, box, "S", gs.keyBindBack.isKeyDown());
        keyBox(x + (box + gap) * 2, r2, box, box, "D", gs.keyBindRight.isKeyDown());
        int r3 = r2 + box + gap;
        int half = (gridW - gap) / 2;
        keyBox(x, r3, half, box, "LMB", gs.keyBindAttack.isKeyDown());
        keyBox(x + half + gap, r3, gridW - half - gap, box, "RMB", gs.keyBindUseItem.isKeyDown());
        int r4 = r3 + box + gap;
        keyBox(x, r4, gridW, box, "___", gs.keyBindJump.isKeyDown());
    }

    private void keyBox(int x, int y, int w, int h, String label, boolean down) {
        OriginGl.fill(x, y, x + w, y + h, down ? OriginTheme.ACCENT : OriginTheme.PANEL_TRANSLUCENT);
        if (label != null && !label.isEmpty()) {
            int col = down ? OriginTheme.BG : OriginTheme.TEXT;
            int tw = OriginGl.textWidth(label);
            OriginGl.text(label, x + (w - tw) / 2, y + (h - OriginGl.fontHeight()) / 2 + 1, col, false);
        }
    }

    /** Cardinal direction from the player yaw (0 = south, clockwise). */
    private String facing(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return dirs[(int) Math.round(y / 45.0) % 8];
    }

    /** FPS from Minecraft's public debug string (debugFPS is private on 1.8.9). */
    private int fps(Minecraft mc) {
        try {
            String d = mc.debug;
            if (d != null) {
                int sp = d.indexOf(' ');
                if (sp > 0) {
                    return Integer.parseInt(d.substring(0, sp).trim());
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
