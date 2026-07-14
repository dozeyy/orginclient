package com.origin.client;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Origin Client for Minecraft 1.12.2 (Forge). Entry point only — all surfaces
 * register from init so a failure in any one of them can never stop the mod
 * (and the game) from loading.
 */
@Mod(modid = "originclient", name = "Origin Client", version = "@VERSION@", clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public final class OriginClientMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        try {
            OriginRuntime.register();
        } catch (Throwable t) {
            // Never let Origin stop the game from loading — vanilla look is
            // the fail-soft floor, a crash is not.
            System.err.println("[OriginClient] init failed, running vanilla: " + t);
        }
    }
}
