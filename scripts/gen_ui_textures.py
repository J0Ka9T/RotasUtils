"""Paints the fantasy UI textures the forge and altar screens use (assets/.../textures/gui/anime).

A dark stone slab framed in worked gold with corner studs, iron button plates with gold trim, bronze-
framed gauges, gold-ringed rune sockets. Shapes are signed-distance fields with analytic anti-aliasing,
drawn at native size so corners are round and nothing is resampled. Bodies and inlays are white/grey so
the screen tints them per use; frames and trims are baked gold.
"""
import math
import os

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

OUT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources',
                   'assets', 'rotasutils', 'textures', 'gui', 'anime')


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x + 0.5, y + 0.5


def rrect(w, h, r, inset=0.0):
    x, y = grid(w, h)
    hx, hy = w / 2 - inset - r, h / 2 - inset - r
    qx = np.abs(x - w / 2) - hx
    qy = np.abs(y - h / 2) - hy
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def cover(d):
    return np.clip(0.5 - d, 0, 1)


def hexc(s):
    return np.array([int(s[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255


def save(name, rgb, a):
    img = np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)])
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name))


def noise(w, h, scale, seed):
    rng = np.random.default_rng(seed)
    a = ndi.gaussian_filter(rng.random((h, w)).astype(np.float32), scale, mode='wrap')
    return (a - a.mean()) / (a.std() + 1e-6)


def vgrad(h, top, bottom):
    t = ((np.arange(h, dtype=np.float32) + 0.5) / h)[:, None, None]
    return top[None, None, :] * (1 - t) + bottom[None, None, :] * t


def gold_ramp(t):
    """t 0..1 dark to bright gold, as rgb."""
    stops = [(0.0, '#2b1a08'), (0.35, '#7a4f16'), (0.62, '#c8912e'), (0.85, '#efc766'), (1.0, '#fff1bd')]
    ps = [s[0] for s in stops]
    cs = np.array([hexc(s[1]) for s in stops])
    return np.stack([np.interp(t, ps, cs[:, k]) for k in range(3)], -1)


def stone(w, h, top, bottom, seed, amount=0.05):
    col = vgrad(h, hexc(top), hexc(bottom)) * np.ones((1, w, 1), np.float32)
    n = noise(w, h, 6, seed) * 0.6 + noise(w, h, 1.6, seed + 1) * 0.4
    return col + n[..., None] * amount


def panel_bg(w=64, h=64, r=8):
    d = rrect(w, h, r)
    col = stone(w, h, '#241f30', '#120e19', 3, 0.045)
    x, y = grid(w, h)
    vig = np.clip(1 + d / 14, 0, 1)
    col = col * (1 - 0.4 * vig[..., None])
    save('panel_bg.png', col, cover(d))


def panel_frame(w=64, h=64, r=8):
    """Worked gold border, lit from the top left, with a stud in each corner and a mid-edge stud."""
    d = rrect(w, h, r)
    x, y = grid(w, h)
    band = cover(np.abs(d + 2.2) - 2.2)                        # 4.4px band inside the edge
    depth = np.clip(-d / 4.4, 0, 1)                            # 0 at outer edge, 1 at inner edge
    lit = np.clip(0.5 - ((x - w / 2) + (y - h / 2)) / (w + h) * 1.4, 0, 1)
    t = np.clip(0.25 + 0.45 * np.sin(depth * math.pi) + 0.3 * (lit - 0.5), 0, 1)
    col = gold_ramp(t)
    a = band
    # inner hairline, darker
    hair = cover(np.abs(d + 5.6) - 0.6)
    col = col * (1 - hair[..., None]) + gold_ramp(np.full_like(x, 0.2))[..., :] * hair[..., None]
    a = np.maximum(a, hair * 0.9)
    # studs
    for cx, cy in ((7.5, 7.5), (w - 7.5, 7.5), (7.5, h - 7.5), (w - 7.5, h - 7.5)):
        dd = np.hypot(x - cx, y - cy)
        stud = cover(dd - 3.2)
        shade = np.clip(0.55 + (-(x - cx) - (y - cy)) / 8, 0, 1)
        col = col * (1 - stud[..., None]) + gold_ramp(shade)[..., :] * stud[..., None]
        ring = cover(np.abs(dd - 3.4) - 0.5)
        col = col * (1 - ring[..., None]) + hexc('#1a0e04') * ring[..., None]
        a = np.maximum(a, stud)
    save('panel_frame.png', col, a)


def inlay(w=64, h=64, r=8):
    d = rrect(w, h, r, 7.0)
    ring = cover(np.abs(d) - 0.55)
    save('panel_inlay.png', np.ones((h, w, 3), np.float32), ring * 0.9)


def button_body(w=48, h=32, r=6):
    d = rrect(w, h, r)
    x, y = grid(w, h)
    t = y / h
    body = 1.0 - 0.5 * t
    brushed = noise(w, h, 0.01, 5) * 0
    n = noise(w, h, 1.0, 9)
    grain = (ndi.gaussian_filter(np.random.default_rng(11).random((h, w)).astype(np.float32), (0.4, 5)) - 0.5) * 0.5
    col = np.clip(body + grain * 0.35 + 0.0 * n + np.clip(1 - np.abs(t - 0.12) * 12, 0, 1) * 0.18, 0, 1)
    save('button_body.png', col[..., None] * np.ones((1, 1, 3), np.float32), cover(d))


def button_trim(w=48, h=32, r=6):
    d = rrect(w, h, r)
    x, y = grid(w, h)
    band = cover(np.abs(d + 1.4) - 1.4)
    lit = np.clip(0.5 - ((x - w / 2) + (y - h / 2)) / (w + h) * 1.6, 0, 1)
    col = gold_ramp(np.clip(0.3 + 0.55 * lit, 0, 1))
    outer = cover(d + 0.5) * (1 - cover(d + 1.0))
    save('button_trim.png', col, band)


def trough(w=32, h=16, r=4):
    d = rrect(w, h, r)
    x, y = grid(w, h)
    inner = cover(d + 2.0)
    col = vgrad(h, hexc('#07050b'), hexc('#171222')) * np.ones((1, w, 1), np.float32)
    lit = np.clip(0.5 - ((x - w / 2) + (y - h / 2)) / (w + h) * 1.4, 0, 1)
    frame = gold_ramp(np.clip(0.28 + 0.5 * lit, 0, 1))
    m = inner[..., None]
    out = frame * (1 - m) + col * m
    save('trough.png', out, cover(d))


def fill(w=32, h=16, r=4):
    d = rrect(w, h, r, 2.0)
    x, y = grid(w, h)
    t = y / h
    body = 0.55 + 0.45 * np.clip(1 - np.abs(t - 0.3) * 1.9, 0, 1)
    save('fill.png', np.clip(body, 0, 1)[..., None] * np.ones((1, 1, 3), np.float32), cover(d))


def card(w=48, h=48, r=6):
    d = rrect(w, h, r)
    col = stone(w, h, '#2b2538', '#171220', 31, 0.04)
    ring = 1 - cover(d + 1.4)
    edge = gold_ramp(np.full((h, w), 0.32, np.float32))
    col = col * (1 - ring[..., None]) + edge * ring[..., None]
    save('card.png', col, cover(d))
    save('card_rim.png', np.ones((h, w, 3), np.float32), cover(np.abs(d + 3.0) - 0.7) * 0.9)


def socket(n=64):
    x, y = grid(n, n)
    dx, dy = x - n / 2, y - n / 2
    r = np.hypot(dx, dy)
    outer = n * 0.47
    a = cover(r - outer)
    well = np.clip(1 - r / outer, 0, 1)
    col = (hexc('#0b0813') * (0.55 + 0.45 * well[..., None] ** 0.6))
    col = col + noise(n, n, 1.4, 41)[..., None] * 0.012
    save('socket_bg.png', col, a)
    ring = cover(np.abs(r - (outer - 2.6)) - 2.6)
    lit = np.clip(0.5 - (dx + dy) / (n * 1.1), 0, 1)
    depth = np.clip(1 - np.abs(r - (outer - 2.6)) / 2.6, 0, 1)
    t = np.clip(0.28 + 0.5 * lit + 0.25 * depth, 0, 1)
    save('socket_ring.png', gold_ramp(t), ring)
    save('socket_inlay.png', np.ones((n, n, 3), np.float32), cover(np.abs(r - (outer - 6.2)) - 0.6) * 0.9)


def glow(n=128):
    x, y = grid(n, n)
    r = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    save('glow.png', np.ones((n, n, 3), np.float32), np.clip(1 - r, 0, 1) ** 2.2)


def rays(n=256):
    """Faint thin shafts of light, sixteen of them, fading out."""
    x, y = grid(n, n)
    dx, dy = x - n / 2, y - n / 2
    r = np.hypot(dx, dy) / (n / 2)
    ang = np.arctan2(dy, dx)
    k = 16
    tri = np.abs(((ang * k / (2 * math.pi)) % 1.0) - 0.5) * 2
    ray = np.clip(1 - tri / (0.16 + 0.2 * r), 0, 1) ** 1.6
    fade = np.clip(1 - r, 0, 1) ** 1.4 * np.clip(r / 0.1, 0, 1)
    save('rays.png', np.ones((n, n, 3), np.float32), ray * fade * 0.7)


def spark(n=32):
    x, y = grid(n, n)
    dx, dy = np.abs(x - n / 2) / (n / 2), np.abs(y - n / 2) / (n / 2)
    a = np.maximum(np.clip(1 - dx * 6, 0, 1) * np.clip(1 - dy, 0, 1), np.clip(1 - dy * 6, 0, 1) * np.clip(1 - dx, 0, 1))
    save('spark.png', np.ones((n, n, 3), np.float32), np.clip(a + np.clip(1 - np.hypot(dx, dy) * 4, 0, 1), 0, 1))


def ribbon_body(w=96, h=32):
    """A banner with swallow-tail ends; body tinted by the screen."""
    x, y = grid(w, h)
    notch = 9 * (1 - np.abs(y - h / 2) / (h / 2))
    inside = np.minimum(x - (9 - notch), (w - x) - (9 - notch))
    a = np.clip(inside + 0.5, 0, 1) * cover(np.abs(y - h / 2) - h / 2 + 3)
    t = y / h
    col = 0.75 - 0.4 * t + noise(w, h, 1.2, 51) * 0.03
    save('ribbon.png', np.clip(col, 0, 1)[..., None] * np.ones((1, 1, 3), np.float32), a)
    trim = np.clip(np.maximum(cover(np.abs(y - 3.5) - 1.0), cover(np.abs(y - (h - 3.5)) - 1.0)), 0, 1) * np.clip(inside + 0.5, 0, 1)
    lit = np.clip(0.55 + 0.3 * np.sin(x / 6), 0, 1)
    save('ribbon_trim.png', gold_ramp(lit * 0.8), trim)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for f in (panel_bg, panel_frame, inlay, button_body, button_trim, trough, fill, card, socket, glow, rays, spark, ribbon_body):
        f()
        print('painted', f.__name__)
    for stale in ('panel_rim.png', 'button.png'):
        p = os.path.join(OUT, stale)
        if os.path.exists(p):
            os.remove(p)
