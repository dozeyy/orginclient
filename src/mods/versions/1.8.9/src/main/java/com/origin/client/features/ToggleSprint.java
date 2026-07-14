package com.origin.client.features;

import com.origin.client.mods.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Toggle Sprint / Toggle Sneak. Pressing the vanilla sprint (or sneak) key
 * flips a latch; while latched the key is held down for the game via
 * KeyBinding.setKeyBindState — no movement patching, so it composes with
 * every other movement mechanic.
 */
public final class ToggleSprint {

    public static volatile boolean sprintToggled = false;
    public static volatile boolean sneakToggled = false;

    private boolean sprintWasDown = false;
    private boolean sneakWasDown = false;

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || !Mods.isOn("togglesprint")) return;
        int sprintKey = mc.gameSettings.keyBindSprint.getKeyCode();
        int sneakKey = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean sprintDown = sprintKey > 0 && sprintKey < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(sprintKey);
        boolean sneakDown = sneakKey > 0 && sneakKey < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(sneakKey);
        if ("Toggle".equals(Mods.mode("togglesprint", "mode")) && sprintDown && !sprintWasDown)
            sprintToggled = !sprintToggled;
        if (Mods.bool("togglesprint", "sneak") && sneakDown && !sneakWasDown)
            sneakToggled = !sneakToggled;
        sprintWasDown = sprintDown;
        sneakWasDown = sneakDown;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (!Mods.isOn("togglesprint")) {
            sprintToggled = false;
            sneakToggled = false;
            return;
        }
        if (!"Toggle".equals(Mods.mode("togglesprint", "mode"))) sprintToggled = false;
        if (!Mods.bool("togglesprint", "sneak")) sneakToggled = false;
        if (sprintToggled)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        if (sneakToggled && mc.currentScreen == null)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
    }
}
