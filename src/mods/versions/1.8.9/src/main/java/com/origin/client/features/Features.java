package com.origin.client.features;

import com.origin.client.mods.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The non-HUD feature mods: zoom, fullbright, time/weather changer, hitboxes,
 * block overlay, chat timestamps, chunk borders and the smart-disconnect
 * confirm. Each handler reads its settings live from Mods every use and is
 * written to restore vanilla state the moment its mod is toggled off.
 */
public final class Features {

    private final Minecraft mc = Minecraft.getMinecraft();

    // ---- Zoom ----
    private boolean zooming = false;
    private float fovBefore, sensBefore;
    private boolean zoomKeyWas = false;

    // ---- Fullbright ----
    private float gammaBefore = 1.0f;
    private boolean gammaBoosted = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.theWorld == null) return;
        tickZoom();
        tickFullbright();
        tickTimeChanger();
        tickWeather();
    }

    private void tickZoom() {
        boolean keyDown = com.origin.client.OriginKeyBindings.ZOOM.isKeyDown() && mc.inGameHasFocus;
        boolean wantZoom;
        if (Mods.isOn("zoom") && Mods.bool("zoom", "toggleZoom")) {
            if (keyDown && !zoomKeyWas) zooming = !zooming;
            wantZoom = zooming && Mods.isOn("zoom");
        } else {
            wantZoom = keyDown && Mods.isOn("zoom");
        }
        zoomKeyWas = keyDown;

        if (wantZoom && !zoomApplied) {
            fovBefore = mc.gameSettings.fovSetting;
            sensBefore = mc.gameSettings.mouseSensitivity;
            mc.gameSettings.fovSetting = (float) Mods.num("zoom", "fov");
            mc.gameSettings.mouseSensitivity = sensBefore * 0.5f;
            zoomApplied = true;
        }
        if (zoomApplied) {
            // Tracked live so flipping the option mid-zoom takes effect, and
            // ALWAYS cleared on release — smooth camera is zoom-owned state.
            // Restoring a captured "before" value could permanently latch
            // cinematic camera into options.txt after a crash mid-zoom (the
            // "smooth zoom can't be turned off" report).
            mc.gameSettings.smoothCamera = wantZoom && Mods.bool("zoom", "smoothZoom");
        }
        if (!wantZoom && zoomApplied) {
            mc.gameSettings.fovSetting = fovBefore;
            mc.gameSettings.mouseSensitivity = sensBefore;
            mc.gameSettings.smoothCamera = false;
            zoomApplied = false;
            zooming = false;
        }
    }

    private boolean zoomApplied = false;

    private boolean nightVisionApplied = false;

    private void tickFullbright() {
        if (Mods.isOn("fullbright")) {
            if (!gammaBoosted) {
                gammaBefore = mc.gameSettings.gammaSetting;
                gammaBoosted = true;
            }
            mc.gameSettings.gammaSetting = (float) Mods.num("fullbright", "gamma");
            // Gamma is ignored by OptiFine shader packs (they compute their
            // own lighting) — a client-side Night Vision effect is the one
            // signal shaders universally honor, so fullbright works with and
            // without shaders. Applied ONCE with a very long duration: each
            // re-add forces a lightmap rebuild (a frame hitch), so refreshing
            // it every few seconds caused a periodic stutter. A ~14-hour
            // duration means it never needs re-applying in a session.
            net.minecraft.potion.PotionEffect existing = mc.thePlayer != null
                ? mc.thePlayer.getActivePotionEffect(net.minecraft.potion.Potion.nightVision) : null;
            if (mc.thePlayer != null && existing == null) {
                mc.thePlayer.addPotionEffect(new net.minecraft.potion.PotionEffect(
                    net.minecraft.potion.Potion.nightVision.id, 1000000, 0, true, false));
                nightVisionApplied = true;
            }
        } else {
            if (gammaBoosted) {
                // Restore to at most vanilla max — a crash while boosted may
                // have persisted a huge value into options.txt.
                mc.gameSettings.gammaSetting = Math.min(1.0f, gammaBefore);
                gammaBoosted = false;
            }
            if (nightVisionApplied && mc.thePlayer != null) {
                mc.thePlayer.removePotionEffect(net.minecraft.potion.Potion.nightVision.id);
                nightVisionApplied = false;
            }
        }
    }

    // Singleplayer time is set AUTHORITATIVELY on the integrated server:
    // gamerule doDaylightCycle off + set the server world's time once. The
    // server then broadcasts that frozen time, so the client never sees a
    // conflicting value — no blink, and (critically) ZERO per-frame work. The
    // earlier render-tick pin re-read the JSON config and called setWorldTime
    // every rendered frame (300+/s), and the Gson boxing churn was the cause
    // of the "super inconsistent FPS" regression. Multiplayer, where we can't
    // touch the server, still uses a light client-side render pin below.
    private long timeApplied = Long.MIN_VALUE;
    private boolean passageWas;
    private boolean daylightDisabled;
    private long mpCachedTarget = Long.MIN_VALUE;

    private void tickTimeChanger() {
        if (!Mods.isOn("timechanger")) {
            if (daylightDisabled && mc.isIntegratedServerRunning())
                setServerDaylight(true);
            daylightDisabled = false;
            timeApplied = Long.MIN_VALUE;
            mpCachedTarget = Long.MIN_VALUE;
            return;
        }
        WorldClient world = mc.theWorld;
        if (world == null) return;
        long target = (((long) Mods.num("timechanger", "time")) % 24000 + 24000) % 24000;
        boolean passage = Mods.bool("timechanger", "timePassage");

        if (mc.isIntegratedServerRunning()) {
            net.minecraft.world.WorldServer sw = serverWorld(world);
            if (sw != null && (timeApplied != target || passageWas != passage || daylightDisabled == passage)) {
                setServerDaylight(passage);
                daylightDisabled = !passage;
                sw.setWorldTime(target);
                timeApplied = target;
                passageWas = passage;
            }
        } else {
            // Multiplayer: cache the target here (client tick) so the per-frame
            // render pin never touches the JSON config.
            mpCachedTarget = passage ? Long.MIN_VALUE : target;
        }
    }

    private net.minecraft.world.WorldServer serverWorld(WorldClient client) {
        try {
            return mc.getIntegratedServer().worldServerForDimension(client.provider.getDimensionId());
        } catch (Throwable t) {
            return null;
        }
    }

    private void setServerDaylight(boolean on) {
        try {
            net.minecraft.world.WorldServer sw = serverWorld(mc.theWorld);
            if (sw != null)
                sw.getGameRules().setOrCreateGameRule("doDaylightCycle", Boolean.toString(on));
        } catch (Throwable t) {
            // Best effort — falls back to the client-side pin behaviour.
        }
    }

    // Multiplayer-only client-side pin: cheap (no JSON, no allocation), and a
    // no-op in singleplayer (mpCachedTarget stays MIN_VALUE there).
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long target = mpCachedTarget;
        if (target == Long.MIN_VALUE) return;
        WorldClient world = mc.theWorld;
        if (world != null && world.getWorldTime() % 24000 != target)
            world.setWorldTime(target);
    }

    private void tickWeather() {
        if (!Mods.isOn("weather")) return;
        WorldClient world = mc.theWorld;
        if (world == null) return;
        boolean rain = "Rain".equals(Mods.mode("weather", "mode"));
        world.getWorldInfo().setRaining(rain);
        world.setRainStrength(rain ? 1f : 0f);
        world.setThunderStrength(0f);
    }

    // ---- Chat timestamps ----

    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("HH:mm");

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type == 2) return; // action bar
        if (!Mods.isOn("chat") || !Mods.bool("chat", "timestamps")) return;
        ChatComponentText stamp = new ChatComponentText(
            EnumChatFormatting.GRAY + "[" + TIMESTAMP.format(new Date()) + "] " + EnumChatFormatting.RESET);
        stamp.appendSibling(event.message);
        event.message = stamp;
    }

    // ---- Smart disconnect (confirm before quitting a world/server) ----

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiIngameMenu) || event.button.id != 1) return;
        if (!Mods.confirmDisconnect()) return;
        event.setCanceled(true);
        final boolean integrated = mc.isIntegratedServerRunning();
        mc.displayGuiScreen(new GuiYesNo(new GuiYesNoCallback() {
            @Override
            public void confirmClicked(boolean result, int id) {
                if (!result) {
                    mc.displayGuiScreen(null);
                    mc.setIngameFocus();
                    return;
                }
                // The vanilla GuiIngameMenu quit sequence.
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld((WorldClient) null);
                if (integrated) mc.displayGuiScreen(new GuiMainMenu());
                else mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
            }
        }, "Leave this world?", "You'll disconnect from the current world or server.", 0));
    }

    // ---- Hitboxes + chunk borders + block overlay (world-space lines) ----

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        try {
            if (Mods.isOn("hitboxes")) drawHitboxes(event.partialTicks);
            if (Mods.isOn("chunkborders")) drawChunkBorders(event.partialTicks);
        } catch (Throwable t) {
            // World overlays must never kill the frame.
        }
    }

    // World-space lines go through the Tessellator with POSITION_COLOR —
    // exactly the path vanilla's own selection box / F3+B hitboxes use.
    // Raw glBegin/glVertex drawing (the first implementation) renders black
    // or vanishes at certain angles under OptiFine shader programs, because
    // the active gbuffer shader reads per-vertex attributes immediate mode
    // never supplies. Per-vertex color rides the vertex format, so shaders
    // handle it the same way they handle vanilla's boxes.
    private void setupLines(float width) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GL11.glLineWidth(width);
    }

    private void teardownLines() {
        GL11.glLineWidth(1f);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private net.minecraft.client.renderer.WorldRenderer lineR = null;
    private float lineRed, lineGreen, lineBlue, lineAlpha;

    private void beginLines(int argb) {
        lineRed = (argb >>> 16 & 0xFF) / 255f;
        lineGreen = (argb >>> 8 & 0xFF) / 255f;
        lineBlue = (argb & 0xFF) / 255f;
        lineAlpha = (argb >>> 24 & 0xFF) / 255f;
        lineR = net.minecraft.client.renderer.Tessellator.getInstance().getWorldRenderer();
        lineR.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
    }

    private void endLines() {
        net.minecraft.client.renderer.Tessellator.getInstance().draw();
        lineR = null;
    }

    private double camX(float pt) { Entity e = mc.getRenderViewEntity(); return e.lastTickPosX + (e.posX - e.lastTickPosX) * pt; }
    private double camY(float pt) { Entity e = mc.getRenderViewEntity(); return e.lastTickPosY + (e.posY - e.lastTickPosY) * pt; }
    private double camZ(float pt) { Entity e = mc.getRenderViewEntity(); return e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt; }

    private void drawHitboxes(float pt) {
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        double maxDist = Mods.num("hitboxes", "maxDistance");
        int color = Mods.liveColor("hitboxes", "lineColor");
        setupLines((float) Mods.num("hitboxes", "lineWidth"));
        double cx = camX(pt), cy = camY(pt), cz = camZ(pt);
        beginLines(color);
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (e == view || e == mc.thePlayer) continue;
            if (view.getDistanceToEntity(e) > maxDist) continue;
            double ex = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
            double ey = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt;
            double ez = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
            AxisAlignedBB bb = e.getEntityBoundingBox()
                .offset(ex - e.posX, ey - e.posY, ez - e.posZ)
                .offset(-cx, -cy, -cz);
            drawBoxOutline(bb);
        }
        endLines();
        teardownLines();
    }

    private void drawChunkBorders(float pt) {
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        int chunkX = (int) Math.floor(view.posX / 16.0) * 16;
        int chunkZ = (int) Math.floor(view.posZ / 16.0) * 16;
        setupLines((float) Mods.num("chunkborders", "thickness"));
        double cx = camX(pt), cy = camY(pt), cz = camZ(pt);
        beginLines(Mods.liveColor("chunkborders", "color"));
        // Vertical edges every 4 blocks along the current chunk's boundary
        // (vanilla F3+G's density), full world height.
        for (int d = 0; d <= 16; d += 4) {
            vline(chunkX + d, chunkZ, cx, cy, cz);
            vline(chunkX + d, chunkZ + 16, cx, cy, cz);
            vline(chunkX, chunkZ + d, cx, cy, cz);
            vline(chunkX + 16, chunkZ + d, cx, cy, cz);
        }
        if (Mods.bool("chunkborders", "grid")) {
            // Horizontal rings every 8 blocks of height near the player.
            int py = (int) Math.floor(view.posY / 8.0) * 8;
            for (int y = Math.max(0, py - 40); y <= Math.min(256, py + 40); y += 8) {
                line(chunkX, y, chunkZ, chunkX + 16, y, chunkZ, cx, cy, cz);
                line(chunkX + 16, y, chunkZ, chunkX + 16, y, chunkZ + 16, cx, cy, cz);
                line(chunkX + 16, y, chunkZ + 16, chunkX, y, chunkZ + 16, cx, cy, cz);
                line(chunkX, y, chunkZ + 16, chunkX, y, chunkZ, cx, cy, cz);
            }
        }
        endLines();
        teardownLines();
    }

    private void vline(double x, double z, double cx, double cy, double cz) {
        line(x, 0, z, x, 256, z, cx, cy, cz);
    }

    private void line(double x0, double y0, double z0, double x1, double y1, double z1,
                      double cx, double cy, double cz) {
        lineR.pos(x0 - cx, y0 - cy, z0 - cz).color(lineRed, lineGreen, lineBlue, lineAlpha).endVertex();
        lineR.pos(x1 - cx, y1 - cy, z1 - cz).color(lineRed, lineGreen, lineBlue, lineAlpha).endVertex();
    }

    private void seg(double x0, double y0, double z0, double x1, double y1, double z1) {
        lineR.pos(x0, y0, z0).color(lineRed, lineGreen, lineBlue, lineAlpha).endVertex();
        lineR.pos(x1, y1, z1).color(lineRed, lineGreen, lineBlue, lineAlpha).endVertex();
    }

    /** 12 edges of an already-camera-relative box into the open line batch. */
    private void drawBoxOutline(AxisAlignedBB bb) {
        // bottom
        seg(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        seg(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        seg(bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        seg(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ);
        // top
        seg(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        seg(bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        seg(bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        seg(bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);
        // pillars
        seg(bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        seg(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        seg(bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        seg(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
    }

    @SubscribeEvent
    public void onBlockHighlight(DrawBlockHighlightEvent event) {
        if (!Mods.isOn("blockoverlay")) return;
        if (event.target == null || event.target.getBlockPos() == null) return;
        if (event.target.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) return;
        try {
            net.minecraft.block.Block block = mc.theWorld.getBlockState(event.target.getBlockPos()).getBlock();
            if (block.getMaterial() == net.minecraft.block.material.Material.air) return;
            event.setCanceled(true);
            float pt = event.partialTicks;
            block.setBlockBoundsBasedOnState(mc.theWorld, event.target.getBlockPos());
            AxisAlignedBB bb = block.getSelectedBoundingBox(mc.theWorld, event.target.getBlockPos())
                .expand(0.002, 0.002, 0.002)
                .offset(-camX(pt), -camY(pt), -camZ(pt));
            setupLines((float) Mods.num("blockoverlay", "thickness"));
            GlStateManager.depthMask(false);
            beginLines(Mods.liveColor("blockoverlay", "color"));
            drawBoxOutline(bb);
            endLines();
            if (Mods.bool("blockoverlay", "overlay")) {
                // Face fill rides the same POSITION_COLOR path as the lines.
                int oc = Mods.liveColor("blockoverlay", "overlayColor");
                float r = (oc >>> 16 & 0xFF) / 255f, g = (oc >>> 8 & 0xFF) / 255f;
                float b = (oc & 0xFF) / 255f, a = (oc >>> 24 & 0xFF) / 255f;
                net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
                net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
                wr.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
                wr.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
                wr.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
                tess.draw();
            }
            GlStateManager.depthMask(true);
            teardownLines();
        } catch (Throwable t) {
            // Highlight restyle must never kill the frame; vanilla resumes next frame.
        }
    }
}
