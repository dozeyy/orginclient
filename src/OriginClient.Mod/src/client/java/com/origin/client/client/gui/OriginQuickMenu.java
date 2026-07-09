package com.origin.client.client.gui;

import com.origin.client.client.theme.OriginTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

// The Right Shift quick menu (spec §2): just the ORIGIN mark + a single MODS
// button — no emotes, no cosmetics. MODS opens the full grid; Right Shift or
// Esc dismisses. Non-pausing, with a soft fade + scale-in so it reads like the
// client surfacing over the game rather than a jarring screen swap.
public class OriginQuickMenu extends Screen {
	private final long openedAt = System.currentTimeMillis();

	public OriginQuickMenu() {
		super(Component.literal("Origin"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int btnX() {
		return width / 2 - BTN_W / 2;
	}

	private int btnY() {
		return height / 2 + 34;
	}

	private static final int BTN_W = 140, BTN_H = 30;

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
		float p = (float) OriginTheme.easeOut(Math.min(1.0, (System.currentTimeMillis() - openedAt) / 160.0));
		g.fill(0, 0, width, height, (int) (0x88 * p) << 24);

		var pose = g.pose();
		pose.pushPose();
		float s = 0.96f + 0.04f * p;
		pose.translate(width / 2.0, height / 2.0, 0);
		pose.scale(s, s, 1f);
		pose.translate(-width / 2.0, -height / 2.0, 0);

		int cx = width / 2, cy = height / 2;
		OriginUi.glow(g, cx, cy - 26, 120, 0.18f * p);
		OriginUi.mark(g, cx, cy - 26, 46, p);
		String word = "ORIGIN";
		g.drawString(font, word, cx - font.width(word) / 2, cy + 8, alpha(OriginTheme.TEXT, p), false);

		boolean hover = in(mouseX, mouseY, btnX(), btnY(), btnX() + BTN_W, btnY() + BTN_H);
		float hv = OriginUi.anim("quick:mods", hover, 120.0);
		OriginUi.panel(g, btnX(), btnY() - Math.round(hv), BTN_W, BTN_H, 9,
				alpha(hover ? 0xFFF5F5F5 : 0xFFE4E4E4, p), 0);
		String label = "MODS";
		g.drawString(font, label, cx - font.width(label) / 2, btnY() + 11 - Math.round(hv), alpha(0xFF121212, p), false);
		pose.popPose();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && in(mx, my, btnX(), btnY(), btnX() + BTN_W, btnY() + BTN_H)) {
			Minecraft.getInstance().setScreen(new OriginModMenuScreen());
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private static int alpha(int argb, float a) {
		int aa = (int) (((argb >>> 24) & 0xFF) * a);
		return (aa << 24) | (argb & 0xFFFFFF);
	}

	private static boolean in(double mx, double my, int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}
}
