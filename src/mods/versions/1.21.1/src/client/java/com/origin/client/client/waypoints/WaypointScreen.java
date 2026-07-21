package com.origin.client.client.waypoints;

import com.origin.client.client.gui.OriginUi;
import com.origin.client.client.mods.Mods;
import com.origin.client.client.theme.OriginTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

// The Waypoints manager: a scrollable list (toggle / delete each, with an optional
// confirm), Quick Create, and a Create form (name, current-coords autofill, colour
// palette, display options). Origin-styled; reached from the Waypoints mod card or
// the Open-Waypoints keybind. Full per-waypoint edit is intentionally light for v1.
public class WaypointScreen extends Screen {
	private double scroll = 0;
	// create-form state
	private boolean creating = false;
	private String cName = "";
	private int cx, cy, cz;
	private boolean coordsSet = false;
	private int cColor = 0xFFFFFFFF;
	private boolean cText = true, cBeam = true, cHighlight = false, cDistance = true;
	private boolean nameFocused = false;
	// delete confirm
	private Waypoints.Waypoint pendingDelete = null;

	public WaypointScreen() {
		super(Component.literal("Waypoints"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return (height - ph()) / 2;
	}

	private int pw() {
		return Math.min(420, (int) (width * 0.7));
	}

	private int ph() {
		return (int) (height * 0.8);
	}

	private int listTop() {
		return py() + (creating ? 196 : 64);
	}

	private int listBottom() {
		return py() + ph() - 12;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int x = px(), y = py(), w = pw(), h = ph();
		OriginUi.panel(g, x, y, w, h, 10, 0xC80E0E0E, OriginTheme.STROKE);
		OriginUi.logo(g, x + 22, y + 20, 22, 1f);
		g.drawString(font, "Waypoints", x + 42, y + 10, OriginTheme.TEXT, false);
		int total = Waypoints.all().size();
		g.drawString(font, total + (total == 1 ? " waypoint" : " waypoints"), x + 42, y + 22, OriginTheme.MUTED, false);

		// top action buttons
		int by = y + 36;
		button(g, x + 12, by, 130, 18, creating ? "Cancel" : "Create Waypoint", mouseX, mouseY);
		button(g, x + 148, by, 110, 18, "Quick Create", mouseX, mouseY);
		// system toggles (right)
		boolean death = Mods.bool("waypoints", "deathWaypoints");
		String dt = "Death WP: " + (death ? "On" : "Off");
		button(g, x + w - 12 - font.width(dt) - 16, by, font.width(dt) + 16, 18, dt, mouseX, mouseY);

		if (creating) {
			renderCreateForm(g, x, y + 60, w, mouseX, mouseY);
		}

		// list
		int top = listTop(), bottom = listBottom();
		g.enableScissor(x, top, x + w, bottom);
		int ry = top - (int) Math.round(scroll);
		for (Waypoints.Waypoint wp : Waypoints.all()) {
			if (ry + 24 >= top && ry <= bottom) {
				renderRow(g, wp, x + 12, ry, w - 24, mouseX, mouseY);
			}
			ry += 28;
		}
		g.disableScissor();

		if (pendingDelete != null) {
			renderConfirm(g, mouseX, mouseY);
		}
		String hint = "Esc to close";
		g.drawString(font, hint, x + w - 10 - font.width(hint), y + h - 12, OriginTheme.MUTED, false);
	}

	private void renderCreateForm(GuiGraphics g, int x, int y, int w, int mx, int my) {
		OriginUi.panel(g, x + 12, y, w - 24, 130, 8, 0x30FFFFFF, OriginTheme.STROKE);
		// name
		g.drawString(font, "Name:", x + 20, y + 8, OriginTheme.TEXT_DIM, false);
		OriginUi.panel(g, x + 60, y + 4, 180, 16, 6, nameFocused ? OriginTheme.BOX_FILL_HOVER : OriginTheme.BOX_FILL,
				nameFocused ? OriginTheme.BOX_BORDER_HOVER : OriginTheme.BOX_BORDER);
		String shown = cName.isEmpty() && !nameFocused ? Waypoints.nextName("Waypoint") : cName;
		g.drawString(font, shown, x + 64, y + 8, cName.isEmpty() && !nameFocused ? OriginTheme.MUTED : OriginTheme.TEXT, false);
		// coords
		button(g, x + 20, y + 26, 150, 16, coordsSet ? (cx + " " + cy + " " + cz) : "Use Current Coordinates", mx, my);
		// color palette
		g.drawString(font, "Color:", x + 20, y + 50, OriginTheme.TEXT_DIM, false);
		for (int i = 0; i < Mods.PALETTE.length; i++) {
			int sx = x + 60 + i * 18;
			OriginUi.panel(g, sx, y + 46, 14, 14, 4, Mods.PALETTE[i], cColor == Mods.PALETTE[i] ? 0xFFFFFFFF : 0x40FFFFFF);
		}
		// display toggles
		toggle(g, x + 20, y + 70, "Text", cText, mx, my);
		toggle(g, x + 90, y + 70, "Beam", cBeam, mx, my);
		toggle(g, x + 160, y + 70, "Distance", cDistance, mx, my);
		toggle(g, x + 250, y + 70, "Highlight", cHighlight, mx, my);
		// create
		button(g, x + 20, y + 94, 100, 18, "Create", mx, my);
	}

	private void renderRow(GuiGraphics g, Waypoints.Waypoint wp, int x, int y, int w, int mx, int my) {
		boolean hover = mx >= x && mx <= x + w && my >= y && my < y + 24;
		OriginUi.panel(g, x, y, w, 24, 8, hover ? OriginTheme.BOX_FILL_HOVER : OriginTheme.BOX_FILL,
				hover ? OriginTheme.BOX_BORDER_HOVER : OriginTheme.BOX_BORDER);
		OriginUi.panel(g, x + 6, y + 7, 10, 10, 3, wp.color, 0x40FFFFFF);
		g.drawString(font, wp.name, x + 22, y + 3, wp.enabled ? OriginTheme.TEXT : OriginTheme.MUTED, false);
		String sub = wp.x + ", " + wp.y + ", " + wp.z + "  ·  " + wp.dimension.replace("minecraft:", "");
		g.drawString(font, sub, x + 22, y + 13, OriginTheme.MUTED, false);
		// enable toggle
		OriginUi.switchAt(g, "wp:" + wp.id, x + w - 74, y + 4, 30, wp.enabled, true);
		// delete
		boolean dh = mx >= x + w - 22 && mx <= x + w - 6 && my >= y + 6 && my <= y + 20;
		g.drawString(font, "✕", x + w - 18, y + 8, dh ? 0xFFC77A73 : 0x99C77A73, false);
	}

	private void renderConfirm(GuiGraphics g, int mx, int my) {
		int cw = 240, ch = 80;
		int x = (width - cw) / 2, y = (height - ch) / 2;
		g.fill(0, 0, width, height, 0x88000000);
		OriginUi.panel(g, x, y, cw, ch, 10, 0xF01A1A1A, OriginTheme.STROKE_STRONG);
		g.drawString(font, "Delete \"" + pendingDelete.name + "\"?", x + 12, y + 14, OriginTheme.TEXT, false);
		button(g, x + 12, y + 46, 100, 20, "Delete", mx, my);
		button(g, x + cw - 112, y + 46, 100, 20, "Cancel", mx, my);
	}

	private void button(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my) {
		boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
		OriginUi.panel(g, x, y, w, h, 7, hover ? OriginTheme.BOX_FILL_HOVER : OriginTheme.BOX_FILL,
				hover ? OriginTheme.BOX_BORDER_HOVER : OriginTheme.BOX_BORDER);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, OriginTheme.TEXT, false);
	}

	private void toggle(GuiGraphics g, int x, int y, String label, boolean on, int mx, int my) {
		g.drawString(font, label, x, y + 2, on ? OriginTheme.TEXT : OriginTheme.MUTED, false);
		OriginUi.switchAt(g, "cf:" + label, x + font.width(label) + 4, y - 1, 22, on, true);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int x = px(), y = py(), w = pw();
		if (pendingDelete != null) {
			int cw = 240, ch = 80, dx = (width - cw) / 2, dy = (height - ch) / 2;
			if (in(mx, my, dx + 12, dy + 46, dx + 112, dy + 66)) {
				Waypoints.remove(pendingDelete);
				pendingDelete = null;
			} else if (in(mx, my, dx + cw - 112, dy + 46, dx + cw - 12, dy + 66)) {
				pendingDelete = null;
			}
			return true;
		}
		int by = y + 36;
		if (in(mx, my, x + 12, by, x + 142, by + 18)) {          // Create / Cancel
			creating = !creating;
			return true;
		}
		if (in(mx, my, x + 148, by, x + 258, by + 18)) {         // Quick Create
			Waypoints.quickCreate();
			return true;
		}
		String dt = "Death WP: " + (Mods.bool("waypoints", "deathWaypoints") ? "On" : "Off");
		int dbx = x + w - 12 - font.width(dt) - 16;
		if (in(mx, my, dbx, by, dbx + font.width(dt) + 16, by + 18)) {
			Mods.set("waypoints", "deathWaypoints", !Mods.bool("waypoints", "deathWaypoints"));
			return true;
		}
		if (creating && clickCreateForm(mx, my, x, y + 60, w)) {
			return true;
		}
		// list rows
		int top = listTop(), bottom = listBottom();
		if (my >= top && my <= bottom) {
			int ry = top - (int) Math.round(scroll);
			for (Waypoints.Waypoint wp : new java.util.ArrayList<>(Waypoints.all())) {
				int rx = x + 12, rw = w - 24;
				if (my >= ry && my < ry + 24) {
					if (in(mx, my, rx + rw - 74, ry + 4, rx + rw - 44, ry + 20)) {   // toggle
						wp.enabled = !wp.enabled;
						Waypoints.save();
						return true;
					}
					if (in(mx, my, rx + rw - 22, ry + 6, rx + rw - 6, ry + 20)) {     // delete
						if (Mods.bool("waypoints", "confirmDelete")) {
							pendingDelete = wp;
						} else {
							Waypoints.remove(wp);
						}
						return true;
					}
				}
				ry += 28;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	private boolean clickCreateForm(double mx, double my, int x, int y, int w) {
		nameFocused = in(mx, my, x + 60, y + 4, x + 240, y + 20);
		if (nameFocused) {
			return true;
		}
		if (in(mx, my, x + 20, y + 26, x + 170, y + 42)) {       // use current coords
			var mc = Minecraft.getInstance();
			if (mc.player != null) {
				BlockPos p = mc.player.blockPosition();
				cx = p.getX();
				cy = p.getY();
				cz = p.getZ();
				coordsSet = true;
			}
			return true;
		}
		for (int i = 0; i < Mods.PALETTE.length; i++) {          // colour swatch
			int sx = x + 60 + i * 18;
			if (in(mx, my, sx, y + 46, sx + 14, y + 60)) {
				cColor = Mods.PALETTE[i];
				return true;
			}
		}
		if (in(mx, my, x + 20, y + 70, x + 60, y + 84)) {
			cText = !cText;
			return true;
		}
		if (in(mx, my, x + 90, y + 70, x + 130, y + 84)) {
			cBeam = !cBeam;
			return true;
		}
		if (in(mx, my, x + 160, y + 70, x + 220, y + 84)) {
			cDistance = !cDistance;
			return true;
		}
		if (in(mx, my, x + 250, y + 70, x + 310, y + 84)) {
			cHighlight = !cHighlight;
			return true;
		}
		if (in(mx, my, x + 20, y + 94, x + 120, y + 112)) {      // create
			var mc = Minecraft.getInstance();
			if (!coordsSet && mc.player != null) {
				BlockPos p = mc.player.blockPosition();
				cx = p.getX();
				cy = p.getY();
				cz = p.getZ();
			}
			String name = cName.isEmpty() ? Waypoints.nextName("Waypoint") : cName;
			Waypoints.Waypoint wp = Waypoints.create(name, cx, cy, cz, Waypoints.currentDim(), cColor);
			wp.showText = cText;
			wp.showBeam = cBeam;
			wp.showDistance = cDistance;
			wp.highlightBlock = cHighlight;
			Waypoints.save();
			creating = false;
			cName = "";
			coordsSet = false;
			cColor = 0xFFFFFFFF;
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (creating && nameFocused && chr >= 32 && cName.length() < 32) {
			cName += chr;
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (creating && nameFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !cName.isEmpty()) {
			cName = cName.substring(0, cName.length() - 1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (pendingDelete != null) {
				pendingDelete = null;
			} else if (creating) {
				creating = false;
			} else {
				Minecraft.getInstance().setScreen(null);
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int content = Waypoints.all().size() * 28;
		int maxScroll = Math.max(0, content - (listBottom() - listTop()));
		scroll = Math.max(0, Math.min(maxScroll, scroll - sy * 24));
		return true;
	}

	private static boolean in(double mx, double my, double x0, double y0, double x1, double y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}
}
