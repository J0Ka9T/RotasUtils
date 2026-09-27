from __future__ import annotations

import colorsys
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

FILES = (
    "gui_controls.png",
    "icons.png",
    "slots_background.png",
    "storage_background_12.png",
    "storage_background_12_wider.png",
    "storage_background_9.png",
    "storage_background_9_wider.png",
)

BASE = (0x22, 0x1A, 0x12)
SURFACE = (0x2C, 0x22, 0x18)
HIGH = (0x38, 0x2C, 0x1F)
BORDER = (0x6B, 0x53, 0x34)
TRACK = (0x1A, 0x14, 0x0E)
ACCENT = (0xC9, 0x8B, 0x3D)
ACCENT_STRONG = (0xE3, 0xA8, 0x57)
ACCENT_WASH = (0x4C, 0x3A, 0x20)
TEXT = (0xED, 0xDF, 0xC0)
MUTED = (0xB6, 0xA1, 0x7C)

BACKGROUND_FILES = {
    "slots_background.png",
    "storage_background_12.png",
    "storage_background_12_wider.png",
    "storage_background_9.png",
    "storage_background_9_wider.png",
}


def luminance(rgb):
    r, g, b = (c / 255.0 for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def saturation(rgb):
    r, g, b = (c / 255.0 for c in rgb)
    return colorsys.rgb_to_hsv(r, g, b)[1]


def map_neutral(rgb):
    value = luminance(rgb)
    if value < 0.18:
        return TRACK
    if value < 0.34:
        return BASE
    if value < 0.50:
        return SURFACE
    if value < 0.66:
        return HIGH
    if value < 0.82:
        return BORDER
    return MUTED


def map_warm(rgb):
    value = luminance(rgb)
    if value < 0.35:
        return ACCENT_WASH
    if value < 0.72:
        return ACCENT
    return ACCENT_STRONG


def map_icon(rgb):
    sat = saturation(rgb)
    if sat >= 0.28:
        return rgb
    value = luminance(rgb)
    if value < 0.45:
        return TEXT
    return MUTED


def map_pixel(filename, rgba):
    r, g, b, a = rgba
    if a == 0:
        return rgba

    rgb = (r, g, b)
    sat = saturation(rgb)

    if filename == "icons.png":
        nr, ng, nb = map_icon(rgb)
        return nr, ng, nb, a

    if filename == "gui_controls.png":
        if r > g and g > b and r - b > 18:
            nr, ng, nb = map_warm(rgb)
            return nr, ng, nb, a
        if sat >= 0.36:
            return rgba
        nr, ng, nb = map_neutral(rgb)
        return nr, ng, nb, a

    if filename in BACKGROUND_FILES:
        if sat >= 0.42:
            return rgba
        nr, ng, nb = map_neutral(rgb)
        return nr, ng, nb, a

    return rgba


def theme(image, filename):
    src = image.convert("RGBA")
    out = Image.new("RGBA", src.size)
    out.putdata([map_pixel(filename, pixel) for pixel in src.get_flattened_data()])
    return out


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: theme_sophisticated_core.py <sophisticated-core-jar> <resources-root>")

    jar = Path(sys.argv[1])
    root = Path(sys.argv[2])
    output = root / "assets" / "sophisticatedcore" / "textures" / "gui"
    output.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(jar) as archive:
        for filename in FILES:
            member = f"assets/sophisticatedcore/textures/gui/{filename}"
            source = Image.open(io.BytesIO(archive.read(member)))
            if source.size != (256, 256):
                raise RuntimeError(f"{member} changed size: {source.size}")
            themed = theme(source, filename)
            themed.save(output / filename, format="PNG", optimize=False)


if __name__ == "__main__":
    main()
