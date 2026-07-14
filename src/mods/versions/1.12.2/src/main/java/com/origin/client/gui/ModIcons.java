package com.origin.client.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * mod_icons.png atlas map — 576x384, 6 cols, 96px cells. Constants inlined
 * from mod_icons.json (the shipped PNG is byte-identical to the modern
 * modules', so cell coordinates are too).
 */
final class ModIcons {

    private static final Map<String, int[]> CELLS = new HashMap<String, int[]>();

    static {
        put("armorhud", 0, 0);      put("blockoverlay", 96, 0);  put("chat", 192, 0);
        put("chunkborders", 288, 0); put("coords", 384, 0);      put("cps", 480, 0);
        put("fps", 0, 96);          put("freelook", 96, 96);     put("fullbright", 192, 96);
        put("hitboxes", 288, 96);   put("keystrokes", 384, 96);  put("motionblur", 480, 96);
        put("nametags", 0, 192);    put("particles", 96, 192);   put("potionhud", 192, 192);
        put("scoreboard", 288, 192); put("serveraddress", 384, 192); put("timechanger", 480, 192);
        put("togglesprint", 0, 288); put("weather", 96, 288);    put("zoom", 192, 288);
    }

    private ModIcons() {}

    private static void put(String id, int x, int y) { CELLS.put(id, new int[]{x, y}); }

    static int[] cell(String id) { return CELLS.get(id); }
}
