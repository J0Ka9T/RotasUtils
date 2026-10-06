"""Bakes the recoloured copies of the eldritch sky textures (red, gold, void, rainbow).

The formulas match EldritchSkyPalette, which remaps vertex colours the same way at runtime, so a
textured layer and a coloured layer of the same sky always agree. Run from the project root after
changing any texture in textures/environment/:

    python scripts/sky_palettes.py
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources",
                    "assets", "rotasutils", "textures", "environment")


def red(r, g, b, v, w):
    return np.maximum(r, b), 0.40 * g + 0.30 * w, 0.25 * r + 0.20 * w


def gold(r, g, b, v, w):
    return v, 0.62 * v + 0.33 * g + 0.05 * w, 0.15 * v + 0.55 * w + 0.25 * g * g


def void(r, g, b, v, w):
    return 0.55 * (0.30 * v + 0.55 * w), 0.55 * (0.34 * v + 0.55 * w + 0.32 * g), 0.55 * (0.30 * v + 0.52 * w + 0.10 * g)


def rainbow(r, g, b, v, w):
    """Hue removed, brightness kept: the rainbow sky carries all of its colour in the vertex colour.

    Vertex colours multiply the texture, so a blue texture can only ever tint toward blue. Neutral
    copies let EldritchSkyPalette paint any hue on them, and cycle it while the sky is open.
    """
    return v, v, v


PALETTES = {"red": red, "gold": gold, "void": void, "rainbow": rainbow}

ENTITY = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources",
                      "assets", "rotasutils", "textures", "entity")
PRISM_RIFT = {"rift_portal_violet.png": "rift_portal_prism.png",
              "rift_portal_glow_violet.png": "rift_portal_glow_prism.png"}


def neutralise(source, target):
    px = np.asarray(Image.open(source).convert("RGBA")).astype(np.float32) / 255.0
    v = np.maximum(px[..., 0], np.maximum(px[..., 1], px[..., 2]))
    out = np.stack([v, v, v, px[..., 3]], axis=-1)
    Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(target)


def main():
    names = sorted(f for f in os.listdir(ROOT) if f.endswith(".png"))
    for folder, remap in PALETTES.items():
        out_dir = os.path.join(ROOT, folder)
        os.makedirs(out_dir, exist_ok=True)
        for name in names:
            px = np.asarray(Image.open(os.path.join(ROOT, name)).convert("RGBA")).astype(np.float32) / 255.0
            r, g, b, a = px[..., 0], px[..., 1], px[..., 2], px[..., 3]
            v = np.maximum(r, np.maximum(g, b))
            w = np.minimum(r, np.minimum(g, b))
            nr, ng, nb = remap(r, g, b, v, w)
            out = np.stack([nr, ng, nb, a], axis=-1)
            Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(out_dir, name))
        print(folder, len(names))
    for source, target in PRISM_RIFT.items():
        neutralise(os.path.join(ENTITY, source), os.path.join(ENTITY, target))
    print("prism rift", len(PRISM_RIFT))


if __name__ == "__main__":
    main()
