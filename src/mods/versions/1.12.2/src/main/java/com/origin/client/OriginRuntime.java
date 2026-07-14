package com.origin.client;

import com.origin.client.features.ClickTracker;
import com.origin.client.features.Features;
import com.origin.client.features.ToggleSprint;
import com.origin.client.hud.HudEditorScreen;
import com.origin.client.hud.HudElements;
import com.origin.client.render.OriginLoadingRenderer;
import com.origin.client.render.OriginScreenRenderer;
import com.origin.client.render.OriginScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Event hub + wiring. Registered once from the mod entry point; everything
 * else hangs off here. Each surface registers independently so one failed
 * registration can't take the rest down.
 */
public final class OriginRuntime {

    public static void register() {
        OriginKeyBindings.register();
        registerSafely(new OriginRuntime());
        registerSafely(new OriginScreens());
        registerSafely(new Features());
        registerSafely(new ToggleSprint());
        registerSafely(new ClickTracker());
    }

    private static void registerSafely(Object handler) {
        try {
            MinecraftForge.EVENT_BUS.register(handler);
        } catch (Throwable t) {
            System.err.println("[OriginClient] handler " + handler.getClass().getSimpleName() + " not registered: " + t);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();

        // Claim mc.loadingScreen for the Origin loading scene, and RE-claim it
        // if anything else replaces it. OptiFine installs its own
        // LoadingScreenRenderer after mod init (and can re-create it), which is
        // why a one-shot swap left the world-load screen vanilla — this instanceof
        // check quietly takes it back next tick. Cheap (once per client tick).
        if (mc.loadingScreen != null && !(mc.loadingScreen instanceof OriginLoadingRenderer)) {
            try {
                mc.loadingScreen = new OriginLoadingRenderer(mc);
            } catch (Throwable t) {
                System.err.println("[OriginClient] loading renderer swap failed (vanilla stays): " + t);
            }
        }

        // Right Shift opens the quick HUD screen (which hosts the MODS button)
        // — only in-world with no screen open; the menu itself handles the
        // close path.
        if (mc.player != null && mc.currentScreen == null
            && OriginKeyBindings.OPEN_MENU.isPressed()) {
            mc.displayGuiScreen(new HudEditorScreen(true));
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        try {
            HudElements.renderAll(new ScaledResolution(Minecraft.getMinecraft()));
        } catch (Throwable t) {
            // HUD dispatch must never take the overlay down.
        }
    }
}
