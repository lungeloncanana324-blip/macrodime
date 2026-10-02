#!/usr/bin/env python3
"""Builds the Android launcher icon and the Play Store icon from the iOS icon.

The iOS icon is one flat 1024 px square: a gold mark on a near-black gradient.
Android draws icons as two layers (adaptive icons) that the launcher masks to
its own shape and may move independently, so a flat square would show a seam.
This separates the two: the gold mark becomes the foreground layer, with its
anti-aliased edge turned into transparency, and the gradient is drawn as its own
background layer (res/drawable/ic_launcher_background.xml). The same mask in
white is the monochrome layer that Android 13 tints for themed icons.

    python3 android/scripts/make_icons.py

Rerun it if the iOS icon changes. Needs Pillow.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "MacroDime" / "Resources" / "Assets.xcassets" / "AppIcon.appiconset" / "AppIcon-1024.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"
STORE = ROOT / "android" / "play-store"

GOLD = (227, 180, 92)
# Background green channel at the top and bottom of the iOS gradient.
BG_TOP_G, BG_BOTTOM_G = 22, 9
# Coverage below this (out of 255) is gradient noise, not the mark.
NOISE_FLOOR = 12

# Adaptive icon layers are 108 dp; launchers show the middle 72 dp and only
# guarantee a 66 dp circle. The mark's knobs reach 37% of the iOS square from
# its centre, so drawing the square at 78 dp keeps the whole mark inside a
# 58 dp circle: clear of the safe zone's edge, with margin a circular mask needs.
LAYER_DP = 108
SQUARE_DP = 78
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def mark_alpha(source: Image.Image) -> Image.Image:
    """The gold mark's coverage, 0 to 255, recovered from the green channel."""
    rgb = source.convert("RGB")
    width, height = rgb.size
    alpha = Image.new("L", rgb.size)
    pixels = rgb.load()
    out = alpha.load()
    for y in range(height):
        background = BG_TOP_G + (BG_BOTTOM_G - BG_TOP_G) * y / (height - 1)
        span = GOLD[1] - background
        for x in range(width):
            coverage = (pixels[x, y][1] - background) / span
            value = max(0, min(255, round(coverage * 255)))
            # The gradient is estimated, not exact, so plain background reads as
            # a few percent coverage. Below the threshold it is background.
            out[x, y] = 0 if value < NOISE_FLOOR else value
    return alpha


def layer(alpha: Image.Image, colour: tuple[int, int, int], size: int) -> Image.Image:
    square = round(size * SQUARE_DP / LAYER_DP)
    mark = Image.new("RGBA", alpha.size, colour + (0,))
    mark.putalpha(alpha)
    mark = mark.resize((square, square), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset = (size - square) // 2
    canvas.alpha_composite(mark, (offset, offset))
    return canvas


def main() -> None:
    source = Image.open(SOURCE)
    alpha = mark_alpha(source)
    for density, scale in DENSITIES.items():
        size = round(LAYER_DP * scale)
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        layer(alpha, GOLD, size).save(folder / "ic_launcher_foreground.png", optimize=True)
        layer(alpha, (255, 255, 255), size).save(folder / "ic_launcher_monochrome.png", optimize=True)

    # Play Console wants a 512 px, 32-bit PNG and applies its own rounding.
    STORE.mkdir(parents=True, exist_ok=True)
    # The mark alone, cropped to its bounds, for the feature graphic.
    mark = Image.new("RGBA", alpha.size, GOLD + (0,))
    mark.putalpha(alpha)
    cropped = mark.crop(alpha.getbbox())
    side = max(cropped.size)
    square = Image.new("RGBA", (side, side), GOLD + (0,))
    square.alpha_composite(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2))
    square.resize((600, 600), Image.LANCZOS).save(STORE / "mark.png", optimize=True)
    source.convert("RGB").resize((512, 512), Image.LANCZOS).save(STORE / "icon-512.png", optimize=True)
    print("icons written")


if __name__ == "__main__":
    main()
