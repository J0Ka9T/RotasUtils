"""Draw the 16x16 item textures for the refinement materials.

Two shapes carry all seven items: a cut crystal for the refine ores and a rolled scroll for the three
scrolls. Only the palette changes between them, so the set reads as one family in a hotbar, and an
enriched ore is the plain ore plus its spark highlights.

Usage: python scripts/gen_refine_textures.py
"""
import os

from PIL import Image

OUT = "common/src/main/resources/assets/rotasutils/textures/item"

CRYSTAL = [
    "................",
    ".......ee.......",
    "......eHHe......",
    ".....eHLLHe.....",
    "....eHLLLLHe....",
    "...eHLLLLMLHe...",
    "..eHLLLMMMLLHe..",
    "..eHLLMMMMMLLHe.",
    "..eHLMMMMMMMLHe.",
    "...eHMMMMMMMHe..",
    "...eHMMMMMMHe...",
    "....eHMMMMHe....",
    ".....eHMMHe.....",
    "......eHHe......",
    ".......ee.......",
    "................",
]

SPARK = [
    "................",
    "................",
    "..s.............",
    ".sss...........s",
    "..s...........s.",
    "...............s",
    "................",
    "................",
    "................",
    "................",
    "..............s.",
    ".............sss",
    "..............s.",
    "................",
    "................",
    "................",
]

SCROLL = [
    "................",
    "..rrrrrrrrrr....",
    ".rRRRRRRRRRRr...",
    ".rRRRRRRRRRRr...",
    "..ePPPPPPPPe....",
    "..ePSSSSPPPe....",
    "..ePPPPPPPPe....",
    "..ePSSSSSSPe....",
    "..ePPPPPPPPe....",
    "..ePSSSSPPPe....",
    "..ePPPPPPPPe....",
    ".rRRRRRRRRRRr...",
    ".rRRRRRRRRRRr...",
    "..rrrrrrrrrr....",
    "................",
    "................",
]

PALETTES = {
    "oridecon": {"e": "1d2430", "H": "9fc7e8", "L": "6d9fd0", "M": "3f6aa0"},
    "elunium": {"e": "1b2a1e", "H": "bde8a8", "L": "7fc46a", "M": "48853c"},
    "enriched_oridecon": {"e": "1d2430", "H": "d8f0ff", "L": "8fc6f4", "M": "4f86c8", "s": "ffffff"},
    "enriched_elunium": {"e": "1b2a1e", "H": "e2ffd4", "L": "9ee384", "M": "58a349", "s": "ffffff"},
    "protection_scroll": {"e": "6b4a24", "R": "c79350", "r": "7d5526", "P": "f2e5c4", "S": "8a6130"},
    "blessing_scroll": {"e": "3f5c33", "R": "7db059", "r": "4d6f38", "P": "f0f6de", "S": "4d6f38"},
    "certificate_scroll": {"e": "5a3f74", "R": "a97fd0", "r": "6d4a8c", "P": "f6edff", "S": "6d4a8c"},
}


def rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def draw(name, shape, palette, overlay=None):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    layers = [shape] + ([overlay] if overlay else [])
    for layer in layers:
        for y, row in enumerate(layer):
            for x, char in enumerate(row[:16]):
                colour = palette.get(char)
                if colour:
                    pixels[x, y] = rgb(colour)
    path = os.path.join(OUT, name + ".png")
    image.save(path)
    print("wrote", path)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, palette in PALETTES.items():
        if name.endswith("scroll"):
            draw(name, SCROLL, palette)
        elif name.startswith("enriched"):
            draw(name, CRYSTAL, palette, SPARK)
        else:
            draw(name, CRYSTAL, palette)


if __name__ == "__main__":
    main()
