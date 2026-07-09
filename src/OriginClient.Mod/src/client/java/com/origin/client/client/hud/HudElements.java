package com.origin.client.client.hud;

import com.origin.client.client.OriginClientMod;
import com.origin.client.client.gui.OriginColorPicker;
import com.origin.client.client.gui.OriginUi;
import com.origin.client.client.mods.ClickStats;
import com.origin.client.client.mods.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// Every movable HUD element: its owning mod, default placement, measured
// size, and renderer. One dispatcher draws them all; the HUD editor drags the
// same list. Settings are read LIVE from the Mods registry on every frame —
// flipping any option updates the element instantly, no restart.
//
// Sizing rule (Will): an element's measured box is always big enough for the
// element with EVERYTHING turned on, so the edit-screen outline never
// mismatches the content no matter which options are active.
public final class HudElements {
	public static final int TEXT = 0xFFE0E0E0;
	private static final int PANEL = 0x66101010;

	// Set by the HUD editor for its whole lifetime: elements that need worn
	// armor / an active potion to be visible draw sample content instead, and
	// measure/hit-testing see the same preview the render shows.
	public static volatile boolean editorPreview = false;

	public interface Renderer {
		void render(GuiGraphics g, Minecraft mc, int w, int h);
	}

	public record Element(String id, String modId, String label, HudPos defaults,
						  java.util.function.Function<Minecraft, int[]> measure, Renderer renderer) {
		public HudPos pos() {
			return HudPos.load(id, new HudPos(defaults.anchor, defaults.dx, defaults.dy, defaults.scale));
		}
	}

	public static final List<Element> ALL = new ArrayList<>();

	private static long fpsSampledAt = 0;
	private static int fpsSample = 0;

	// ---- shared option plumbing ----

	/** The mod's own Scale slider (1.0 when the mod has no such option). */
	private static float ms(String modId) {
		double s = Mods.num(modId, "scale");
		return (float) (s <= 0.05 ? 1.0 : s);
	}

	private static boolean chatOpen(Minecraft mc) {
		return mc.screen instanceof ChatScreen;
	}

	/** Standard text row backing, gated by the mod's Show Background toggle. */
	private static void bg(GuiGraphics g, String modId, int w, int h) {
		if (Mods.bool(modId, "showBackground")) {
			OriginUi.panel(g, -3, -3, w + 6, h + 6, 5, PANEL, 0);
		}
	}

	private static void bgColored(GuiGraphics g, String modId, String colorKey, int w, int h) {
		if (Mods.bool(modId, "showBackground")) {
			OriginUi.panel(g, -3, -3, w + 6, h + 6, 5, OriginColorPicker.liveColor(modId, colorKey), 0);
		}
	}

	static {
		// ---- FPS ----
		add("fps", "fps", "FPS", new HudPos(0, 6, 6, 1.0), mc -> {
			float s = ms("fps");
			return new int[]{(int) ((mc.font.width("[9999 FPS]") + 2) * s), (int) (12 * s)};
		}, (g, mc, w, h) -> {
			long now = System.currentTimeMillis();
			if (now - fpsSampledAt > 250) {
				fpsSampledAt = now;
				fpsSample = mc.getFps();
			}
			float s = ms("fps");
			var p = g.pose();
			p.pushPose();
			p.scale(s, s, 1f);
			int w0 = (int) (w / s), h0 = (int) (h / s);
			bg(g, "fps", w0, h0);
			String txt = Mods.bool("fps", "reverseOrder") ? fpsSample + " FPS" : "FPS: " + fpsSample;
			if (Mods.bool("fps", "showBrackets")) {
				txt = "[" + txt + "]";
			}
			g.drawString(mc.font, txt, 1, 2, OriginColorPicker.liveColor("fps", "color"), Mods.bool("fps", "textShadow"));
			p.popPose();
		});

		// ---- CPS ----
		add("cps", "cps", "CPS", new HudPos(0, 6, 22, 1.0), mc -> {
			float s = ms("cps");
			return new int[]{(int) ((mc.font.width("CPS: 99 | 99") + 2) * s), (int) (12 * s)};
		}, (g, mc, w, h) -> {
			float s = ms("cps");
			var p = g.pose();
			p.pushPose();
			p.scale(s, s, 1f);
			int w0 = (int) (w / s), h0 = (int) (h / s);
			bg(g, "cps", w0, h0);
			String value = String.valueOf(ClickStats.leftCps());
			if (Mods.bool("cps", "rightClick")) {
				value += " | " + ClickStats.rightCps();
			}
			String txt = !Mods.bool("cps", "showText") ? value
					: Mods.bool("cps", "reverseText") ? value + " CPS" : "CPS: " + value;
			g.drawString(mc.font, txt, 1, 2, OriginColorPicker.liveColor("cps", "color"), Mods.bool("cps", "textShadow"));
			p.popPose();
		});

		// ---- Coordinates ----
		add("coords", "coords", "Coords", new HudPos(0, 6, 38, 1.0), mc -> {
			float s = ms("coords");
			// max: X + Y + Z (or one combined line) + C + Facing + Biome
			boolean horizontal = Mods.mode("coords", "listMode").equals("Horizontal");
			int lines = (horizontal ? 1 : 3) + 3;
			return new int[]{(int) (150 * s), (int) ((lines * 10 + 2) * s)};
		}, (g, mc, w, h) -> {
			var pl = mc.player;
			if (pl == null) {
				return;
			}
			if (chatOpen(mc) && !Mods.bool("coords", "showWhileTyping")) {
				return;
			}
			float s = ms("coords");
			var p = g.pose();
			p.pushPose();
			p.scale(s, s, 1f);
			int w0 = (int) (w / s), h0 = (int) (h / s);
			bgColored(g, "coords", "bgColor", w0, h0);
			boolean shadow = Mods.bool("coords", "textShadow");
			boolean dec = Mods.bool("coords", "decimal");
			int color = TEXT;
			int y = 1;
			String fx = dec ? String.format("%.1f", pl.getX()) : String.valueOf(pl.blockPosition().getX());
			String fy = dec ? String.format("%.1f", pl.getY()) : String.valueOf(pl.blockPosition().getY());
			String fz = dec ? String.format("%.1f", pl.getZ()) : String.valueOf(pl.blockPosition().getZ());
			boolean sx = Mods.bool("coords", "x"), sy2 = Mods.bool("coords", "y"), sz = Mods.bool("coords", "z");
			if (Mods.mode("coords", "listMode").equals("Horizontal")) {
				StringBuilder sb = new StringBuilder("XYZ:");
				if (sx) sb.append(' ').append(fx);
				if (sy2) sb.append(sx ? " / " : " ").append(fy);
				if (sz) sb.append(sx || sy2 ? " / " : " ").append(fz);
				if (sx || sy2 || sz) {
					g.drawString(mc.font, sb.toString(), 1, y, color, shadow);
					y += 10;
				}
			} else {
				if (sx) { g.drawString(mc.font, "X: " + fx, 1, y, color, shadow); y += 10; }
				if (sy2) { g.drawString(mc.font, "Y: " + fy, 1, y, color, shadow); y += 10; }
				if (sz) { g.drawString(mc.font, "Z: " + fz, 1, y, color, shadow); y += 10; }
			}
			if (Mods.bool("coords", "renderers")) {
				int c;
				try {
					c = mc.levelRenderer.countRenderedSections();
				} catch (Throwable t) {
					c = -1;
				}
				if (c >= 0) {
					g.drawString(mc.font, "C: " + c, 1, y, color, shadow);
					y += 10;
				}
			}
			if (Mods.bool("coords", "direction")) {
				Direction d = pl.getDirection();
				g.drawString(mc.font, String.format("Facing: %s (%.0f°)",
						d.getName().substring(0, 1).toUpperCase() + d.getName().substring(1),
						net.minecraft.util.Mth.wrapDegrees(pl.getYRot())), 1, y, color, shadow);
				y += 10;
			}
			if (Mods.bool("coords", "biome") && mc.level != null) {
				var biome = mc.level.getBiome(BlockPos.containing(pl.position()));
				String name = biome.unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("unknown");
				g.drawString(mc.font, "Biome: " + name, 1, y, color, shadow);
			}
			p.popPose();
		});

		// ---- Key Strokes ----
		add("keystrokes", "keystrokes", "Keystrokes", new HudPos(3, 6, -40, 1.0), mc -> {
			float s = ks();
			// full grid: WASD rows + clicks + space bar at max thickness (78px)
			return new int[]{(int) (70 * s), (int) (78 * s)};
		}, HudElements::renderKeystrokes);

		// ---- Potion Effects ----
		add("potionhud", "potionhud", "Potions", new HudPos(2, -6, 6, 1.0), mc -> {
			int n = potionRows(mc).size();
			return new int[]{130, Math.max(1, n) * 20};
		}, (g, mc, w, h) -> {
			if (mc.player == null) {
				return;
			}
			if (chatOpen(mc) && !Mods.bool("potionhud", "showWhileTyping")) {
				return;
			}
			var rows = potionRows(mc);
			if (rows.isEmpty()) {
				return; // nothing active in-game: no box, no background at all
			}
			// background hugs the actual rows — one effect gets a one-row
			// backing, more effects grow it; never a big empty gray area
			if (Mods.bool("potionhud", "showBackground")) {
				int wAct = 0;
				for (PotionRow row : rows) {
					wAct = Math.max(wAct, 22 + rowTextWidth(mc, row));
				}
				OriginUi.panel(g, -3, -3, Math.min(130, wAct) + 6, rows.size() * 20 + 4, 5,
						OriginColorPicker.liveColor("potionhud", "bgColor"), 0);
			}
			int y = 0;
			for (PotionRow row : rows) {
				drawEffectRow(g, mc, y, row.effect, row.duration, row.infinite);
				y += 20;
			}
		});

		// ---- Armor Status ----
		add("armorhud", "armorhud", "Armor", new HudPos(6, 6, -24, 1.0), mc -> {
			boolean vertical = Mods.mode("armorhud", "listMode").equals("Vertical");
			// room for durability text in every position mode
			return vertical ? new int[]{18 + 44, 5 * 19} : new int[]{5 * 19 + 40, 30};
		}, HudElements::renderArmor);

		// ---- Server Address ----
		add("serveraddress", "serveraddress", "Server IP", new HudPos(2, -6, 6, 1.0), mc -> {
			float s = ms("serveraddress");
			return new int[]{(int) ((16 + mc.font.width("255.255.255.255:25565 (99)")) * s), (int) (16 * s)};
		}, (g, mc, w, h) -> {
			float s = ms("serveraddress");
			var p = g.pose();
			p.pushPose();
			p.scale(s, s, 1f);
			int w0 = (int) (w / s), h0 = (int) (h / s);
			bg(g, "serveraddress", w0, h0);
			boolean shadow = Mods.bool("serveraddress", "textShadow");
			int color = OriginColorPicker.liveColor("serveraddress", "color");
			int x = 0;
			if (Mods.bool("serveraddress", "serverIcon")) {
				OriginUi.icon(g, "serveraddress", 0, 1, 14, color);
				x = 17;
			}
			ServerData server = mc.getCurrentServer();
			if (server == null) {
				g.drawString(mc.font, "Singleplayer", x, 4, color, shadow);
			} else {
				int players = mc.getConnection() != null ? mc.getConnection().getOnlinePlayers().size() : 0;
				g.drawString(mc.font, server.ip + " (" + players + ")", x, 4, color, shadow);
			}
			p.popPose();
		});

		// ---- Sprint/Sneak state ----
		add("sprintstate", "togglesprint", "Sprint state", new HudPos(6, 6, -6, 1.0), mc -> text("Sprint (Toggled)"),
				(g, mc, w, h) -> {
					if (!Mods.bool("togglesprint", "hud")) {
						return;
					}
					var f = OriginClientMod.FEATURES;
					String s = f.sprintToggledOn ? "Sprint (Toggled)" : f.sneakToggledOn ? "Sneak (Toggled)" : null;
					if (s == null && editorPreview) {
						s = "Sprint (Toggled)";
					}
					if (s != null) {
						g.drawString(mc.font, s, 0, 0, TEXT);
					}
				});
	}

	private static void add(String id, String modId, String label, HudPos def,
							java.util.function.Function<Minecraft, int[]> measure, Renderer r) {
		ALL.add(new Element(id, modId, label, def, measure, r));
	}

	private static int[] text(String sample) {
		return new int[]{Minecraft.getInstance().font.width(sample), 10};
	}

	private static String trim(Minecraft mc, String s, int px) {
		return mc.font.width(s) <= px ? s : mc.font.plainSubstrByWidth(s, px - 6) + "…";
	}

	// ---- potions ----

	private record PotionRow(Holder<MobEffect> effect, int duration, boolean infinite) {
	}

	private static int rowTextWidth(Minecraft mc, PotionRow row) {
		String name = row.effect.value().getDisplayName().getString();
		int secs = row.duration / 20;
		String time = row.infinite ? "∞" : secs / 60 + ":" + String.format("%02d", secs % 60);
		return mc.font.width(name + " " + time);
	}

	private static List<PotionRow> potionRows(Minecraft mc) {
		List<PotionRow> rows = new ArrayList<>();
		if (mc.player != null) {
			boolean excludePerm = Mods.bool("potionhud", "excludePermanent");
			for (MobEffectInstance e : mc.player.getActiveEffects()) {
				if (excludePerm && e.isInfiniteDuration()) {
					continue;
				}
				rows.add(new PotionRow(e.getEffect(), e.getDuration(), e.isInfiniteDuration()));
			}
		}
		if (rows.isEmpty() && editorPreview) {
			rows.add(new PotionRow(MobEffects.MOVEMENT_SPEED, 83 * 20, false));
			rows.add(new PotionRow(MobEffects.DAMAGE_BOOST, 45 * 20, false));
		}
		return rows;
	}

	private static void drawEffectRow(GuiGraphics g, Minecraft mc, int y, Holder<MobEffect> effect, int duration, boolean infinite) {
		int secs = duration / 20;
		// Blink: within the threshold, the row pulses like vanilla's expiring icons
		if (!infinite && Mods.bool("potionhud", "blink")
				&& secs < Mods.num("potionhud", "blinkDuration")
				&& (System.currentTimeMillis() / 400) % 2 == 0) {
			return;
		}
		try {
			var sprite = mc.getMobEffectTextures().get(effect);
			g.blit(0, y, 0, 18, 18, sprite);
		} catch (Throwable t) {
			g.fill(2, y + 4, 12, y + 14, 0xFF000000 | effect.value().getColor());
		}
		boolean shadow = Mods.bool("potionhud", "textShadow");
		boolean minimal = Mods.bool("potionhud", "minimal");
		boolean showName = Mods.bool("potionhud", "effectName") && !minimal;
		String name = effect.value().getDisplayName().getString();
		if (Mods.bool("potionhud", "uppercase")) {
			name = name.toUpperCase(java.util.Locale.ROOT);
		}
		String time = infinite ? "∞"
				: Mods.bool("potionhud", "formattedDurations")
				? secs / 60 + ":" + String.format("%02d", secs % 60)
				: secs + "s";
		int nameColor = Mods.bool("potionhud", "colorByEffect")
				? 0xFF000000 | effect.value().getColor()
				: OriginColorPicker.liveColor("potionhud", "textColor");
		int timeColor = OriginColorPicker.liveColor("potionhud", "durationColor");
		int x = 22;
		if (!showName) {
			g.drawString(mc.font, time, x, y + 5, timeColor, shadow);
			return;
		}
		String nm = trim(mc, name, 74);
		if (Mods.bool("potionhud", "reversedText")) {
			g.drawString(mc.font, time, x, y + 5, timeColor, shadow);
			g.drawString(mc.font, nm, x + mc.font.width(time + " "), y + 5, nameColor, shadow);
		} else {
			g.drawString(mc.font, nm, x, y + 5, nameColor, shadow);
			g.drawString(mc.font, time, x + mc.font.width(nm + " "), y + 5, timeColor, shadow);
		}
	}

	// ---- armor ----

	private static void renderArmor(GuiGraphics g, Minecraft mc, int w, int h) {
		if (mc.player == null) {
			return;
		}
		List<ItemStack> items = new ArrayList<>();
		for (ItemStack s : mc.player.getInventory().armor) {
			if (!s.isEmpty()) {
				items.add(s);
			}
		}
		java.util.Collections.reverse(items); // helmet first
		if (!mc.player.getMainHandItem().isEmpty()) {
			items.add(mc.player.getMainHandItem());
		}
		if (items.isEmpty() && editorPreview) {
			items = List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
					new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
					new ItemStack(Items.DIAMOND_SWORD));
		}
		if (items.isEmpty()) {
			return; // no armor / no held item in-game: draw nothing at all
		}
		boolean vertical = Mods.mode("armorhud", "listMode").equals("Vertical");
		String durPos = Mods.mode("armorhud", "durabilityPos");
		boolean shadow = Mods.bool("armorhud", "textShadow");
		int textColor = OriginColorPicker.liveColor("armorhud", "textColor");
		if (Mods.bool("armorhud", "showBackground")) {
			// background hugs however many pieces are actually worn, growing
			// piece by piece — never the full-loadout gray slab
			boolean dur = !durPos.equals("Hidden");
			int wAct = vertical ? 19 + (dur ? 30 : 0) : items.size() * 19;
			int hAct = vertical ? items.size() * 19 : 17 + (dur ? 10 : 0);
			OriginUi.panel(g, -3, -3, wAct + 6, hAct + 6, 5, PANEL, 0);
		}

		int x = 0, y = 0;
		for (ItemStack stack : items) {
			g.renderItem(stack, x, y);
			if (Mods.bool("armorhud", "itemCount")) {
				g.renderItemDecorations(mc.font, stack, x, y);
			}
			if (!durPos.equals("Hidden") && stack.isDamageableItem()) {
				int remaining = stack.getMaxDamage() - stack.getDamageValue();
				String txt = Mods.mode("armorhud", "damageDisplay").equals("Percent")
						? (int) Math.round(remaining * 100.0 / stack.getMaxDamage()) + "%"
						: String.valueOf(remaining);
				// Left/Right placements only make sense stacked vertically; in
				// the horizontal row they'd overflow the box, so fall to Below.
				String pos2 = vertical ? durPos : "Below";
				int tx = switch (pos2) {
					case "Left" -> x - mc.font.width(txt) - 2;
					case "Below" -> x + (16 - mc.font.width(txt)) / 2;
					default -> x + 19; // Right
				};
				int ty = pos2.equals("Below") ? y + 17 : y + 5;
				g.drawString(mc.font, txt, tx, ty, textColor, shadow);
			}
			if (vertical) {
				y += 19;
			} else {
				x += 19;
			}
		}
	}

	// ---- keystrokes ----

	/** Combined size factor: the mod's Scale slider x its Box Size slider. */
	private static float ks() {
		double s = Mods.num("keystrokes", "scale");
		double b = Mods.num("keystrokes", "boxSize");
		return (float) ((s <= 0.05 ? 1 : s) * (b <= 0.05 ? 1 : b));
	}

	private static void renderKeystrokes(GuiGraphics g, Minecraft mc, int w, int h) {
		float s = ks();
		var p = g.pose();
		p.pushPose();
		p.scale(s, s, 1f);
		var o = mc.options;
		boolean movement = Mods.bool("keystrokes", "showMovement");
		boolean arrows = Mods.bool("keystrokes", "arrows");
		if (movement) {
			key(g, mc, 24, 0, 22, 22, arrows ? "↑" : "W", o.keyUp.isDown());
			key(g, mc, 0, 24, 22, 22, arrows ? "←" : "A", o.keyLeft.isDown());
			key(g, mc, 24, 24, 22, 22, arrows ? "↓" : "S", o.keyDown.isDown());
			key(g, mc, 48, 24, 22, 22, arrows ? "→" : "D", o.keyRight.isDown());
		}
		if (Mods.bool("keystrokes", "showClicks")) {
			key(g, mc, 0, 48, 34, 14, "LMB", ClickStats.leftDown);
			key(g, mc, 36, 48, 34, 14, "RMB", ClickStats.rightDown);
		}
		if (Mods.bool("keystrokes", "showSpace")) {
			int thick = (int) Math.max(1, Math.min(10, Mods.num("keystrokes", "spacebarThickness"))) + 4;
			key(g, mc, 0, 64, 70, thick, "", o.keyJump.isDown());
		}
		p.popPose();
	}

	private static void key(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, String label, boolean down) {
		// Key Fade Delay drives the press/release fade between the two color sets
		double fade = Math.max(50, Mods.num("keystrokes", "keyFadeDelay"));
		float k = OriginUi.anim("ks:" + label + x, down, fade);
		int bgUp = OriginColorPicker.liveColor("keystrokes", "bgColor");
		int bgDown = OriginColorPicker.liveColor("keystrokes", "bgColorPressed");
		int fill = com.origin.client.client.theme.OriginTheme.lerpColor(bgUp, bgDown, k);
		g.fill(x, y, x + w, y + h, fill);
		if (Mods.bool("keystrokes", "border")) {
			int t = (int) Math.max(1, Math.min(4, Math.round(Mods.num("keystrokes", "borderThickness"))));
			int bc = OriginColorPicker.liveColor("keystrokes", "borderColor");
			g.fill(x, y, x + w, y + t, bc);
			g.fill(x, y + h - t, x + w, y + h, bc);
			g.fill(x, y, x + t, y + h, bc);
			g.fill(x + w - t, y, x + w, y + h, bc);
		}
		if (!label.isEmpty()) {
			int up = OriginColorPicker.liveColor("keystrokes", "color");
			int dn = OriginColorPicker.liveColor("keystrokes", "textColorPressed");
			int tc = com.origin.client.client.theme.OriginTheme.lerpColor(up, dn, k);
			int tw = mc.font.width(label);
			g.drawString(mc.font, label, x + (w - tw) / 2, y + (h - 8) / 2, tc, Mods.bool("keystrokes", "textShadow"));
		}
	}

	/** Per-module rounded backing at the element's own opacity (0 = none). */
	public static void drawBacking(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h, double bg) {
		if (bg <= 0.01) {
			return;
		}
		int a = (int) (bg * 255);
		OriginUi.panel(g, x - 4, y - 4, w + 8, h + 8, 6, (a << 24) | 0x0E0E0E, 0);
	}

	/** Main in-game dispatcher: draws every enabled element at its anchored,
	 *  scaled position. Skipped entirely while the HUD editor is open (the
	 *  editor draws its own draggable versions). */
	public static void renderAll(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen instanceof HudEditorScreen) {
			return;
		}
		int sw = g.guiWidth(), sh = g.guiHeight();
		for (Element e : ALL) {
			if (!Mods.on(e.modId())) {
				continue;
			}
			HudPos pos = e.pos();
			int[] size = e.measure().apply(mc);
			double w = size[0] * pos.scale, h = size[1] * pos.scale;
			double x = pos.x(sw, w), y = pos.y(sh, h);
			drawBacking(g, (int) x, (int) y, (int) w, (int) h, pos.bg);
			var p = g.pose();
			p.pushPose();
			p.translate(x, y, 0);
			p.scale((float) pos.scale, (float) pos.scale, 1f);
			try {
				e.renderer().render(g, mc, size[0], size[1]);
			} catch (Throwable t) {
				// One bad element must never take the HUD down.
			}
			p.popPose();
		}
	}
}
