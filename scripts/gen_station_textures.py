"""Paints the Refine Forge and Rune Altar block textures (16x16 pixel art)."""
import math
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources',
                   'assets', 'rotasutils', 'textures', 'block')


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


class Tex:
    def __init__(self, base, seed, noise=6):
        rng = random.Random(seed)
        self.px = [[shade(base, rng.randint(-noise, noise)) for _ in range(16)] for _ in range(16)]
        self.rng = rng

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def bevel(self, dark, light, mid_dark, mid_light):
        """Outer 1px dark frame, inner 1px highlight top/left and shadow bottom/right."""
        for i in range(16):
            for c in (dark,):
                self.set(i, 0, c); self.set(i, 15, c); self.set(0, i, c); self.set(15, i, c)
        for i in range(1, 15):
            self.set(i, 1, light); self.set(1, i, light)
            self.set(i, 14, mid_dark); self.set(14, i, mid_dark)
        self.set(14, 1, mid_light); self.set(1, 14, mid_light)

    def save(self, name):
        img = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                img.putpixel((x, y), (*self.px[y][x], 255))
        img.save(os.path.join(OUT, name))


IRON = (62, 64, 72)
IRON_DARK = (24, 25, 30)
IRON_LIGHT = (104, 108, 120)
IRON_SHADOW = (38, 40, 46)
RIVET = (168, 170, 180)


def rivet(t, x, y):
    t.set(x, y, RIVET)
    t.set(x + 1, y, (120, 122, 132))
    t.set(x, y + 1, (30, 31, 36))
    t.set(x + 1, y + 1, (30, 31, 36))


def plate(seed):
    t = Tex(IRON, seed)
    t.bevel(IRON_DARK, IRON_LIGHT, IRON_SHADOW, (84, 87, 96))
    return t


def heat_stain(t, rows=(11, 12, 13)):
    """Soot and a dull orange glow bleeding up from the fire."""
    for y in rows:
        k = (y - rows[0] + 1) / len(rows)
        for x in range(2, 14):
            if t.rng.random() < 0.55 * k:
                t.set(x, y, mix(t.px[y][x], (120, 60, 28), 0.45 * k))


def forge_side():
    t = plate(11)
    for y in (2, 12):
        for x in (2, 12):
            rivet(t, x, y)
    for x in range(2, 14):
        t.set(x, 7, (30, 31, 36))
        t.set(x, 8, (86, 89, 99))
    heat_stain(t)
    t.save('refine_forge_side.png')


def forge_front():
    t = plate(23)
    for x in (2, 12):
        rivet(t, x, 2)
    # lintel and brick-dark surround
    t.rect(3, 3, 12, 3, (30, 31, 36))
    t.rect(2, 4, 13, 12, (16, 14, 16))
    # the fire: deep red at the top, orange, then yellow-white at the bottom
    rows = {5: (110, 24, 10), 6: (170, 44, 12), 7: (222, 84, 16), 8: (240, 130, 22),
            9: (250, 172, 40), 10: (255, 208, 84), 11: (255, 238, 170)}
    for y, c in rows.items():
        for x in range(4, 12):
            t.set(x, y, shade(c, t.rng.randint(-14, 10)))
    # flame tongues licking up
    for x in (5, 7, 10):
        t.set(x, 4, (200, 60, 14))
        t.set(x, 5, (240, 120, 20))
    for x in (6, 9):
        t.set(x, 4, (140, 34, 10))
    # coals along the bottom
    for x in range(4, 12):
        if t.rng.random() < 0.5:
            t.set(x, 12, (70, 20, 10))
        else:
            t.set(x, 12, (200, 60, 12))
    # grate bars across the mouth
    for x in (6, 9):
        for y in range(5, 12):
            t.set(x, y, (20, 18, 20))
    t.rect(3, 13, 12, 13, (34, 34, 40))
    # glow spilling onto the frame
    for x in range(3, 13):
        t.set(x, 4, mix(t.px[4][x], (200, 90, 20), 0.35))
    t.save('refine_forge_front.png')


def forge_top():
    t = plate(37)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(t, x, y)
    # recessed steel anvil pad
    t.rect(4, 4, 11, 11, (30, 31, 36))
    t.rect(5, 5, 10, 10, (118, 122, 134))
    for i in range(5, 11):
        t.set(i, 5, (150, 154, 166))
        t.set(5, i, (150, 154, 166))
        t.set(i, 10, (84, 87, 97))
        t.set(10, i, (84, 87, 97))
    # hammer strikes, hot
    for x, y, c in ((7, 7, (255, 170, 40)), (8, 7, (255, 210, 90)), (8, 8, (240, 110, 20)), (6, 8, (200, 70, 14))):
        t.set(x, y, c)
    t.set(9, 6, (255, 230, 150))
    t.save('refine_forge_top.png')


def forge_bottom():
    t = plate(41)
    t.save('refine_forge_bottom.png')


OBS = (24, 19, 36)
GOLD = (214, 176, 66)
GOLD_LIGHT = (250, 226, 130)
GOLD_DARK = (128, 92, 34)
VIOLET = (150, 84, 232)
VIOLET_LIGHT = (214, 172, 255)


def obsidian(seed):
    t = Tex(OBS, seed, 5)
    # faint violet speckle, like crying obsidian
    for _ in range(9):
        x, y = t.rng.randint(0, 15), t.rng.randint(0, 15)
        t.set(x, y, mix(t.px[y][x], VIOLET, 0.28))
    return t


def band(t, y0, y1):
    """A gold trim band across rows y0..y1."""
    for x in range(16):
        for y in range(y0, y1 + 1):
            t.set(x, y, GOLD_DARK if y == y1 else (GOLD if y != y0 else GOLD_LIGHT))
    for x in range(0, 16, 4):
        t.set(x, (y0 + y1) // 2, GOLD_LIGHT)


GLYPH = ["..#..",
         ".###.",
         "#.#.#",
         "..#..",
         ".###.",
         "#...#",
         "#...#"]


def altar_side():
    t = obsidian(53)
    band(t, 0, 2)
    band(t, 13, 15)
    # inlaid glyph, glowing
    for gy, row in enumerate(GLYPH):
        for gx, ch in enumerate(row):
            if ch == '#':
                t.set(5 + gx + 1, 4 + gy, VIOLET)
    t.set(8, 4, VIOLET_LIGHT)
    t.set(8, 7, VIOLET_LIGHT)
    # halo around the glyph
    for gy in range(-1, 8):
        for gx in range(-1, 6):
            x, y = 6 + gx, 4 + gy
            if t.px[y][x][2] < 100:
                t.set(x, y, mix(t.px[y][x], VIOLET, 0.12))
    # vertical edge highlights
    for y in range(3, 13):
        t.set(0, y, shade(t.px[y][0], 16))
        t.set(15, y, shade(t.px[y][15], -8))
    t.save('rune_altar_side.png')


def altar_top():
    t = obsidian(67)
    cx = cy = 7.5
    # ring, brightest at the eight nodes
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if 5.2 <= d <= 6.2:
                t.set(x, y, mix(VIOLET, (90, 46, 160), 0.4 if (x + y) % 2 else 0))
            elif 3.4 <= d <= 3.9:
                t.set(x, y, (100, 54, 176))
    for k in range(8):
        a = math.pi * 2 * k / 8
        x, y = int(round(cx + math.cos(a) * 5.7)), int(round(cy + math.sin(a) * 5.7))
        t.set(x, y, VIOLET_LIGHT)
    # diamond core
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        t.set(7 + dx, 7 + dy, (240, 226, 255))
    for x, y in ((6, 7), (6, 8), (9, 7), (9, 8), (7, 6), (8, 6), (7, 9), (8, 9)):
        t.set(x, y, VIOLET_LIGHT)
    # gold frame
    for i in range(16):
        for c, pos in ((GOLD_DARK, 0), (GOLD, 1)):
            pass
        t.set(i, 0, GOLD_DARK); t.set(0, i, GOLD_DARK); t.set(i, 15, GOLD_DARK); t.set(15, i, GOLD_DARK)
    for i in range(1, 15):
        t.set(i, 1, GOLD); t.set(1, i, GOLD)
    t.set(1, 1, GOLD_LIGHT)
    t.save('rune_altar_top.png')


def altar_bottom():
    t = obsidian(71)
    t.bevel((12, 9, 20), (44, 34, 64), (18, 14, 28), (34, 26, 52))
    t.save('rune_altar_bottom.png')


def altar_crystal():
    t = Tex(VIOLET, 79, 4)
    for y in range(16):
        for x in range(16):
            # facets: diagonal light/dark bands
            k = ((x + y) // 3) % 3
            t.set(x, y, [VIOLET_LIGHT, VIOLET, (96, 50, 176)][k] if (x + y) % 5 else VIOLET)
    t.rect(0, 0, 15, 0, (70, 36, 130))
    t.rect(0, 15, 15, 15, (70, 36, 130))
    t.rect(0, 0, 0, 15, (70, 36, 130))
    t.rect(15, 0, 15, 15, (70, 36, 130))
    t.save('rune_altar_crystal.png')


if __name__ == '__main__':
    for f in (forge_side, forge_front, forge_top, forge_bottom, altar_side, altar_top, altar_bottom, altar_crystal):
        f()
        print('painted', f.__name__)
