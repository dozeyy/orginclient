#!/usr/bin/env python3
"""Shared helper: bake a text string into a smooth white texture with optional
letter-spacing and a soft glow, returning the image + ink-box metrics.

Used by generate_wordmark.py (the "Origin" wordmark). The wordmark must appear
instantly (before Minecraft's own font loads), so it's baked this way -- a fixed
image, not a dynamic glyph atlas, so it carries none of the earlier
custom-font-rendering risk.
"""
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont


def render_text(font, text, letter_spacing_px=0.0, pad=90, glow_blur=40, glow_alpha=0.30):
    """Render `text` (drawn char-by-char with letter_spacing_px between glyphs)
    to an RGBA image cropped to the ink + `pad`. Returns (img, meta) where meta
    has width/height/inkX/inkY/inkWidth/inkHeight, plus `letters`: one
    full-height [x0,x1] band per glyph (final-image px), partitioned at the
    midpoints between glyph centers. The in-game per-letter reveal blits these
    bands with a staggered fade so the wordmark builds up letter by letter."""
    # First pass: pen positions
    margin = pad + 40
    baseline = margin + font.getmetrics()[0]
    positions = []
    pen = 0.0
    for ch in text:
        adv = font.getlength(ch)
        positions.append((ch, pen))
        pen += adv + letter_spacing_px
    total_adv = pen - (letter_spacing_px if text else 0)

    canvas_w = int(total_adv + 2 * margin)
    canvas_h = int(baseline + font.getmetrics()[1] + margin)
    layer = Image.new("L", (max(canvas_w, 1), max(canvas_h, 1)), 0)
    draw = ImageDraw.Draw(layer)
    for ch, pen_x in positions:
        draw.text((margin + pen_x, baseline), ch, fill=255, font=font, anchor="ls")

    # crop to ink + pad
    bbox = layer.getbbox()
    if bbox is None:
        bbox = (0, 0, 1, 1)
    l, t, r, b = bbox
    l = max(0, l - pad); t = max(0, t - pad)
    r = min(layer.width, r + pad); b = min(layer.height, b + pad)
    layer = layer.crop((l, t, r, b))

    ink_l = pad if (bbox[0] - l) >= pad else (bbox[0] - l)
    ink_t = pad if (bbox[1] - t) >= pad else (bbox[1] - t)
    ink_w = bbox[2] - bbox[0]
    ink_h = bbox[3] - bbox[1]

    # Per-letter vertical bands in final (cropped) coords. Glyph center =
    # margin + pen_x + advance/2, shifted left by the crop origin `l`. Cut lines
    # sit at the midpoint between neighbouring centers; the first band starts at
    # 0 and the last ends at the image width so the union covers everything.
    final_w = layer.width
    centers = [(margin + pen_x + font.getlength(ch) / 2.0) - l for ch, pen_x in positions]
    letters = []
    for i in range(len(centers)):
        x0 = 0.0 if i == 0 else (centers[i - 1] + centers[i]) / 2.0
        x1 = float(final_w) if i == len(centers) - 1 else (centers[i] + centers[i + 1]) / 2.0
        letters.append([max(0, int(round(x0))), min(final_w, int(round(x1)))])

    glow = layer.filter(ImageFilter.GaussianBlur(glow_blur)).point(lambda a: int(a * glow_alpha))
    alpha = ImageChops.lighter(layer, glow)
    white = Image.new("L", layer.size, 255)
    img = Image.merge("RGBA", (white, white, white, alpha))

    meta = {
        "text": text,
        "width": img.width, "height": img.height,
        "inkX": int(ink_l), "inkY": int(ink_t),
        "inkWidth": int(ink_w), "inkHeight": int(ink_h),
        "letters": letters,
    }
    return img, meta


def load_font(rel_path, size):
    from pathlib import Path
    here = Path(__file__).resolve().parent
    return ImageFont.truetype(str((here / rel_path).resolve()), size=size)
