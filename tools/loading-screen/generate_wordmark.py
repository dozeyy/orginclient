#!/usr/bin/env python3
"""Bake the loading-screen "Origin" wordmark into a smooth texture.

Why a texture and not live text: at the very first resource load, Minecraft's
own font isn't ready yet, so drawString renders "ORIGIN" as tofu boxes for the
first couple seconds (Will saw exactly this live, 2026-07-08). A pre-baked
wordmark image shows instantly, is perfectly centerable (known pixel size), and
carries none of the custom-glyph-rendering risk that sank the earlier font work
-- it's one fixed word rendered as an image, not a glyph atlas for arbitrary
dynamic text.

This variant uses the website's own typeface (Inter), matching the site's
wordmark. A Minecraft-pixel-style variant can replace wordmark.png without any
in-game code change if that's preferred.

Usage: python3 generate_wordmark.py [TEXT]
Requires: Pillow, ../font-atlas/fonts/Inter-600.ttf
Output: ../../src/OriginClient.Mod/src/client/resources/assets/originclient/textures/ui/
        wordmark.png + wordmark.json (pixel dims, for exact in-game centering)
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
OUT = (HERE / ".." / ".." / "src" / "OriginClient.Mod" / "src" / "client" /
       "resources" / "assets" / "originclient" / "textures" / "ui").resolve()
FONT = (HERE / ".." / "font-atlas" / "fonts" / "Inter-600.ttf").resolve()

TEXT = sys.argv[1] if len(sys.argv) > 1 else "Origin"
CAP = 260          # nominal glyph size, px (baked large, displayed scaled)
PAD = 90           # padding around the ink for the soft glow
GLOW_BLUR = 40     # gaussian blur radius for the white text-glow (website §5)
GLOW_ALPHA = 0.30  # peak glow opacity


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    font = ImageFont.truetype(str(FONT), size=CAP)

    # measure ink box
    tmp = Image.new("L", (10, 10), 0)
    l, t, r, b = ImageDraw.Draw(tmp).textbbox((0, 0), TEXT, font=font)
    ink_w, ink_h = r - l, b - t

    W, H = ink_w + 2 * PAD, ink_h + 2 * PAD
    ox, oy = PAD - l, PAD - t  # draw origin so ink lands inside the padding

    # crisp white text on its own alpha
    text_layer = Image.new("L", (W, H), 0)
    ImageDraw.Draw(text_layer).text((ox, oy), TEXT, fill=255, font=font)

    # soft glow = blurred copy of the text alpha, scaled down in opacity
    glow = text_layer.filter(ImageFilter.GaussianBlur(GLOW_BLUR)).point(lambda a: int(a * GLOW_ALPHA))

    # final alpha = max(crisp text, soft glow) so letters stay sharp and the
    # halo shows outside them; color is white everywhere
    alpha = ImageChops.lighter(text_layer, glow)
    white = Image.new("L", (W, H), 255)
    img = Image.merge("RGBA", (white, white, white, alpha))
    img.save(OUT / "wordmark.png")

    # ink box (excluding glow padding) so the in-game code can center on the
    # letters, not the glow halo
    meta = {
        "text": TEXT,
        "width": W, "height": H,
        "inkX": PAD, "inkY": PAD, "inkWidth": ink_w, "inkHeight": ink_h,
    }
    (OUT / "wordmark.json").write_text(json.dumps(meta, indent=2))
    print(f"wordmark '{TEXT}': texture {W}x{H}, ink {ink_w}x{ink_h} -> wordmark.png / wordmark.json")


if __name__ == "__main__":
    main()
