package com.origin.client.hud;

import com.origin.client.features.ClickTracker;
import com.origin.client.features.ToggleSprint;
import com.origin.client.gui.OriginUi;
import com.origin.client.mods.Mods;
import com.origin.client.theme.OriginTheme;
import com.origin.client.util.Gl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.StringUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Origin HUD: element registry + per-frame renderer. Layout, ids and
 * default positions mirror the modern HudElements so saved HUD layouts carry
 * the same meaning. Every element draw is individually try/caught — one bad
 * element never takes the HUD (or the frame) down.
 */
public final class HudElements {

    public static final int TEXT = 0xFFE0E0E0;

    /** One HUD element. Sizes/draws are in UNSCALED units; the dispatcher applies pos.scale. */
    public abstract static class Element {
        public final String id;
        private final HudPos def;

        Element(String id, int anchor, double dx, double dy) {
            this.id = id;
            this.def = new HudPos(anchor, dx, dy, 1.0);
        }

        public boolean on() { return Mods.isOn(id); }

        public HudPos pos() { return HudPos.load(id, def); }

        /** Measured for "everything on" so the editor outline is stable. */
        public abstract int[] size(boolean preview);

        public abstract void draw(int x, int y, boolean preview);

        boolean showBackground() { return Mods.bool(id, "showBackground"); }

        /** The standard translucent rounded backing, inset -3 like modern. */
        void backing(int x, int y, int w, int h) {
            if (showBackground())
                OriginUi.panel(x - 3, y - 3, w + 6, h + 6, 5, 0x66101010, 0);
        }
    }

    private static final List<Element> ELEMENTS = new ArrayList<Element>();

    static {
        ELEMENTS.add(new FpsElement());
        ELEMENTS.add(new CpsElement());
        ELEMENTS.add(new CoordsElement());
        ELEMENTS.add(new KeystrokesElement());
        ELEMENTS.add(new PotionHudElement());
        ELEMENTS.add(new ArmorHudElement());
        ELEMENTS.add(new ServerAddressElement());
        ELEMENTS.add(new SprintStateElement());
    }

    private HudElements() {}

    public static List<Element> all() { return ELEMENTS; }

    /** Set by HudEditorScreen while open so the game-overlay pass stands down. */
    public static volatile boolean editorOpen = false;

    /** Game-overlay entry point (RenderGameOverlayEvent.Post ALL). */
    public static void renderAll(ScaledResolution sr) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.hideGUI || editorOpen) return;
        for (Element e : ELEMENTS) {
            if (!e.on()) continue;
            try {
                drawScaled(e, sr, false);
            } catch (Throwable t) {
                // One element failing must not kill the HUD; disable it.
                try { Mods.setOn(e.id, false); } catch (Throwable ignored) {}
                System.err.println("[OriginClient] HUD element '" + e.id + "' disabled: " + t);
            }
        }
    }

    /** Draw one element honoring its saved pos+scale. Used by HUD + editor. */
    public static void drawScaled(Element e, ScaledResolution sr, boolean preview) {
        HudPos pos = e.pos();
        int[] size = e.size(preview);
        int w = (int) Math.round(size[0] * pos.scale);
        int h = (int) Math.round(size[1] * pos.scale);
        int x = pos.x(sr.getScaledWidth(), w);
        int y = pos.y(sr.getScaledHeight(), h);
        if (pos.bg > 0.004) {
            int a = (int) Math.round(OriginTheme.clamp01(pos.bg) * 255);
            OriginUi.panel(x - 4, y - 4, w + 8, h + 8, 6, (a << 24) | 0x0E0E0E, 0);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale((float) pos.scale, (float) pos.scale, 1f);
        e.draw(0, 0, preview);
        GlStateManager.popMatrix();
    }

    /** The on-screen box (x, y, w, h) of an element — editor hit testing. */
    public static int[] bounds(Element e, ScaledResolution sr, boolean preview) {
        HudPos pos = e.pos();
        int[] size = e.size(preview);
        int w = (int) Math.round(size[0] * pos.scale);
        int h = (int) Math.round(size[1] * pos.scale);
        return new int[]{pos.x(sr.getScaledWidth(), w), pos.y(sr.getScaledHeight(), h), w, h};
    }

    private static FontRenderer font() { return Minecraft.getMinecraft().fontRendererObj; }

    private static void text(String s, int x, int y, int color, boolean shadow) {
        font().drawString(s, x, y, color, shadow);
    }

    // ------------------------------------------------------------------

    private static final class FpsElement extends Element {
        FpsElement() { super("fps", 0, 6, 6); }

        private String line() {
            int fps = Minecraft.getDebugFPS();
            boolean rev = Mods.bool(id, "reverseOrder");
            String s = rev ? "FPS: " + fps : fps + " FPS";
            if (Mods.bool(id, "showBrackets")) s = "[" + s + "]";
            return s;
        }

        public int[] size(boolean preview) { return new int[]{font().getStringWidth(line()), 10}; }

        public void draw(int x, int y, boolean preview) {
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            text(line(), x, y + 1, Mods.liveColor(id, "color"), Mods.bool(id, "textShadow"));
        }
    }

    private static final class CpsElement extends Element {
        CpsElement() { super("cps", 0, 6, 22); }

        private String line() {
            int left = ClickTracker.leftCps();
            String s;
            if (Mods.bool(id, "rightClick")) s = left + " | " + ClickTracker.rightCps();
            else s = String.valueOf(left);
            if (Mods.bool(id, "showText")) s = Mods.bool(id, "reverseText") ? "CPS: " + s : s + " CPS";
            return s;
        }

        public int[] size(boolean preview) { return new int[]{font().getStringWidth(line()), 10}; }

        public void draw(int x, int y, boolean preview) {
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            text(line(), x, y + 1, Mods.liveColor(id, "color"), true);
        }
    }

    private static final class CoordsElement extends Element {
        CoordsElement() { super("coords", 0, 6, 38); }

        private List<String> lines(boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            List<String> out = new ArrayList<String>();
            boolean dec = Mods.bool(id, "decimal");
            double px, py, pz;
            String facing = "South", biome = "Plains";
            if (mc.thePlayer != null) {
                px = mc.thePlayer.posX; py = mc.thePlayer.posY; pz = mc.thePlayer.posZ;
                facing = mc.thePlayer.getHorizontalFacing().toString();
                facing = Character.toUpperCase(facing.charAt(0)) + facing.substring(1);
                try {
                    biome = mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer)).biomeName;
                } catch (Throwable ignored) {}
            } else {
                px = 128.5; py = 64.0; pz = -320.5;
            }
            String x = dec ? String.format("%.1f", px) : String.valueOf((int) Math.floor(px));
            String y = dec ? String.format("%.1f", py) : String.valueOf((int) Math.floor(py));
            String z = dec ? String.format("%.1f", pz) : String.valueOf((int) Math.floor(pz));
            boolean vertical = "Vertical".equals(Mods.mode(id, "listMode"));
            if (vertical) {
                out.add("X: " + x);
                out.add("Y: " + y);
                out.add("Z: " + z);
                if (Mods.bool(id, "direction")) out.add("Facing: " + facing);
                if (Mods.bool(id, "biome")) out.add("Biome: " + biome);
            } else {
                StringBuilder sb = new StringBuilder("X: ").append(x).append("  Y: ").append(y).append("  Z: ").append(z);
                if (Mods.bool(id, "direction")) sb.append("  ").append(facing);
                out.add(sb.toString());
                if (Mods.bool(id, "biome")) out.add("Biome: " + biome);
            }
            return out;
        }

        public int[] size(boolean preview) {
            int w = 0;
            List<String> ls = lines(preview);
            for (String s : ls) w = Math.max(w, font().getStringWidth(s));
            return new int[]{w, ls.size() * 10};
        }

        public void draw(int x, int y, boolean preview) {
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            boolean shadow = Mods.bool(id, "textShadow");
            int i = 0;
            for (String line : lines(preview)) {
                text(line, x, y + 1 + i * 10, TEXT, shadow);
                i++;
            }
        }
    }

    private static final class KeystrokesElement extends Element {
        KeystrokesElement() { super("keystrokes", 3, 6, -40); }

        // Per-key press-fade state.
        private final Map<String, double[]> fade = new HashMap<String, double[]>();

        private double boxSize() { return Mods.num(id, "boxSize"); }

        public int[] size(boolean preview) {
            // Base layout at boxSize 1.0: WASD 22px boxes with 2px gaps (70 wide),
            // mouse row 34x14 x2, space 70x(thickness+4).
            double bs = boxSize();
            int w = (int) Math.round(70 * bs);
            int h = 0;
            if (Mods.bool(id, "showMovement")) h += (int) Math.round(46 * bs) + 2;
            if (Mods.bool(id, "showClicks")) h += (int) Math.round(14 * bs) + 2;
            if (Mods.bool(id, "showSpace")) h += (int) Math.round((clampSpace() + 4) * bs) + 2;
            if (h > 0) h -= 2;
            return new int[]{w, Math.max(h, 10)};
        }

        private int clampSpace() {
            int v = (int) Mods.num(id, "spacebarThickness");
            return Math.max(1, Math.min(10, v));
        }

        private double fadeT(String key, boolean down) {
            double delay = Math.max(1, Mods.num(id, "keyFadeDelay"));
            long now = System.currentTimeMillis();
            double[] st = fade.get(key);
            if (st == null) { st = new double[]{down ? 1 : 0, now}; fade.put(key, st); }
            double dt = now - st[1];
            st[1] = now;
            double step = dt / delay;
            st[0] += down ? step : -step;
            st[0] = OriginTheme.clamp01(st[0]);
            return st[0];
        }

        private void key(String label, String stateKey, boolean down, double x, double y, double w, double h, boolean shadow) {
            double t = fadeT(stateKey, down);
            int bg = OriginTheme.lerpColor(Mods.liveColor(id, "bgColor"), Mods.liveColor(id, "bgColorPressed"), t);
            Gl.fill(x, y, x + w, y + h, bg);
            if (Mods.bool(id, "border")) {
                int bt = Math.max(0, Math.min(4, (int) Mods.num(id, "borderThickness")));
                if (bt > 0) {
                    int bc = Mods.liveColor(id, "borderColor");
                    Gl.fill(x, y, x + w, y + bt, bc);
                    Gl.fill(x, y + h - bt, x + w, y + h, bc);
                    Gl.fill(x, y + bt, x + bt, y + h - bt, bc);
                    Gl.fill(x + w - bt, y + bt, x + w, y + h - bt, bc);
                }
            }
            int tc = OriginTheme.lerpColor(Mods.liveColor(id, "color"), Mods.liveColor(id, "textColorPressed"), t);
            FontRenderer f = font();
            f.drawString(label, (float) (x + (w - f.getStringWidth(label)) / 2.0), (float) (y + (h - 8) / 2.0), tc, shadow);
        }

        public void draw(int x, int y, boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            GameSettings gs = mc.gameSettings;
            boolean shadow = Mods.bool(id, "textShadow");
            double bs = boxSize();
            double box = 22 * bs, gap = 2;
            double yy = y;
            boolean inGame = mc.inGameHasFocus || preview;
            if (Mods.bool(id, "showMovement")) {
                boolean wDown = inGame && isDown(gs.keyBindForward.getKeyCode());
                boolean aDown = inGame && isDown(gs.keyBindLeft.getKeyCode());
                boolean sDown = inGame && isDown(gs.keyBindBack.getKeyCode());
                boolean dDown = inGame && isDown(gs.keyBindRight.getKeyCode());
                key(keyName(gs.keyBindForward.getKeyCode(), "W"), "w", wDown, x + box + gap, yy, box, box, shadow);
                yy += box + gap;
                key(keyName(gs.keyBindLeft.getKeyCode(), "A"), "a", aDown, x, yy, box, box, shadow);
                key(keyName(gs.keyBindBack.getKeyCode(), "S"), "s", sDown, x + box + gap, yy, box, box, shadow);
                key(keyName(gs.keyBindRight.getKeyCode(), "D"), "d", dDown, x + (box + gap) * 2, yy, box, box, shadow);
                yy += box + gap;
            }
            if (Mods.bool(id, "showClicks")) {
                double mw = 34 * bs, mh = 14 * bs;
                boolean l = inGame && Mouse.isButtonDown(0);
                boolean r = inGame && Mouse.isButtonDown(1);
                key("LMB", "lmb", l, x, yy, mw, mh, shadow);
                key("RMB", "rmb", r, x + mw + gap, yy, mw, mh, shadow);
                yy += mh + gap;
            }
            if (Mods.bool(id, "showSpace")) {
                double sw = 70 * bs, sh = (clampSpace() + 4) * bs;
                boolean sp = inGame && isDown(gs.keyBindJump.getKeyCode());
                double t = fadeT("space", sp);
                int bg = OriginTheme.lerpColor(Mods.liveColor(id, "bgColor"), Mods.liveColor(id, "bgColorPressed"), t);
                Gl.fill(x, yy, x + sw, yy + sh, bg);
                double barW = sw * 0.5, barH = Math.max(1, 2 * bs);
                int tc = OriginTheme.lerpColor(Mods.liveColor(id, "color"), Mods.liveColor(id, "textColorPressed"), t);
                Gl.fill(x + (sw - barW) / 2, yy + (sh - barH) / 2, x + (sw + barW) / 2, yy + (sh + barH) / 2, tc);
            }
        }

        private boolean isDown(int code) {
            if (code == 0) return false;
            if (code < 0) return Mouse.isButtonDown(code + 100);
            return code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
        }

        private String keyName(int code, String fallback) {
            try {
                String n = GameSettings.getKeyDisplayString(code);
                if (n != null && n.length() <= 3) return n;
                return n != null ? n.substring(0, 1) : fallback;
            } catch (Throwable t) {
                return fallback;
            }
        }
    }

    private static final class PotionHudElement extends Element {
        PotionHudElement() { super("potionhud", 2, -6, 6); }

        private List<PotionEffect> effects(boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            List<PotionEffect> out = new ArrayList<PotionEffect>();
            if (preview && (mc.thePlayer == null || mc.thePlayer.getActivePotionEffects().isEmpty())) {
                out.add(new PotionEffect(Potion.moveSpeed.id, 83 * 20, 0));
                out.add(new PotionEffect(Potion.damageBoost.id, 45 * 20, 1));
                return out;
            }
            if (mc.thePlayer != null) {
                Collection<PotionEffect> active = mc.thePlayer.getActivePotionEffects();
                out.addAll(active);
            }
            return out;
        }

        public int[] size(boolean preview) {
            List<PotionEffect> fx = effects(preview);
            int n = Math.max(1, fx.size());
            boolean minimal = Mods.bool(id, "minimal");
            int w = minimal ? 60 : 110;
            return new int[]{w, n * 22};
        }

        public void draw(int x, int y, boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            List<PotionEffect> fx = effects(preview);
            if (fx.isEmpty()) return;
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            boolean shadow = Mods.bool(id, "textShadow");
            boolean minimal = Mods.bool(id, "minimal");
            int yy = y;
            for (PotionEffect e : fx) {
                Potion potion = Potion.potionTypes[e.getPotionID()];
                if (potion == null) continue;
                // Blink when expiring (< 5s), like modern.
                boolean visible = true;
                if (Mods.bool(id, "blink") && e.getDuration() < 100 && !preview)
                    visible = (e.getDuration() / 10) % 2 == 0 || e.getDuration() > 60;
                if (visible && potion.hasStatusIcon()) {
                    // GuiContainer.inventoryBackground is protected — same texture.
                    mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation("textures/gui/container/inventory.png"));
                    GlStateManager.color(1f, 1f, 1f, 1f);
                    GlStateManager.enableBlend();
                    int icon = potion.getStatusIconIndex();
                    new net.minecraft.client.gui.Gui().drawTexturedModalRect(x, yy, icon % 8 * 18, 198 + icon / 8 * 18, 18, 18);
                }
                String name = net.minecraft.client.resources.I18n.format(e.getEffectName());
                if (e.getAmplifier() == 1) name += " II";
                else if (e.getAmplifier() == 2) name += " III";
                else if (e.getAmplifier() == 3) name += " IV";
                if (Mods.bool(id, "uppercase")) name = name.toUpperCase();
                String time = Mods.bool(id, "formattedDurations")
                    ? StringUtils.ticksToElapsedTime(e.getDuration())
                    : String.valueOf(e.getDuration() / 20) + "s";
                if (minimal) {
                    text(time, x + 21, yy + 5, TEXT, shadow);
                } else {
                    text(name, x + 21, yy + 1, TEXT, shadow);
                    text(time, x + 21, yy + 11, OriginTheme.TEXT_DIM, shadow);
                }
                yy += 22;
            }
        }
    }

    private static final class ArmorHudElement extends Element {
        ArmorHudElement() { super("armorhud", 6, 6, -24); }

        private List<ItemStack> stacks(boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            List<ItemStack> out = new ArrayList<ItemStack>();
            boolean any = false;
            if (mc.thePlayer != null) {
                for (int i = 3; i >= 0; i--) {
                    ItemStack st = mc.thePlayer.inventory.armorInventory[i];
                    out.add(st);
                    if (st != null) any = true;
                }
                ItemStack held = mc.thePlayer.getCurrentEquippedItem();
                out.add(held);
                if (held != null) any = true;
            }
            if (preview && !any) {
                out.clear();
                out.add(new ItemStack(Items.diamond_helmet));
                out.add(new ItemStack(Items.diamond_chestplate));
                out.add(new ItemStack(Items.diamond_leggings));
                out.add(new ItemStack(Items.diamond_boots));
                out.add(new ItemStack(Items.diamond_sword));
            }
            return out;
        }

        public int[] size(boolean preview) {
            boolean vertical = !"Horizontal".equals(Mods.mode(id, "listMode"));
            boolean showDur = !"Hidden".equals(Mods.mode(id, "durabilityPos"));
            int n = 5;
            if (vertical) return new int[]{showDur ? 62 : 18, n * 19};
            return new int[]{n * 19, showDur ? 30 : 18};
        }

        public void draw(int x, int y, boolean preview) {
            List<ItemStack> stacks = stacks(preview);
            if (stacks.isEmpty()) return;
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            boolean vertical = !"Horizontal".equals(Mods.mode(id, "listMode"));
            String durPos = Mods.mode(id, "durabilityPos");
            Minecraft mc = Minecraft.getMinecraft();
            RenderHelper.enableGUIStandardItemLighting();
            int i = 0;
            for (ItemStack st : stacks) {
                if (st != null) {
                    int ix = vertical ? x : x + i * 19;
                    int iy = vertical ? y + i * 19 : y;
                    mc.getRenderItem().renderItemAndEffectIntoGUI(st, ix, iy);
                    if (!"Hidden".equals(durPos) && st.isItemStackDamageable()) {
                        int max = st.getMaxDamage();
                        int left = max - st.getItemDamage();
                        boolean pct = "Percent".equals(Mods.mode(id, "damageDisplay"));
                        String txt = pct ? Math.round(left * 100f / max) + "%" : String.valueOf(left);
                        int color = left < max * 0.15 ? Mods.liveColor(id, "damageColor") : Mods.liveColor(id, "textColor");
                        RenderHelper.disableStandardItemLighting();
                        if ("Below".equals(durPos)) text(txt, ix, iy + 19, color, true);
                        else if ("Left".equals(durPos)) text(txt, ix - 2 - font().getStringWidth(txt), iy + 5, color, true);
                        else text(txt, ix + 20, iy + 5, color, true);
                        RenderHelper.enableGUIStandardItemLighting();
                    }
                }
                i++;
            }
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private static final class ServerAddressElement extends Element {
        ServerAddressElement() { super("serveraddress", 2, -6, 6); }

        private String line(boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            ServerData sd = mc.getCurrentServerData();
            if (sd == null) return preview ? "play.example.net" : "Singleplayer";
            String ip = sd.serverIP;
            try {
                int players = mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfoMap().size() : 0;
                if (players > 0) return ip + " (" + players + ")";
            } catch (Throwable ignored) {}
            return ip;
        }

        public int[] size(boolean preview) {
            int w = font().getStringWidth(line(preview));
            if (Mods.bool(id, "serverIcon")) w += 14;
            return new int[]{w, 12};
        }

        public void draw(int x, int y, boolean preview) {
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            int tx = x;
            if (Mods.bool(id, "serverIcon")) {
                OriginUi.logo(x + 6, y + 6, 12, 1.0);
                tx += 14;
            }
            text(line(preview), tx, y + 2, TEXT, Mods.bool(id, "textShadow"));
        }
    }

    private static final class SprintStateElement extends Element {
        SprintStateElement() { super("togglesprint", 6, 6, -6); }

        @Override
        public boolean on() { return Mods.isOn("togglesprint") && Mods.bool("togglesprint", "hud"); }

        private String line() {
            if (ToggleSprint.sneakToggled) return "Sneak (Toggled)";
            if (ToggleSprint.sprintToggled) return "Sprint (Toggled)";
            if (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.isSprinting())
                return "Sprinting";
            return "Sprint (Toggled)";
        }

        public int[] size(boolean preview) { return new int[]{font().getStringWidth(line()), 10}; }

        public void draw(int x, int y, boolean preview) {
            boolean active = preview || ToggleSprint.sprintToggled || ToggleSprint.sneakToggled
                || (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.isSprinting());
            if (!active) return;
            int[] s = size(preview);
            backing(x, y, s[0], s[1]);
            text(line(), x, y + 1, TEXT, true);
        }
    }
}
