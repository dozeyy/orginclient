package com.origin.client.gui;

import com.origin.client.theme.OriginTheme;
import com.origin.client.util.Gl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * The Origin shader browser — one-click downloads of curated OptiFine-
 * compatible packs (the legacy counterpart of the modern module's
 * ShaderBrowserScreen inside Iris). Every entry is a pinned Modrinth CDN
 * file verified to work on 1.7.10+ OptiFine; downloads land in the
 * instance's shaderpacks/ and OptiFine's own Shaders screen (reachable from
 * the chip up top) enables them.
 *
 * Renders in the same fixed-eff-2 unit space as OriginModMenuScreen.
 */
public class OriginShaderBrowserScreen extends GuiScreen {

    private static final class Pack {
        final String name, desc, fileName, url;
        Pack(String name, String desc, String fileName, String url) {
            this.name = name;
            this.desc = desc;
            this.fileName = fileName;
            this.url = url;
        }
    }

    // Curated for the legacy versions: Sildur's targets 1.7.10+ explicitly.
    // Ordered lightest -> heaviest so the list doubles as a perf guide.
    private static final Pack[] PACKS = {
        new Pack("Sildur's Enhanced Default (Fast)", "Vanilla look + shadows — the lightest option",
            "Sildur's Enhanced Default v1.19 Fast.zip",
            "https://cdn.modrinth.com/data/2jvH1Rcl/versions/sNDsk0x8/Sildur%27s%20Enhanced%20Default%20v1.19%20Fast.zip"),
        new Pack("Sildur's Enhanced Default (Fancy)", "Vanilla look + shadows and water effects",
            "Sildur's Enhanced Default v1.19 Fancy.zip",
            "https://cdn.modrinth.com/data/2jvH1Rcl/versions/iGZzk9Qi/Sildur%27s%20Enhanced%20Default%20v1.19%20Fancy.zip"),
        new Pack("Sildur's Vibrant Lite", "Full shading on a budget — great FPS",
            "Sildur's Vibrant Shaders v2.01 Lite.zip",
            "https://cdn.modrinth.com/data/z8EjLYqN/versions/bxUlVfyu/Sildur%27s%20Vibrant%20Shaders%20v2.01%20Lite.zip"),
        new Pack("Sildur's Vibrant Medium", "The balanced pick",
            "Sildur's Vibrant Shaders v2.01 Medium.zip",
            "https://cdn.modrinth.com/data/z8EjLYqN/versions/qxwBWio0/Sildur%27s%20Vibrant%20Shaders%20v2.01%20Medium.zip"),
        new Pack("Sildur's Vibrant High", "Rich lighting and water",
            "Sildur's Vibrant Shaders v2.01 High.zip",
            "https://cdn.modrinth.com/data/z8EjLYqN/versions/tQJEI9MM/Sildur%27s%20Vibrant%20Shaders%20v2.01%20High.zip"),
        new Pack("Sildur's Vibrant Extreme", "Everything on — needs a strong GPU",
            "Sildur's Vibrant Shaders v2.01 Extreme.zip",
            "https://cdn.modrinth.com/data/z8EjLYqN/versions/BO6K7uVU/Sildur%27s%20Vibrant%20Shaders%20v2.01%20Extreme.zip"),
    };

    /** Download states by fileName: absent = idle, 0 = running, 1 = done, -1 = failed. */
    private static final Map<String, Integer> STATE = new HashMap<String, Integer>();

    private final GuiScreen parent;
    private double scroll, scrollTarget;
    private double px, py, pw, ph, x1;
    private boolean failed;

    public OriginShaderBrowserScreen(GuiScreen parent) {
        this.parent = parent;
    }

    private void fail(Throwable t) {
        if (!failed) System.err.println("[OriginClient] shader browser failed, closing: " + t);
        failed = true;
        mc.displayGuiScreen(null);
    }

    // Same fixed-eff-2 rendering scheme as OriginModMenuScreen.
    @Override
    public void setWorldAndResolution(Minecraft mcIn, int w, int h) {
        super.setWorldAndResolution(mcIn, Math.max(320, mcIn.displayWidth / 2), Math.max(240, mcIn.displayHeight / 2));
    }

    private float uiToGui() {
        return 2f / new ScaledResolution(mc).getScaleFactor();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private FontRenderer font() { return mc.fontRenderer; }

    private static boolean in(int mx, int my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void layout() {
        px = width * 0.125;
        py = height * 0.125;
        pw = width * 0.75;
        ph = height * 0.75;
        x1 = px + pw - 24;
    }

    private File shaderpacksDir() {
        File dir = new File(mc.gameDir, "shaderpacks");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    // ------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            // drawScreen mouse args arrive in GUI units (EntityRenderer),
            // clicks in screen-field units — same conversion as the mod menu.
            float s = uiToGui();
            int mx = (int) Math.round(mouseX / s);
            int my = (int) Math.round(mouseY / s);
            GlStateManager.pushMatrix();
            GlStateManager.scale(s, s, 1f);
            drawInner(mx, my);
            GlStateManager.popMatrix();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawInner(int mouseX, int mouseY) {
        layout();
        boolean backed = com.origin.client.mods.Mods.panelBacking();
        if (backed) OriginUi.panel(px, py, pw, ph, 10, 0xC80E0E0E, OriginTheme.STROKE);

        // Header: back chip, logo, title, folder + OptiFine chips.
        boolean backHover = in(mouseX, mouseY, px + 24, py + 12, 24, 20);
        OriginUi.panel(px + 24, py + 12, 24, 20, 6, OriginUi.chipFill(backHover, backed),
                       backHover ? OriginTheme.STROKE_HOVER : OriginTheme.STROKE);
        OriginUi.label("<", px + 36, py + 21, backHover ? OriginTheme.TEXT : OriginTheme.TEXT_DIM);
        OriginUi.logo(px + 70, py + 22, 24, 1.0);
        OriginUi.labelLeft("SHADERS", px + 88, py + 21, OriginTheme.TEXT);

        String ofLabel = "Shader Options";
        double ofW = font().getStringWidth(ofLabel) + 16;
        double ofX = x1 - ofW;
        boolean ofHover = in(mouseX, mouseY, ofX, py + 12, ofW, 20);
        OriginUi.panel(ofX, py + 12, ofW, 20, 6, OriginUi.chipFill(ofHover, backed),
                       ofHover ? OriginTheme.STROKE_HOVER : OriginTheme.STROKE);
        OriginUi.label(ofLabel, ofX + ofW / 2.0, py + 21, ofHover ? OriginTheme.TEXT : OriginTheme.TEXT_DIM);

        String fdLabel = "Open Folder";
        double fdW = font().getStringWidth(fdLabel) + 16;
        double fdX = ofX - 8 - fdW;
        boolean fdHover = in(mouseX, mouseY, fdX, py + 12, fdW, 20);
        OriginUi.panel(fdX, py + 12, fdW, 20, 6, OriginUi.chipFill(fdHover, backed),
                       fdHover ? OriginTheme.STROKE_HOVER : OriginTheme.STROKE);
        OriginUi.label(fdLabel, fdX + fdW / 2.0, py + 21, fdHover ? OriginTheme.TEXT : OriginTheme.TEXT_DIM);

        // Pack rows.
        double top = py + 44, bottom = py + ph - 14;
        double maxScroll = Math.max(0, PACKS.length * 34 - (bottom - top));
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll += (scrollTarget - scroll) * 0.45;

        Gl.enableScissorScaled((int) px, (int) top, (int) (px + pw), (int) bottom, 2);
        double y = top + 2 - scroll;
        for (Pack pack : PACKS) {
            drawPackRow(pack, px + 24, y, mouseX, mouseY, backed, top, bottom);
            y += 34;
        }
        Gl.disableScissor();
    }

    private void drawPackRow(Pack pack, double rx, double ry, int mouseX, int mouseY,
                             boolean backed, double top, double bottom) {
        boolean mouseInList = mouseY >= top && mouseY < bottom;
        OriginUi.panel(rx, ry, x1 - rx, 28, 8, backed ? 0x10FFFFFF : 0xC0101010, OriginTheme.STROKE);
        OriginUi.labelLeft(pack.name, rx + 10, ry + 9, OriginTheme.TEXT);
        OriginUi.labelLeft(pack.desc, rx + 10, ry + 20, OriginTheme.MUTED);

        boolean present = new File(shaderpacksDir(), pack.fileName).isFile();
        Integer st = STATE.get(pack.fileName);
        String label;
        int textColor, edge, fill;
        if (present || (st != null && st == 1)) {
            label = "DOWNLOADED";
            textColor = OriginTheme.GREEN_TEXT; edge = OriginTheme.GREEN_EDGE; fill = OriginTheme.GREEN_FILL;
        } else if (st != null && st == 0) {
            label = "DOWNLOADING" + dots();
            textColor = OriginTheme.TEXT_DIM; edge = OriginTheme.STROKE_STRONG; fill = 0x14FFFFFF;
        } else if (st != null && st == -1) {
            label = "RETRY";
            textColor = OriginTheme.RED_TEXT; edge = OriginTheme.RED_EDGE; fill = OriginTheme.RED_FILL;
        } else {
            label = "DOWNLOAD";
            textColor = OriginTheme.TEXT; edge = OriginTheme.STROKE_STRONG; fill = 0x14FFFFFF;
        }
        double bw = 96;
        boolean hover = mouseInList && in(mouseX, mouseY, x1 - 10 - bw, ry + 5, bw, 18);
        if (hover && !present) fill = OriginTheme.withAlpha(fill, 0x2E);
        OriginUi.panel(x1 - 10 - bw, ry + 5, bw, 18, 7, fill, edge);
        OriginUi.label(label, x1 - 10 - bw / 2.0, ry + 14, textColor);
    }

    private static String dots() {
        int n = (int) ((System.currentTimeMillis() / 400) % 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('.');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            if (mouseButton != 0) return;
            layout();

            if (in(mouseX, mouseY, px + 24, py + 12, 24, 20)) { goBack(); return; }

            String ofLabel = "Shader Options";
            double ofW = font().getStringWidth(ofLabel) + 16;
            double ofX = x1 - ofW;
            if (in(mouseX, mouseY, ofX, py + 12, ofW, 20)) { openOptiFineShaders(); return; }

            double fdW = font().getStringWidth("Open Folder") + 16;
            if (in(mouseX, mouseY, ofX - 8 - fdW, py + 12, fdW, 20)) { openFolder(); return; }

            double top = py + 44, bottom = py + ph - 14;
            if (mouseY < top || mouseY >= bottom) return;
            double y = top + 2 - scroll;
            for (final Pack pack : PACKS) {
                double bw = 96;
                if (in(mouseX, mouseY, x1 - 10 - bw, y + 5, bw, 18)) {
                    startDownload(pack);
                    return;
                }
                y += 34;
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void goBack() {
        mc.displayGuiScreen(parent != null ? parent : null);
    }

    private void startDownload(final Pack pack) {
        final File dest = new File(shaderpacksDir(), pack.fileName);
        if (dest.isFile()) return;
        Integer st = STATE.get(pack.fileName);
        if (st != null && st == 0) return;
        STATE.put(pack.fileName, 0);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                File tmp = new File(dest.getParentFile(), dest.getName() + ".download");
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(pack.url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "OriginClient shader browser");
                    InputStream in = conn.getInputStream();
                    OutputStream out = new FileOutputStream(tmp);
                    try {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    } finally {
                        out.close();
                        in.close();
                    }
                    if (!tmp.renameTo(dest)) throw new java.io.IOException("rename failed");
                    STATE.put(pack.fileName, 1);
                } catch (Throwable e) {
                    tmp.delete();
                    STATE.put(pack.fileName, -1);
                    System.err.println("[OriginClient] shader download failed: " + e);
                }
            }
        }, "Origin-ShaderDownload");
        t.setDaemon(true);
        t.start();
    }

    /**
     * OptiFine's shader screen, by reflection — OptiFine is a runtime
     * neighbor the compile never sees. Absent OptiFine (someone removed the
     * jar) this quietly does nothing.
     */
    private void openOptiFineShaders() {
        try {
            Class<?> cls = Class.forName("net.optifine.shaders.gui.GuiShaders");
            mc.displayGuiScreen((GuiScreen) cls
                .getConstructor(GuiScreen.class, net.minecraft.client.settings.GameSettings.class)
                .newInstance(this, mc.gameSettings));
        } catch (Throwable t) {
            System.err.println("[OriginClient] OptiFine shaders screen unavailable: " + t);
        }
    }

    private void openFolder() {
        try {
            // LWJGL's opener avoids the AWT/game-thread deadlocks Desktop.open
            // is notorious for under Minecraft.
            org.lwjgl.Sys.openURL("file://" + shaderpacksDir().getAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[OriginClient] open folder failed: " + t);
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        try {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) scrollTarget += wheel > 0 ? -30 : 30;
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        try {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) goBack();
        } catch (Throwable t) {
            fail(t);
        }
    }
}
