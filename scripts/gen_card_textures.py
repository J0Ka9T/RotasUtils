"""Draw the 16x16 item textures for the card system.

A card is a portrait card with a monster face and a gold nameplate; the socket punch is a chisel.
Both read at hotbar size and sit beside the refine materials drawn by gen_refine_textures.py.

Usage: python scripts/gen_card_textures.py
"""
import os

from PIL import Image

OUT = "common/src/main/resources/assets/rotasutils/textures/item"

CARD = [
    "................",
    "...eeeeeeeeee...",
    "...eFFFFFFFFe...",
    "...eFhhhhhhFe...",
    "...eFhhMMhhFe...",
    "...eFhMMMMhFe...",
    "...eFhMsMsMFe...",
    "...eFhMMMMhFe...",
    "...eFhhMMhhFe...",
    "...eFhhhhhhFe...",
    "...eFhSSSShFe...",
    "...eFhhhhhhFe...",
    "...eFFFFFFFFe...",
    "...eeeeeeeeee...",
    "................",
    "................",
]

PUNCH = [
    "................",
    "..........ee....",
    ".........eHHe...",
    "........eHHHe...",
    ".......eHHHe....",
    "......eHHHe.....",
    ".....eHHHe......",
    "....eHHHe.......",
    "...eHHHe........",
    "..eMMMe.........",
    ".eMMMe..........",
    ".eMMe...........",
    "..ee............",
    "................",
    "................",
    "................",
]

PALETTES = {
    "card": {"e": "2a1f33", "F": "6d4a8c", "h": "c9a3e6", "M": "f6edff", "s": "2a1f33", "S": "e7c451"},
    "socket_punch": {"e": "1d2430", "H": "cfe3f2", "M": "7d5526"},
}

SHAPES = {"card": CARD, "socket_punch": PUNCH}


def rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, shape in SHAPES.items():
        image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        pixels = image.load()
        palette = PALETTES[name]
        for y, row in enumerate(shape):
            for x, char in enumerate(row[:16]):
                colour = palette.get(char)
                if colour:
                    pixels[x, y] = rgb(colour)
        path = os.path.join(OUT, name + ".png")
        image.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
