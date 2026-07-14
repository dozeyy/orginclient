package com.origin.client.render;

import com.origin.client.theme.OriginTheme;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Replacement for vanilla's LoadingScreenRenderer (the singleplayer
 * "Loading world / Building terrain 27%" screen — NOT a GuiScreen, it draws
 * directly and swaps buffers itself). Swapped into Minecraft.loadingScreen by
 * OriginRuntime; every public entry vanilla calls is overridden, so the
 * private vanilla draw path is never reached.
 *
 * Fail-soft: while OriginScreenRenderer is broken every call delegates to the
 * vanilla super implementation instead.
 */
public final class OriginLoadingRenderer extends LoadingScreenRenderer {

    private final Minecraft mc;
    private String title = "";
    private String stage = "";
    private int progress = -1;
    private long openedAt;
    private long lastDraw;

    public OriginLoadingRenderer(Minecraft mc) {
        super(mc);
        this.mc = mc;
    }

    @Override
    public void resetProgressAndMessage(String message) {
        if (!OriginScreenRenderer.isActive()) { super.resetProgressAndMessage(message); return; }
        title = message != null ? message : "";
        stage = "";
        progress = -1;
        openedAt = System.currentTimeMillis();
        draw(true);
    }

    @Override
    public void displaySavingString(String message) {
        if (!OriginScreenRenderer.isActive()) { super.displaySavingString(message); return; }
        title = message != null ? message : "";
        stage = "";
        progress = -1;
        if (openedAt == 0) openedAt = System.currentTimeMillis();
        draw(true);
    }

    @Override
    public void displayLoadingString(String message) {
        if (!OriginScreenRenderer.isActive()) { super.displayLoadingString(message); return; }
        stage = message != null ? message : "";
        progress = -1;
        if (openedAt == 0) openedAt = System.currentTimeMillis();
        draw(true);
    }

    @Override
    public void setLoadingProgress(int p) {
        if (!OriginScreenRenderer.isActive()) { super.setLoadingProgress(p); return; }
        progress = p;
        draw(false);
    }

    @Override
    public void setDoneWorking() {
        if (!OriginScreenRenderer.isActive()) { super.setDoneWorking(); return; }
        progress = -1;
        stage = "";
        openedAt = 0;
    }

    private void draw(boolean force) {
        long now = System.currentTimeMillis();
        // Vanilla throttles to 10fps here so loading isn't slowed by drawing;
        // 30fps keeps the bar smooth at negligible cost.
        if (!force && now - lastDraw < 33) return;
        lastDraw = now;
        try {
            if (!Display.isCreated()) return;
            ScaledResolution sr = new ScaledResolution(mc);
            int w = sr.getScaledWidth(), h = sr.getScaledHeight();

            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0.0, 100.0, 300.0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0f, 0f, -200f);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();

            if (!OriginScreenRenderer.renderMenuBackdrop(w, h)) return;

            long elapsed = now - (openedAt == 0 ? now : openedAt);
            double markCenterY = h * 0.48;
            double inkH = OriginScreenRenderer.fitInkHeight(h * 0.135, w, 0.82);
            OriginScreenRenderer.drawWordmarkReveal(w / 2.0, markCenterY, inkH, elapsed);

            double barW = OriginScreenRenderer.wordmarkDisplayWidth(inkH);
            double barH = Math.max(3, Math.round(h * 0.012));
            double barTop = Math.round(markCenterY + inkH * 1.15);
            if (progress >= 0 && progress <= 100)
                OriginScreenRenderer.drawBar((w - barW) / 2.0, barTop, barW, barH, progress / 100.0);
            else
                OriginScreenRenderer.drawIndeterminateBar((w - barW) / 2.0, barTop, barW, barH);

            // Stage line ("Building terrain") in MUTED below the bar; falls
            // back to the title so the screen is never wordless.
            String line = stage.length() > 0 ? stage : title;
            if (line.length() > 0) {
                int tw = mc.fontRendererObj.getStringWidth(line);
                mc.fontRendererObj.drawString(line, (w - tw) / 2f,
                    (float) (barTop + barH + Math.max(8, Math.round(h * 0.035))), OriginTheme.MUTED, false);
            }

            mc.updateDisplay();
        } catch (Throwable t) {
            OriginScreenRenderer.broken = true;
            System.err.println("[OriginClient] loading renderer disabled: " + t);
        }
    }
}
