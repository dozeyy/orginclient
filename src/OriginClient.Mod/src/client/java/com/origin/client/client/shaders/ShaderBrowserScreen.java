package com.origin.client.client.shaders;

import com.origin.client.client.gui.OriginUi;
import com.origin.client.client.theme.OriginTheme;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

// The in-client shader store: the top 20 shaderpacks (live Modrinth download
// ranking, 2026-07-09), each with a preview image, name, and a Download
// button that fills 0→100% and turns into a green "Complete". Downloads land
// in shaderpacks/ for the RUNNING game version, so on return to Iris's shader
// menu (re-init rescans the folder) the pack is there, ready to select.
public class ShaderBrowserScreen extends Screen {
	private final Screen parent;

	private double scroll = 0, scrollTarget = 0;
	private int maxScroll = 0;
	private long lastNanos = 0;

	private static final int ROW_H = 46;
	private static final int PREV_W = 72, PREV_H = 40;

	// {display name, modrinth slug}
	private static final String[][] DIR = {
			{"Complementary Reimagined", "complementary-reimagined"},
			{"Complementary Unbound", "complementary-unbound"},
			{"BSL Shaders", "bsl-shaders"},
			{"Photon Shaders", "photon-shader"},
			{"Solas Shader", "solas-shader"},
			{"Bliss Shaders", "bliss-shader"},
			{"Rethinking Voxels", "rethinking-voxels"},
			{"MakeUp — Ultra Fast", "makeup-ultra-fast-shaders"},
			{"Super Duper Vanilla", "super-duper-vanilla"},
			{"Insanity Shader", "insanity-shader"},
			{"Pastel Shaders", "pastel-shaders"},
			{"Mellow", "mellow"},
			{"AstraLex Shaders", "astralex"},
			{"Nostalgia Shader", "nostalgia-shader"},
			{"Miniature Shader", "miniature-shader"},
			{"VanillAA", "vanillaa"},
			{"Hysteria Shaders", "hysteria-shaders"},
			{"Kappa Shader", "kappa-shader"},
			{"Spooklementary", "spooklementary"},
			{"Potato Shaders", "potato-shaders"},
	};

	public ShaderBrowserScreen(Screen parent) {
		super(Component.literal("Download Shaders"));
		this.parent = parent;
	}

	@Override
	public void onClose() {
		// back to Iris's shader menu — its re-init rescans shaderpacks/ so any
		// pack downloaded here now shows up in the list
		minecraft.setScreen(parent);
	}

	private int pw() {
		return Math.min(460, width - 40);
	}

	private int ph() {
		return Math.min(height - 40, 320);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return (height - ph()) / 2;
	}

	private int listTop() {
		return py() + 40;
	}

	private int listBottom() {
		return py() + ph() - 12;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);

		long nanos = System.nanoTime();
		double dt = lastNanos == 0 ? 16.7 : Math.min(50.0, (nanos - lastNanos) / 1_000_000.0);
		lastNanos = nanos;
		scroll += (scrollTarget - scroll) * Math.min(1.0, dt / 60.0);

		OriginUi.panel(g, px(), py(), pw(), ph(), 12, 0xF2101010, OriginTheme.STROKE_STRONG);
		OriginUi.logo(g, px() + 18, py() + 18, 20, 1f);
		g.drawString(font, "Download Shaders", px() + 34, py() + 13, OriginTheme.TEXT, false);
		String ver = SharedConstants.getCurrentVersion().getName();
		String sub = "Auto-installs the right build for " + ver;
		g.drawString(font, sub, px() + pw() - 12 - font.width(sub), py() + 14, OriginTheme.MUTED, false);

		int x0 = px() + 12, x1 = px() + pw() - 12;
		int top = listTop(), bottom = listBottom();
		maxScroll = Math.max(0, DIR.length * ROW_H - (bottom - top));

		g.enableScissor(px(), top, px() + pw(), bottom);
		int y = top - (int) Math.round(scroll);
		for (int i = 0; i < DIR.length; i++) {
			if (y + ROW_H >= top && y <= bottom) {
				drawRow(g, i, x0, x1, y, mouseX, mouseY);
			}
			y += ROW_H;
		}
		g.disableScissor();
	}

	private void drawRow(GuiGraphics g, int i, int x0, int x1, int y, int mx, int my) {
		String name = DIR[i][0], slug = DIR[i][1];
		OriginUi.panel(g, x0, y, x1 - x0, ROW_H - 6, 8, 0x12FFFFFF, OriginTheme.STROKE);

		// preview thumbnail (or a placeholder tile while it loads)
		int ix = x0 + 8, iy = y + (ROW_H - 6 - PREV_H) / 2;
		OriginUi.panel(g, ix - 1, iy - 1, PREV_W + 2, PREV_H + 2, 4, 0xFF000000, OriginTheme.STROKE);
		var prev = ShaderPreviews.get(slug);
		if (prev != null) {
			g.blit(prev.id(), ix, iy, PREV_W, PREV_H, 0f, 0f, prev.w(), prev.h(), prev.w(), prev.h());
		} else {
			OriginUi.mark(g, ix + PREV_W / 2.0, iy + PREV_H / 2.0, 20, 0.25f);
		}

		int textX = ix + PREV_W + 12;
		g.drawString(font, name, textX, y + 12, OriginTheme.TEXT, false);
		g.drawString(font, "#" + (i + 1) + " most downloaded", textX, y + 24, OriginTheme.MUTED, false);

		// download control (right)
		int bw = 92;
		int bx = x1 - 10 - bw;
		int by = y + (ROW_H - 6 - 18) / 2;
		var st = ShaderDownloader.state(slug);
		switch (st.status()) {
			case WORKING -> {
				OriginUi.panel(g, bx, by + 4, bw, 10, 5, 0x30FFFFFF, OriginTheme.STROKE);
				int fill = (int) (bw * Math.max(0.04, st.progress()));
				OriginUi.panel(g, bx, by + 4, fill, 10, 5, 0xE6E0E0E0, 0);
				String pct = Math.round(st.progress() * 100) + "%";
				g.drawString(font, pct, bx + (bw - font.width(pct)) / 2, by - 6, OriginTheme.TEXT_DIM, false);
			}
			case DONE -> {
				OriginUi.panel(g, bx, by, bw, 18, 7, 0x2E2F7D53, 0xB32F7D53);
				String t = "✓ Complete";
				g.drawString(font, t, bx + (bw - font.width(t)) / 2, by + 5, 0xFF7FA98F, false);
			}
			case ERROR -> {
				OriginUi.panel(g, bx, by, bw, 18, 7, 0x1EB23A33, 0x66B23A33);
				String t = "Unavailable";
				g.drawString(font, t, bx + (bw - font.width(t)) / 2, by + 5, 0xFFC77A73, false);
			}
			default -> {
				boolean hover = in(mx, my, bx, by, bx + bw, by + 18);
				OriginUi.panel(g, bx, by, bw, 18, 7, hover ? 0x3EFFFFFF : 0x22FFFFFF,
						hover ? 0x66FFFFFF : OriginTheme.STROKE);
				String t = "Download";
				g.drawString(font, t, bx + (bw - font.width(t)) / 2, by + 5, OriginTheme.TEXT, false);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			int x1 = px() + pw() - 12;
			int top = listTop(), bottom = listBottom();
			if (my >= top && my <= bottom) {
				String ver = SharedConstants.getCurrentVersion().getName();
				int y = top - (int) Math.round(scroll);
				int bw = 92, bx = x1 - 10 - bw;
				for (int i = 0; i < DIR.length; i++) {
					int by = y + (ROW_H - 6 - 18) / 2;
					if (in(mx, my, bx, by, bx + bw, by + 18)
							&& ShaderDownloader.state(DIR[i][1]).status() == ShaderDownloader.Status.IDLE) {
						ShaderDownloader.start(DIR[i][1], ver);
						return true;
					}
					y += ROW_H;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - sy * 30));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private static boolean in(double mx, double my, int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}
}
