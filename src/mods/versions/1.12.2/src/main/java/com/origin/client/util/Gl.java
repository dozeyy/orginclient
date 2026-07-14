package com.origin.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Low-level drawing kit for the 1.12.2 port. Every Origin draw goes through
 * these helpers so the GL discipline from the modern modules (blend on before
 * textured draws, color reset to opaque white after every tint) lives in ONE
 * place instead of being re-remembered at each call site.
 */
public final class Gl {

    private Gl() {}

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    // ---- Solid fills (float coords — GUI-space subpixel positioning) ----

    public static void fill(double x0, double y0, double x1, double y1, int argb) {
        if (x1 < x0) { double t = x0; x0 = x1; x1 = t; }
        if (y1 < y0) { double t = y0; y0 = y1; y1 = t; }
        float a = (argb >>> 24 & 0xFF) / 255f;
        if (a <= 0f) return;
        float r = (argb >>> 16 & 0xFF) / 255f;
        float g = (argb >>> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormats.POSITION);
        wr.pos(x0, y1, 0.0).endVertex();
        wr.pos(x1, y1, 0.0).endVertex();
        wr.pos(x1, y0, 0.0).endVertex();
        wr.pos(x0, y0, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Vertical gradient fill (top color to bottom color). */
    public static void fillGradient(double x0, double y0, double x1, double y1, int top, int bottom) {
        float ta = (top >>> 24 & 0xFF) / 255f, tr = (top >>> 16 & 0xFF) / 255f,
              tg = (top >>> 8 & 0xFF) / 255f, tb = (top & 0xFF) / 255f;
        float ba = (bottom >>> 24 & 0xFF) / 255f, br = (bottom >>> 16 & 0xFF) / 255f,
              bg = (bottom >>> 8 & 0xFF) / 255f, bb = (bottom & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y0, 0.0).color(tr, tg, tb, ta).endVertex();
        wr.pos(x0, y0, 0.0).color(tr, tg, tb, ta).endVertex();
        wr.pos(x0, y1, 0.0).color(br, bg, bb, ba).endVertex();
        wr.pos(x1, y1, 0.0).color(br, bg, bb, ba).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---- Textured quads ----

    /**
     * Bind an Origin texture and force GL_LINEAR filtering (no mipmap) — the
     * modern modules register these with linear filtering so panels/rings stay
     * smooth at any scale; 1.12.2's SimpleTexture defaults to nearest.
     */
    public static void bindLinear(ResourceLocation tex) {
        mc().getTextureManager().bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    /** Full-texture quad, tinted white at alpha. Texture must be bound. */
    public static void texQuad(double x, double y, double w, double h, float alpha) {
        texQuadUv(x, y, w, h, 0.0, 0.0, 1.0, 1.0, 1f, 1f, 1f, alpha);
    }

    /** Sub-region quad with explicit UVs and tint. Texture must be bound. */
    public static void texQuadUv(double x, double y, double w, double h,
                                 double u0, double v0, double u1, double v1,
                                 float r, float g, float b, float alpha) {
        if (alpha <= 0f) return;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0.0).tex(u0, v1).endVertex();
        wr.pos(x + w, y + h, 0.0).tex(u1, v1).endVertex();
        wr.pos(x + w, y, 0.0).tex(u1, v0).endVertex();
        wr.pos(x, y, 0.0).tex(u0, v0).endVertex();
        tessellator.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---- Scissor (GUI-space coords; converts to bottom-up device pixels) ----

    public static void enableScissor(int x0, int y0, int x1, int y1) {
        ScaledResolution sr = new ScaledResolution(mc());
        enableScissorScaled(x0, y0, x1, y1, sr.getScaleFactor());
    }

    /**
     * Scissor for screens that draw in a non-GUI unit space (the Origin menus
     * render at a fixed effective scale of 2 physical px per unit, independent
     * of the game's GUI-scale setting — see OriginModMenuScreen).
     */
    public static void enableScissorScaled(int x0, int y0, int x1, int y1, double pixelsPerUnit) {
        int px = (int) Math.floor(x0 * pixelsPerUnit);
        int py = (int) Math.floor(mc().displayHeight - y1 * pixelsPerUnit);
        int pw = Math.max(0, (int) Math.ceil((x1 - x0) * pixelsPerUnit));
        int ph = Math.max(0, (int) Math.ceil((y1 - y0) * pixelsPerUnit));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px, py, pw, ph);
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
