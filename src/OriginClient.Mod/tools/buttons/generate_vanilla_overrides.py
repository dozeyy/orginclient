#!/usr/bin/env python3
"""Bake fully-transparent overrides for vanilla widget sprites.

Why: our mixins cancel AbstractButton/AbstractSliderButton/Checkbox
renderWidget and draw the Origin style instead -- but a white focus ring
(vanilla's `widget/slider_highlighted` sprite look) still appeared around the
initially-focused FOV slider on the Options screen, i.e. some uncancelled code
path still blits the vanilla slider sprites. Rather than chase that exact call
site, we override the sprites themselves with fully transparent textures (mod
assets layer above the vanilla pack), so ANY path that draws them renders
nothing and only the Origin style is visible. If the sprites are already dead
(every path cancelled), these overrides are harmless no-ops.

Kept untouched on purpose: `widget/button` / `widget/button_disabled` (if some
unknown vanilla path legitimately draws a plain button somewhere we don't
cover, it should stay visible rather than become an invisible click target).
"""
from pathlib import Path

from PIL import Image

OUT = (
    Path(__file__).resolve().parents[2]
    / "src/client/resources/assets/minecraft/textures/gui/sprites/widget"
)

# name -> (width, height), matching the vanilla sprite dimensions.
SPRITES = {
    "slider.png": (200, 20),
    "slider_highlighted.png": (200, 20),
    "slider_handle.png": (8, 20),
    "slider_handle_highlighted.png": (8, 20),
    "button_highlighted.png": (200, 20),
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, size in SPRITES.items():
        img = Image.new("RGBA", size, (0, 0, 0, 0))
        img.save(OUT / name)
        print(f"wrote {OUT / name} {size} fully transparent")


if __name__ == "__main__":
    main()
