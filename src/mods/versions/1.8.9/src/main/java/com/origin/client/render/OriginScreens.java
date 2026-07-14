package com.origin.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.origin.client.gui.OriginUi;
import com.origin.client.mods.Mods;
import com.origin.client.theme.OriginTheme;
import com.origin.client.util.Gl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonLanguage;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The screen restyler: full-render takeover of the title screen, connecting
 * screen, download-terrain and working screens (Origin scene + restyled
 * vanilla widgets, same widget OBJECTS so behavior is untouched), plus the
 * subtle frame + cursor glow on every other out-of-world menu.
 *
 * No mixins — everything hangs off GuiScreenEvent, so there is no
 * mixin-apply failure mode at all. The takeover only cancels vanilla drawing
 * AFTER the Origin scene drew successfully (OriginScreenRenderer latches
 * broken on any throw and isActive() goes false), so failure degrades to
 * vanilla screens, never a black screen.
 */
public final class OriginScreens {

    /** Live buttonList per screen instance, captured at InitGuiEvent.Post. */
    private final Map<GuiScreen, List<GuiButton>> buttons = new WeakHashMap<GuiScreen, List<GuiButton>>();

    /** Per-button eased hover state: [hoverT, lastMs]. */
    private final Map<GuiButton, double[]> hover = new WeakHashMap<GuiButton, double[]>();

    // Account chip skin (resolved async once per session).
    private static ResourceLocation skin;
    private static boolean skinRequested;

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    // ------------------------------------------------------------------
    // Screen init: capture buttons, hide the language + Forge-Mods buttons
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        buttons.put(event.gui, event.buttonList);
        if (event.gui instanceof GuiMainMenu && originStyleActive()) {
            String modsLabel = I18n.format("fml.menu.mods");
            for (GuiButton b : event.buttonList) {
                // The language icon button and Forge's "Mods" list don't exist
                // on Origin's title screen (Origin has its own mods surface).
                if (b instanceof GuiButtonLanguage || modsLabel.equals(b.displayString)) {
                    b.visible = false;
                    b.enabled = false;
                }
            }
        }
    }

    private static boolean originStyleActive() {
        return OriginScreenRenderer.isActive() && Mods.originTitleStyle();
    }

    // ------------------------------------------------------------------
    // Draw takeovers
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        GuiScreen gui = event.gui;
        if (!originStyleActive()) return;
        try {
            if (gui instanceof GuiMainMenu) {
                if (drawTitle((GuiMainMenu) gui, event.mouseX, event.mouseY)) event.setCanceled(true);
            } else if (gui instanceof GuiConnecting) {
                if (drawConnecting(gui, event.mouseX, event.mouseY)) event.setCanceled(true);
            } else if (gui instanceof GuiDownloadTerrain) {
                if (drawScene(gui, I18n.format("multiplayer.downloadingTerrain"), -1)) event.setCanceled(true);
            } else if (gui instanceof GuiScreenWorking) {
                if (drawWorking((GuiScreenWorking) gui)) event.setCanceled(true);
            }
        } catch (Throwable t) {
            OriginScreenRenderer.broken = true;
            System.err.println("[OriginClient] screen takeover disabled: " + t);
        }
    }

    /** Subtle Origin frame + cursor spotlight on generic out-of-world menus. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.gui;
        if (!OriginScreenRenderer.isActive()) return;
        if (mc().theWorld != null) return;
        if (gui instanceof GuiMainMenu || gui instanceof GuiConnecting
            || gui instanceof GuiDownloadTerrain || gui instanceof GuiScreenWorking) return;
        // Generic menus keep their own (dark, texture-overridden) background —
        // this only adds the corner brackets + spotlight so they read Origin.
        OriginScreenRenderer.renderCursorGlow(gui.width, event.mouseX, event.mouseY, false);
    }

    private boolean drawTitle(GuiMainMenu gui, int mouseX, int mouseY) {
        int w = gui.width, h = gui.height;
        if (!OriginScreenRenderer.renderTitleBackground(w, h)) return false;

        List<GuiButton> list = buttons.get(gui);
        boolean hoveringAny = false;
        if (list != null) {
            for (GuiButton b : list) {
                if (b.visible && b.enabled && contains(b, mouseX, mouseY)) { hoveringAny = true; break; }
            }
        }
        OriginScreenRenderer.renderCursorGlow(w, mouseX, mouseY, hoveringAny);
        drawAccountChip(w);

        // Wordmark centered between screen top and the first button row.
        int singleplayerTop = h / 4 + 48;
        double inkH = OriginScreenRenderer.fitInkHeight(h * 0.13, w, 0.82);
        OriginScreenRenderer.drawWordmarkBreathing(w / 2.0, singleplayerTop / 2.0, inkH);

        if (list != null) {
            for (GuiButton b : list) {
                if (!b.visible) continue;
                drawOriginButton(b, mouseX, mouseY);
            }
        }
        return true;
    }

    private boolean drawConnecting(GuiScreen gui, int mouseX, int mouseY) {
        if (!drawScene(gui, I18n.format("connect.connecting"), -1)) return false;
        List<GuiButton> list = buttons.get(gui);
        if (list != null) {
            for (GuiButton b : list) {
                if (b.visible) drawOriginButton(b, mouseX, mouseY);
            }
        }
        return true;
    }

    private boolean drawWorking(GuiScreenWorking gui) {
        // GuiScreenWorking's title/stage/progress fields are private with no
        // accessors; read them order-based (first two String fields, first
        // int) so this survives dev-vs-production name remapping.
        String title = null, stage = null;
        int progress = -1;
        try {
            int strings = 0;
            for (Field f : GuiScreenWorking.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == String.class) {
                    String v = (String) f.get(gui);
                    if (strings == 0) title = v; else if (strings == 1) stage = v;
                    strings++;
                } else if (f.getType() == int.class) {
                    progress = f.getInt(gui);
                }
            }
        } catch (Throwable ignored) {
            // Fall through with what we have; a null title still renders.
        }
        String line = title != null && title.length() > 0 ? title
                    : (stage != null ? stage : "Working...");
        return drawScene(gui, line, progress / 100.0);
    }

    /** The shared loading scene: backdrop + centered title + bar. */
    private boolean drawScene(GuiScreen gui, String title, double progress) {
        int w = gui.width, h = gui.height;
        if (!OriginScreenRenderer.renderMenuBackdrop(w, h)) return false;
        double barW = Math.max(80, w * 0.30);
        double barH = Math.max(2, h * 0.010);
        double gap = Math.max(8, h * 0.045);
        double groupH = 9 * 1.5 + gap + barH;
        double top = (h - groupH) / 2.0;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) (w / 2.0), (float) (top + 6), 0f);
        GlStateManager.scale(1.5f, 1.5f, 1f);
        OriginUi.label(title, 0, 4, OriginTheme.TEXT);
        GlStateManager.popMatrix();
        double barY = top + 9 * 1.5 + gap;
        if (progress >= 0 && progress <= 1)
            OriginScreenRenderer.drawBar((w - barW) / 2.0, barY, barW, barH, progress);
        else
            OriginScreenRenderer.drawIndeterminateBar((w - barW) / 2.0, barY, barW, barH);
        return true;
    }

    // ------------------------------------------------------------------
    // Origin-styled vanilla buttons (drawn manually; behavior untouched)
    // ------------------------------------------------------------------

    private void drawOriginButton(GuiButton b, int mouseX, int mouseY) {
        boolean hov = b.enabled && contains(b, mouseX, mouseY);
        double[] st = hover.get(b);
        long now = System.currentTimeMillis();
        if (st == null) { st = new double[]{0, now}; hover.put(b, st); }
        double dt = Math.min(100, now - st[1]);
        st[1] = now;
        st[0] += (hov ? 1 : -1) * dt / 90.0;
        st[0] = OriginTheme.clamp01(st[0]);
        double t = OriginTheme.easeOut(st[0]);

        OriginUi.button(b.xPosition, b.yPosition, b.width, b.height, t, b.enabled);
        String label = cleanLabel(b.displayString);
        OriginUi.label(label, b.xPosition + b.width / 2.0, b.yPosition + b.height / 2.0,
                       b.enabled ? OriginTheme.TEXT : OriginTheme.MUTED);
    }

    private static boolean contains(GuiButton b, int mx, int my) {
        return mx >= b.xPosition && mx < b.xPosition + b.width
            && my >= b.yPosition && my < b.yPosition + b.height;
    }

    /** Strip trailing dots/ellipsis: "Options..." -> "Options". */
    private static String cleanLabel(String s) {
        if (s == null) return "";
        String out = s;
        while (out.endsWith(".") || out.endsWith("…"))
            out = out.substring(0, out.length() - 1);
        return out;
    }

    // ------------------------------------------------------------------
    // Account chip
    // ------------------------------------------------------------------

    private void drawAccountChip(int w) {
        Minecraft mc = mc();
        String name = mc.getSession().getUsername();
        int head = 18, padX = 8, padY = 6, gap = 8;
        int chipH = head + 2 * padY;
        int chipW = padX + head + gap + mc.fontRendererObj.getStringWidth(name) + padX;
        int x = Math.max(10, (int) Math.round(w * 0.03));
        int y = x;
        OriginUi.panel(x, y, chipW, chipH, OriginTheme.RADIUS_MD, OriginTheme.PANEL_TRANSLUCENT, 0);

        ResourceLocation face = resolveSkin();
        if (face != null) {
            mc.getTextureManager().bindTexture(face);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(x + padX, y + padY, 8f, 8f, 8, 8, head, head, 64f, 64f);
            Gui.drawScaledCustomSizeModalRect(x + padX, y + padY, 40f, 8f, 8, 8, head, head, 64f, 64f);
        } else {
            OriginUi.logo(x + padX + head / 2.0, y + padY + head / 2.0, head, 1.0);
        }
        mc.fontRendererObj.drawString(name, x + padX + head + gap, y + (chipH - 8) / 2, OriginTheme.TEXT, false);
    }

    private static ResourceLocation resolveSkin() {
        if (skin != null) return skin;
        if (skinRequested) return null;
        skinRequested = true;
        try {
            GameProfile profile = mc().getSession().getProfile();
            mc().getSkinManager().loadProfileTextures(profile, new SkinManager.SkinAvailableCallback() {
                @Override
                public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location,
                                          MinecraftProfileTexture profileTexture) {
                    if (type == MinecraftProfileTexture.Type.SKIN) skin = location;
                }
            }, true);
        } catch (Throwable ignored) {
            // Offline / no profile — the ring logo fallback stays.
        }
        return skin;
    }
}
