package com.origin.client.features;

import com.origin.client.mods.Mods;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ChatType;
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
    private boolean smoothBefore;
    private boolean zoomKeyWas = false;

    // ---- Fullbright ----
    private float gammaBefore = 1.0f;
    private boolean gammaBoosted = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.world == null) return;
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
            smoothBefore = mc.gameSettings.smoothCamera;
            mc.gameSettings.fovSetting = (float) Mods.num("zoom", "fov");
            mc.gameSettings.mouseSensitivity = sensBefore * 0.5f;
            if (Mods.bool("zoom", "smoothZoom")) mc.gameSettings.smoothCamera = true;
            zoomApplied = true;
        } else if (!wantZoom && zoomApplied) {
            mc.gameSettings.fovSetting = fovBefore;
            mc.gameSettings.mouseSensitivity = sensBefore;
            mc.gameSettings.smoothCamera = smoothBefore;
            zoomApplied = false;
            zooming = false;
        }
    }

    private boolean zoomApplied = false;

    private void tickFullbright() {
        if (Mods.isOn("fullbright")) {
            if (!gammaBoosted) {
                gammaBefore = mc.gameSettings.gammaSetting;
                gammaBoosted = true;
            }
            mc.gameSettings.gammaSetting = (float) Mods.num("fullbright", "gamma");
        } else if (gammaBoosted) {
            // Restore to at most vanilla max — a crash while boosted may have
            // persisted a huge value into options.txt, so never restore >1.
            mc.gameSettings.gammaSetting = Math.min(1.0f, gammaBefore);
            gammaBoosted = false;
        }
    }

    private void tickTimeChanger() {
        if (!Mods.isOn("timechanger")) return;
        WorldClient world = mc.world;
        if (world == null) return;
        long target = (long) Mods.num("timechanger", "time");
        if (Mods.bool("timechanger", "timePassage"))
            target += world.getTotalWorldTime() % 24000;
        world.setWorldTime(target % 24000);
    }

    private void tickWeather() {
        if (!Mods.isOn("weather")) return;
        WorldClient world = mc.world;
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
        if (event.getType() == ChatType.GAME_INFO) return; // action bar
        if (!Mods.isOn("chat") || !Mods.bool("chat", "timestamps")) return;
        TextComponentString stamp = new TextComponentString(
            TextFormatting.GRAY + "[" + TIMESTAMP.format(new Date()) + "] " + TextFormatting.RESET);
        stamp.appendSibling(event.getMessage());
        event.setMessage(stamp);
    }

    // ---- Smart disconnect (confirm before quitting a world/server) ----

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiIngameMenu) || event.getButton().id != 1) return;
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
                mc.world.sendQuittingDisconnectingPacket();
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
            if (Mods.isOn("hitboxes")) drawHitboxes(event.getPartialTicks());
            if (Mods.isOn("chunkborders")) drawChunkBorders(event.getPartialTicks());
        } catch (Throwable t) {
            // World overlays must never kill the frame.
        }
    }

    private void setupLines(float width) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableLighting();
        GL11.glLineWidth(width);
    }

    private void teardownLines() {
        GL11.glLineWidth(1f);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void colorOf(int argb) {
        GlStateManager.color((argb >>> 16 & 0xFF) / 255f, (argb >>> 8 & 0xFF) / 255f,
                             (argb & 0xFF) / 255f, (argb >>> 24 & 0xFF) / 255f);
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
        colorOf(color);
        double cx = camX(pt), cy = camY(pt), cz = camZ(pt);
        for (Entity e : mc.world.loadedEntityList) {
            if (e == view || e == mc.player) continue;
            if (view.getDistance(e) > maxDist) continue;
            double ex = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
            double ey = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt;
            double ez = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
            AxisAlignedBB bb = e.getEntityBoundingBox()
                .offset(ex - e.posX, ey - e.posY, ez - e.posZ)
                .offset(-cx, -cy, -cz);
            drawBoxOutline(bb);
        }
        teardownLines();
    }

    private void drawChunkBorders(float pt) {
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        int chunkX = (int) Math.floor(view.posX / 16.0) * 16;
        int chunkZ = (int) Math.floor(view.posZ / 16.0) * 16;
        setupLines((float) Mods.num("chunkborders", "thickness"));
        colorOf(Mods.liveColor("chunkborders", "color"));
        double cx = camX(pt), cy = camY(pt), cz = camZ(pt);
        GlStateManager.translate(-cx, -cy, -cz);
        // Corner verticals of the current chunk.
        for (int dx = 0; dx <= 16; dx += 16) {
            for (int dz = 0; dz <= 16; dz += 16) {
                line(chunkX + dx, 0, chunkZ + dz, chunkX + dx, 256, chunkZ + dz);
            }
        }
        if (Mods.bool("chunkborders", "grid")) {
            // Horizontal rings every 16 blocks of height near the player.
            int py = (int) Math.floor(view.posY / 16.0) * 16;
            for (int y = Math.max(0, py - 32); y <= Math.min(256, py + 32); y += 16) {
                line(chunkX, y, chunkZ, chunkX + 16, y, chunkZ);
                line(chunkX + 16, y, chunkZ, chunkX + 16, y, chunkZ + 16);
                line(chunkX + 16, y, chunkZ + 16, chunkX, y, chunkZ + 16);
                line(chunkX, y, chunkZ + 16, chunkX, y, chunkZ);
            }
        }
        GlStateManager.translate(cx, cy, cz);
        teardownLines();
    }

    private void line(double x0, double y0, double z0, double x1, double y1, double z1) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glEnd();
    }

    private void drawBoxOutline(AxisAlignedBB bb) {
        GL11.glBegin(GL11.GL_LINES);
        // bottom
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        // top
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        // pillars
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glEnd();
    }

    @SubscribeEvent
    public void onBlockHighlight(DrawBlockHighlightEvent event) {
        if (!Mods.isOn("blockoverlay")) return;
        if (event.getTarget() == null || event.getTarget().getBlockPos() == null) return;
        if (event.getTarget().typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) return;
        try {
            IBlockState state = mc.world.getBlockState(event.getTarget().getBlockPos());
            if (state.getMaterial() == net.minecraft.block.material.Material.AIR) return;
            event.setCanceled(true);
            float pt = event.getPartialTicks();
            AxisAlignedBB bb = state.getSelectedBoundingBox(mc.world, event.getTarget().getBlockPos())
                .grow(0.002, 0.002, 0.002)
                .offset(-camX(pt), -camY(pt), -camZ(pt));
            setupLines((float) Mods.num("blockoverlay", "thickness"));
            GlStateManager.depthMask(false);
            colorOf(Mods.liveColor("blockoverlay", "color"));
            drawBoxOutline(bb);
            if (Mods.bool("blockoverlay", "overlay")) {
                colorOf(Mods.liveColor("blockoverlay", "overlayColor"));
                GL11.glBegin(GL11.GL_QUADS);
                // Top face fill only — subtle, like the modern default.
                GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
                GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
                GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
                GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
                GL11.glEnd();
            }
            GlStateManager.depthMask(true);
            teardownLines();
        } catch (Throwable t) {
            // Highlight restyle must never kill the frame; vanilla resumes next frame.
        }
    }
}
