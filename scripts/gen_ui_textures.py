"""Paints the smooth, rounded UI textures the forge and altar screens use (assets/.../textures/gui/anime).

Shapes are signed-distance fields with analytic anti-aliasing, drawn at native pixel size so nothing is
resampled: corners are round and soft instead of stepped rectangles. Panels, buttons and cards are
nine-slice pieces; rims, buttons and fills are white so the screen can tint them with any accent.
"""
import math
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources',
                   'assets', 'rotasutils', 'textures', 'gui', 'anime')


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x + 0.5, y + 0.5


def rrect(w, h, r, inset=0.0):
    """Signed distance (negative inside) to a rounded rectangle inset by `inset`."""
    x, y = grid(w, h)
    cx, cy = w / 2, h / 2
    hx, hy = w / 2 - inset - r, h / 2 - inset - r
    qx = np.abs(x - cx) - hx
    qy = np.abs(y - cy) - hy
    outside = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    inside = np.minimum(np.maximum(qx, qy), 0)
    return outside + inside - r


def cover(d):
    """Anti-aliased coverage from a signed distance."""
    return np.clip(0.5 - d, 0, 1)


def save(name, rgb, a):
    img = np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)])
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name))


def lerp(a, b, t):
    return a + (b - a) * t


def vgrad(h, top, bottom):
    t = (np.arange(h, dtype=np.float32) + 0.5) / h
    return np.array([lerp(top[i], bottom[i], t) for i in range(3)]).T[:, None, :]


def hexc(s):
    return np.array([int(s[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255


def panel_bg(w=48, h=48, r=11):
    d = rrect(w, h, r)
    a = cover(d)
    col = vgrad(h, hexc('#2b2560'), hexc('#150f30')) * np.ones((1, w, 1), np.float32)
    # a soft inner shadow at the edge and a diagonal sheen
    x, y = grid(w, h)
    edge = np.clip(1 + d / 6, 0, 1)
    col = col * (1 - 0.35 * edge[..., None])
    col += 0.05 * np.clip(1 - np.abs((x / w + y / h) - 0.5) * 2.4, 0, 1)[..., None]
    save('panel_bg.png', col, a)


def panel_rim(w=48, h=48, r=11):
    d = rrect(w, h, r)
    ring = cover(np.abs(d + 1.5) - 1.5)                # 3px ring just inside the edge
    x, y = grid(w, h)
    hi = cover(np.abs(rrect(w, h, r, 3.5) + 0.8) - 0.8) * np.clip(1 - y / (h * 0.5), 0, 1) * 0.6
    a = np.maximum(ring, hi)
    ink = cover(d - 1.5) * (1 - cover(d + 0.5))         # thin dark line just outside... kept inside coverage
    save('panel_rim.png', np.ones((h, w, 3), np.float32), a)


def button(w=48, h=32, r=10):
    d = rrect(w, h, r)
    a = cover(d)
    x, y = grid(w, h)
    t = y / h
    body = lerp(1.0, 0.68, np.clip(t, 0, 1))
    gloss = np.clip(1 - np.abs(t - 0.24) * 5, 0, 1) * 0.25 * (t < 0.5)
    col = np.clip(body + gloss, 0, 1)[..., None] * np.ones((1, 1, 3), np.float32)
    outline = cover(d + 2.2) < 0.999                      # ring within 2.2px of the edge
    ring = 1 - cover(d + 2.2)
    col = col * (1 - ring[..., None] * 0.92)              # dark outline baked in
    bottom = np.clip((t - 0.82) * 5, 0, 1)[..., None] * 0.18
    col = col - bottom
    save('button.png', col, a)


def trough(w=32, h=16, r=7):
    d = rrect(w, h, r)
    a = cover(d)
    ring = 1 - cover(d + 2.0)
    col = np.ones((h, w, 3), np.float32) * hexc('#0d0b20')
    col = col * (1 - ring[..., None]) + 0.02 * ring[..., None]
    save('trough.png', col, a)


def fill(w=32, h=16, r=7):
    d = rrect(w, h, r, 2.0)
    a = cover(d)
    x, y = grid(w, h)
    t = y / h
    body = lerp(1.0, 0.7, t)
    gloss = np.clip(1 - np.abs(t - 0.28) * 6, 0, 1) * 0.3
    save('fill.png', np.clip(body + gloss, 0, 1)[..., None] * np.ones((1, 1, 3), np.float32), a)


def card(w=48, h=48, r=9):
    d = rrect(w, h, r)
    a = cover(d)
    col = vgrad(h, hexc('#332c6a'), hexc('#1a1438')) * np.ones((1, w, 1), np.float32)
    ring = 1 - cover(d + 2.0)
    col = col * (1 - ring[..., None] * 0.9)
    save('card.png', col, a)
    rim = cover(np.abs(d + 3.0) - 1.2)
    save('card_rim.png', np.ones((h, w, 3), np.float32), rim)


def socket(n=64):
    """A hexagonal socket: dark well, and a separate white bevel ring to tint."""
    x, y = grid(n, n)
    dx, dy = x - n / 2, y - n / 2

    def hexd(radius):
        # distance to a pointy-top regular hexagon
        k = np.array([-0.866025404, 0.5, 0.577350269])
        px, py = np.abs(dx), np.abs(dy)
        dot = np.minimum(k[0] * px + k[1] * py, 0)
        px = px - 2 * dot * k[0]
        py = py - 2 * dot * k[1]
        px = px - np.clip(px, -k[2] * radius, k[2] * radius)
        py = py - radius
        return np.hypot(px, py) * np.sign(py)

    outer = hexd(n * 0.47)
    a = cover(outer)
    well = cover(hexd(n * 0.47) + 4.5)
    col = np.ones((n, n, 3), np.float32) * hexc('#0f0c26')
    col = col * (0.6 + 0.4 * np.clip(dy / n + 0.5, 0, 1)[..., None])
    save('socket_bg.png', col, a)
    ring = np.clip(a - cover(hexd(n * 0.47) + 4.5), 0, 1)
    shade = 0.75 + 0.25 * np.clip(-(dx + dy) / n + 0.5, 0, 1)
    save('socket_ring.png', shade[..., None] * np.ones((1, 1, 3), np.float32), ring)


def glow(n=128):
    x, y = grid(n, n)
    r = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    a = np.clip(1 - r, 0, 1) ** 2.2
    save('glow.png', np.ones((n, n, 3), np.float32), a)


def rays(n=256):
    x, y = grid(n, n)
    dx, dy = x - n / 2, y - n / 2
    r = np.hypot(dx, dy) / (n / 2)
    ang = np.arctan2(dy, dx)
    k = 12
    tri = np.abs(((ang * k / (2 * math.pi)) % 1.0) - 0.5) * 2         # 0 at ray centre, 1 between rays
    width = 0.34 + 0.4 * r                                            # rays widen outward
    ray = np.clip(1 - tri / width, 0, 1) ** 1.3
    fade = np.clip(1 - r, 0, 1) ** 1.2 * np.clip(r / 0.08, 0, 1)
    save('rays.png', np.ones((n, n, 3), np.float32), ray * fade * 0.9)


def spark(n=32):
    x, y = grid(n, n)
    dx, dy = np.abs(x - n / 2) / (n / 2), np.abs(y - n / 2) / (n / 2)
    a = np.maximum(np.clip(1 - dx * 6, 0, 1) * np.clip(1 - dy, 0, 1),
                   np.clip(1 - dy * 6, 0, 1) * np.clip(1 - dx, 0, 1))
    a = np.clip(a + np.clip(1 - np.hypot(dx, dy) * 4, 0, 1), 0, 1)
    save('spark.png', np.ones((n, n, 3), np.float32), a)


def ribbon(w=96, h=32):
    x, y = grid(w, h)
    slant = 10
    left = x - slant * (1 - y / h)
    right = (w - x) - slant * (y / h)
    inside = np.minimum(left, right)
    a = np.clip(inside + 0.5, 0, 1) * cover(np.abs(y - h / 2) - h / 2 + 0.5)
    t = y / h
    col = np.clip(lerp(1.0, 0.7, t) + np.clip(1 - np.abs(t - 0.25) * 6, 0, 1) * 0.2, 0, 1)
    edge = np.clip(1 - np.clip(inside / 2.2, 0, 1), 0, 1) * 0.9 + np.clip(1 - np.minimum(y, h - y) / 2.2, 0, 1) * 0.9
    col = np.clip(col * (1 - np.clip(edge, 0, 1)), 0, 1)
    save('ribbon.png', col[..., None] * np.ones((1, 1, 3), np.float32), a)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for f in (panel_bg, panel_rim, button, trough, fill, card, socket, glow, rays, spark, ribbon):
        f()
        print('painted', f.__name__)
