package com.origin.client.client.hud;

import com.origin.client.client.mods.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Tab Editor's player-list overlay — a full custom draw that REPLACES vanilla's
 * {@code PlayerTabOverlay.render} (cancelled from PlayerTabOverlayMixin). Drawing it
 * ourselves is what makes every option — heads on/off, ping bars vs number vs
 * hidden, per-element colours, header/footer disable+tint, highlight-own, move-self-
 * to-top, hide-NPCs — reliable without fragile mixins into vanilla's render internals.
 *
 * Layout mirrors vanilla: one column up to 20 players, then more columns; each row is
 * a translucent bar with an optional 8px head, the display name, and a ping readout,
 * with a centered header above and footer below. Every value is read live and it only
 * runs while the Tab Editor mod is on, so it's fully fail-soft to vanilla.
 */
public final class OriginTabList {
	private OriginTabList() {
	}

	private static final int ROW_H = 9;

	/**
	 * @param customize when false (Tab Editor mod OFF) the list renders in a plain,
	 *                  vanilla-like default — heads on, ping bars, standard background,
	 *                  no filters/reorder/recolour — so the tab still works and looks
	 *                  normal without the mod. When true, every option is applied.
	 */
	public static void render(GuiGraphics g, int screenWidth, List<PlayerInfo> allInfos,
							  Component header, Component footer, boolean customize) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		UUID self = mc.player != null ? mc.player.getUUID() : null;

		// Options only apply when customizing; otherwise use vanilla-like defaults.
		boolean heads = !customize || Mods.bool("tablist", "displayHeads");
		boolean hidePing = customize && Mods.bool("tablist", "hidePing");
		boolean pingNum = customize && Mods.bool("tablist", "pingAsNumber");
		boolean highlightSelf = customize && Mods.bool("tablist", "highlightSelf");
		boolean disableHeader = customize && Mods.bool("tablist", "disableHeader");
		boolean disableFooter = customize && Mods.bool("tablist", "disableFooter");
		int bg = customize ? Mods.color("tablist", "backgroundColor") : 0x66000000;
		int headerCol = customize ? Mods.color("tablist", "headerColor") : 0xFFFFFFFF;
		int footerCol = customize ? Mods.color("tablist", "footerColor") : 0xFFFFFFFF;

		// filter + reorder (customize only)
		List<PlayerInfo> list = new ArrayList<>(allInfos);
		if (customize && Mods.bool("tablist", "hideNpcs")) {
			// Real Mojang accounts use version-4 UUIDs; offline/NPC entities (Citizens
			// etc.) use version-2/3 offline UUIDs — a reliable "fake player" tell.
			list.removeIf(p -> p.getProfile().getId().version() != 4);
		}
		if (customize && Mods.bool("tablist", "moveSelfTop") && self != null) {
			final UUID me = self;
			list.sort((a, b) -> Boolean.compare(
					b.getProfile().getId().equals(me), a.getProfile().getId().equals(me)));
		}

		int n = Math.min(80, list.size());
		if (n == 0) {
			return;
		}

		int headW = heads ? ROW_H : 0;

		Component[] names = new Component[n];
		int maxName = 0;
		for (int i = 0; i < n; i++) {
			names[i] = displayName(list.get(i));
			maxName = Math.max(maxName, font.width(names[i]));
		}
		int pingW = hidePing ? 0 : (pingNum ? font.width("999ms") : 11);
		int cellW = headW + maxName + 6 + pingW;

		// columns: <=20 per column, then widen
		int rows = n, cols = 1;
		while (rows > 20) {
			cols++;
			rows = (n + cols - 1) / cols;
		}
		int gap = 5;
		int totalW = cols * cellW + (cols - 1) * gap;
		int cx = screenWidth / 2;
		int left = cx - totalW / 2;
		int y = 10;

		// header
		if (header != null && !disableHeader) {
			y = drawCentered(g, font, header, cx, y, headerCol) + 1;
		}
		int listTop = y;

		for (int i = 0; i < n; i++) {
			int col = i / rows, row = i % rows;
			int x = left + col * (cellW + gap);
			int ry = listTop + row * ROW_H;
			PlayerInfo info = list.get(i);

			g.fill(x - 1, ry - 1, x + cellW + 1, ry + 8, bg);

			int px = x;
			if (heads) {
				PlayerFaceRenderer.draw(g, info.getSkin(), px, ry, 8);
				px += ROW_H;
			}
			boolean isSelf = self != null && info.getProfile().getId().equals(self);
			int nameCol = (isSelf && highlightSelf)
					? Mods.color("tablist", "selfColor") : 0xFFFFFFFF;
			g.drawString(font, names[i], px, ry, nameCol, false);

			if (!hidePing) {
				int ping = info.getLatency();
				if (pingNum) {
					String s = ping < 0 ? "?" : ping + "ms";
					g.drawString(font, s, x + cellW - font.width(s), ry, pingColor(ping), false);
				} else {
					drawPingBars(g, x + cellW - 10, ry, ping);
				}
			}
		}

		if (footer != null && !disableFooter) {
			int fy = listTop + rows * ROW_H + 1;
			drawCentered(g, font, footer, cx, fy, footerCol);
		}
	}

	private static Component displayName(PlayerInfo info) {
		Component c = info.getTabListDisplayName();
		return c != null ? c : Component.literal(info.getProfile().getName());
	}

	/** Draws each line of `text` (split on newlines) centered on `cx`; returns the y
	 *  below the last line. The chosen colour overrides the source formatting so the
	 *  header/footer colour pickers actually take effect. */
	private static int drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
		for (String line : text.getString().split("\n")) {
			g.drawString(font, line, cx - font.width(line) / 2, y, color, false);
			y += ROW_H;
		}
		return y;
	}

	private static int pingColor(int ping) {
		if (ping < 0) {
			return 0xFFAAAAAA;
		}
		if (ping < 150) {
			return 0xFF44DD44;
		}
		if (ping < 300) {
			return 0xFF9BDD44;
		}
		if (ping < 600) {
			return 0xFFDDDD44;
		}
		if (ping < 1000) {
			return 0xFFDD9B44;
		}
		return 0xFFDD4444;
	}

	/** Five signal bars, filled by ping level — a self-drawn stand-in for vanilla's
	 *  connection sprite (avoids depending on private sprite ids). */
	private static void drawPingBars(GuiGraphics g, int x, int y, int ping) {
		int level = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
		for (int b = 0; b < 5; b++) {
			int h = 2 + b;
			int bx = x + b * 2;
			int col = b < level ? 0xFFFFFFFF : 0x55FFFFFF;
			g.fill(bx, y + 7 - h, bx + 1, y + 7, col);
		}
	}
}
