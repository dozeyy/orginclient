#!/usr/bin/env python3
"""Bake the Origin menu-button assets: a rounded-rect fill mask, a hairline
rounded-rect border mask, a soft hover glow, and the Inter button labels.

Same methodology as the rest of the in-game UI: pre-render smooth, high-res
assets here and verify them in-sandbox before they touch Minecraft. The fill and
border are alpha masks (white on transparent) that get tinted to the theme
colors in-game and 9-sliced to any button size (corners drawn from a fixed,
high-res corner region so they stay crisp at small sizes; straight edges are
uniform so they stretch cleanly). Labels are baked per fixed English string and
keyed by text (fallback to vanilla font in-game for anything without a texture).

Usage: python3 generate_buttons.py
Requires: Pillow, ../loading-screen/bake_text.py, ../font-atlas/fonts/Inter-500.ttf
Output: ../../src/OriginClient.Mod/.../assets/originclient/textures/ui/
        button_fill.png, button_border.png, button_glow.png,
        buttons.json, label_*.png, labels.json
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str((HERE / ".." / "loading-screen").resolve()))
from bake_text import load_font  # noqa: E402

OUT = (HERE / ".." / ".." / "src" / "OriginClient.Mod" / "src" / "client" /
       "resources" / "assets" / "originclient" / "textures" / "ui").resolve()

TEX = 96          # square texture size for fill/border
CORNER = 24       # corner region size (px) used for 9-slicing; also the radius
BORDER_PX = 4     # border stroke in texture px (~1px in-game after down-scale)
SS = 4            # supersample for anti-aliasing

# Bake the likely vanilla title-menu strings (and variants) so the in-game
# label lookup, keyed by the button's exact getString(), matches; anything
# unmatched falls back to vanilla font.
LABELS = ["Singleplayer", "Multiplayer", "Options", "Quit", "Quit Game",
          "Realms", "Minecraft Realms"]
LABEL_CAP = 96


def _to_rgba(alpha_L):
    white = Image.new("L", alpha_L.size, 255)
    return Image.merge("RGBA", (white, white, white, alpha_L))


def bake_fill():
    big = TEX * SS
    img = Image.new("L", (big, big), 0)
    ImageDraw.Draw(img).rounded_rectangle([0, 0, big - 1, big - 1], radius=CORNER * SS, fill=255)
    return _to_rgba(img.resize((TEX, TEX), Image.LANCZOS))


def bake_border():
    big = TEX * SS
    img = Image.new("L", (big, big), 0)
    half = BORDER_PX * SS / 2.0
    ImageDraw.Draw(img).rounded_rectangle(
        [half, half, big - 1 - half, big - 1 - half],
        radius=CORNER * SS, outline=255, width=BORDER_PX * SS)
    return _to_rgba(img.resize((TEX, TEX), Image.LANCZOS))


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    bake_fill().save(OUT / "button_fill.png")
    bake_border().save(OUT / "button_border.png")
    (OUT / "buttons.json").write_text(json.dumps({
        "texSize": TEX, "corner": CORNER, "borderPx": BORDER_PX,
    }, indent=2))
    print(f"button masks: {TEX}x{TEX} (corner {CORNER}) -> button_*.png")

    # Labels baked as uniform, baseline-aligned cells (same height for every
    # label) so they all render at the same visual size regardless of
    # descenders, with the wordmark's subtle glow baked in.
    #
    # Baked at a LADDER of cell heights, one per Minecraft GUI scale: the
    # button label displays at ~14.4 GUI px (0.72 * the 20px button), which is
    # 14.4 * guiScale REAL pixels. A single bake is only pixel-perfect at one
    # GUI scale -- every other scale re-stretches it through GL_LINEAR and
    # softens/aliases ("sharpness in all GUI scaling should be perfect").
    # The in-game renderer picks the rung matching the current GUI scale and
    # draws it at exactly 1:1 texture-texel-to-screen-pixel.
    font = load_font("../font-atlas/fonts/Inter-500.ttf", LABEL_CAP)
    ascent, descent = font.getmetrics()
    pad = 14  # room for the glow around the ink
    cell_h = ascent + descent + 2 * pad
    baseline = pad + ascent
    ls = 0.02 * LABEL_CAP
    display_gui_px = 14.4  # label cell height on screen, in GUI units
    ladder = [round(display_gui_px * gs) for gs in range(1, 7)]  # GUI scales 1..6
    labels = {}
    for text in LABELS:
        positions = []
        pen = 0.0
        for ch in text:
            positions.append((ch, pen))
            pen += font.getlength(ch) + ls
        width = int(pen - ls) + 2 * pad
        layer = Image.new("L", (max(width, 1), cell_h), 0)
        draw = ImageDraw.Draw(layer)
        for ch, px in positions:
            draw.text((pad + px, baseline), ch, fill=255, font=font, anchor="ls")
        glow = layer.filter(ImageFilter.GaussianBlur(9)).point(lambda a: int(a * 0.18))
        master = ImageChops.lighter(layer, glow)

        slug = "".join(c if c.isalnum() else "_" for c in text.lower())
        cells = {}
        for target in ladder:
            scale = target / cell_h
            small = master.resize((max(1, round(master.width * scale)), target), Image.LANCZOS)
            fname = f"label_{slug}_{target}.png"
            _to_rgba(small).save(OUT / fname)
            cells[str(target)] = {"file": fname, "width": small.width}
        labels[text] = cells
        print(f"label '{text}': cells {ladder} -> label_{slug}_*.png")

    (OUT / "labels.json").write_text(json.dumps({"cells": ladder, "labels": labels}, indent=2))


if __name__ == "__main__":
    main()
