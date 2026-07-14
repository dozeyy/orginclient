package com.origin.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/** Origin's keybinds — same defaults and lang keys as the modern modules. */
public final class OriginKeyBindings {

    public static final KeyBinding OPEN_MENU =
        new KeyBinding("key.originclient.open_mod_menu", Keyboard.KEY_RSHIFT, "key.categories.originclient");
    public static final KeyBinding ZOOM =
        new KeyBinding("key.originclient.zoom", Keyboard.KEY_C, "key.categories.originclient");

    private OriginKeyBindings() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_MENU);
        ClientRegistry.registerKeyBinding(ZOOM);
    }
}
