"""Paints the Sigil of Sundering sky textures (additive: rgb = colour, alpha = intensity)."""
import math
import os
import random

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources',
                   'assets', 'rotasutils', 'textures', 'environment', 'sunder')
GOLD = np.array([1.0, 0.70, 0.24])
WHITE = np.array([1.0, 0.97, 0.88])
ICE = np.array([0.78, 0.90, 1.0])
VIOLET = np.array([0.62, 0.42, 1.0])


class Canvas:
    def __init__(self, n):
        self.n = n
        ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
        self.x = (xs + 0.5) / n * 2 - 1
        self.y = (ys + 0.5) / n * 2 - 1
        self.core = np.zeros((n, n), np.float32)
        self.glow = np.zeros((n, n), np.float32)

    def seg(self, a, b, w, strength=1.0, glow=4.0, gw=0.35):
        (ax, ay), (bx, by) = a, b
        pad = w * glow * 3
        n = self.n
        x0 = max(0, int((min(ax, bx) - pad + 1) / 2 * n))
        x1 = min(n, int((max(ax, bx) + pad + 1) / 2 * n) + 1)
        y0 = max(0, int((min(ay, by) - pad + 1) / 2 * n))
        y1 = min(n, int((max(ay, by) + pad + 1) / 2 * n) + 1)
        if x0 >= x1 or y0 >= y1:
            return
        X = self.x[y0:y1, x0:x1]
        Y = self.y[y0:y1, x0:x1]
        dx, dy = bx - ax, by - ay
        L2 = dx * dx + dy * dy + 1e-12
        t = np.clip(((X - ax) * dx + (Y - ay) * dy) / L2, 0, 1)
        d = np.hypot(X - ax - t * dx, Y - ay - t * dy)
        self.core[y0:y1, x0:x1] = np.maximum(self.core[y0:y1, x0:x1], strength * np.exp(-(d / w) ** 2))
        self.glow[y0:y1, x0:x1] += strength * gw * np.exp(-(d / (w * glow)) ** 2)

    def ring(self, r, w, strength=1.0, glow=4.0, gw=0.35):
        d = np.abs(np.hypot(self.x, self.y) - r)
        self.core = np.maximum(self.core, strength * np.exp(-(d / w) ** 2))
        self.glow += strength * gw * np.exp(-(d / (w * glow)) ** 2)

    def circle(self, cx, cy, r, w, strength=1.0):
        steps = 28
        for i in range(steps):
            a0, a1 = 2 * math.pi * i / steps, 2 * math.pi * (i + 1) / steps
            self.seg((cx + r * math.cos(a0), cy + r * math.sin(a0)),
                     (cx + r * math.cos(a1), cy + r * math.sin(a1)), w, strength)

    def save(self, name, core_col=WHITE, glow_col=GOLD, fade_edge=True):
        c = np.clip(self.core, 0, 1)[..., None]
        g = np.clip(self.glow, 0, 1.5)[..., None]
        a = np.clip(c + g, 0, 1)
        rgb = (core_col * c + glow_col * g) / np.maximum(c + g, 1e-6)
        if fade_edge:
            r = np.hypot(self.x, self.y)[..., None]
            a = a * np.clip((1.0 - r) / 0.02, 0, 1)
        img = np.concatenate([rgb, a], -1)
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name))


def polar(r, a):
    return (r * math.cos(a), r * math.sin(a))


def glyph(cv, cx, cy, ang, size, rng, w):
    """A made-up rune: a spine plus 1-3 strokes on a 3x3 lattice, upright towards the centre."""
    pts = [(i - 1, j - 1) for i in range(3) for j in range(3)]
    ca, sa = math.cos(ang), math.sin(ang)

    def place(p):
        px, py = p[0] * size * 0.55, p[1] * size
        return (cx + px * -sa + py * ca, cy + px * ca + py * sa)

    cv.seg(place((0, -1)), place((0, 1)), w)
    for _ in range(rng.randint(1, 3)):
        a, b = rng.sample(pts, 2)
        cv.seg(place(a), place(b), w * 0.85)
    if rng.random() < 0.4:
        cx2, cy2 = place((0, 0))
        cv.circle(cx2, cy2, size * 0.18, w * 0.7, 0.9)


def seal():
    rng = random.Random(7)
    cv = Canvas(1024)
    W = 0.0035
    for r, w in [(0.965, W * 1.3), (0.935, W * 0.8), (0.80, W * 1.1), (0.64, W), (0.615, W * 0.7), (0.31, W)]:
        cv.ring(r, w)
    for i in range(144):
        a = 2 * math.pi * i / 144
        cv.seg(polar(0.935, a), polar(0.935 + (0.03 if i % 6 == 0 else 0.014), a), W * 0.6, 0.8, gw=0.2)
    for i in range(48):
        a = 2 * math.pi * (i + 0.5) / 48
        x, y = polar(0.868, a)
        glyph(cv, x, y, a, 0.030, rng, W * 0.75)
    for i in range(8):
        a0, a1 = 2 * math.pi * i / 8, 2 * math.pi * (i + 3) / 8
        cv.seg(polar(0.80, a0), polar(0.80, a1), W)
        x, y = polar(0.80, a0)
        cv.circle(x, y, 0.045, W * 0.8)
        cv.circle(x, y, 0.022, W * 0.6, 0.8)
    for k in range(2):
        for i in range(3):
            a0 = 2 * math.pi * i / 3 + k * math.pi / 3 - math.pi / 2
            cv.seg(polar(0.31, a0), polar(0.31, a0 + 2 * math.pi / 3), W)
    for i in range(16):
        a = 2 * math.pi * i / 16
        cv.seg(polar(0.33, a), polar(0.60, a), W * (0.9 if i % 2 == 0 else 0.5), 0.7 if i % 2 else 1.0)
    for i in range(24):
        a = 2 * math.pi * (i + 0.5) / 24
        x, y = polar(0.47, a)
        glyph(cv, x, y, a, 0.020, rng, W * 0.6)
    cv.circle(0, 0, 0.06, W)
    cv.save('seal.png')


def rune_ring():
    rng = random.Random(21)
    cv = Canvas(1024)
    W = 0.003
    cv.ring(0.975, W * 1.2)
    cv.ring(0.845, W * 1.2)
    cv.ring(0.83, W * 0.6, 0.7)
    for i in range(64):
        a = 2 * math.pi * (i + 0.5) / 64
        x, y = polar(0.91, a)
        glyph(cv, x, y, a, 0.036, rng, W * 0.9)
    for i in range(16):
        a = 2 * math.pi * i / 16
        cv.seg(polar(0.975, a), polar(0.999, a), W, 0.9)
        cv.seg(polar(0.845, a - 0.02), polar(0.80, a), W * 0.8)
        cv.seg(polar(0.845, a + 0.02), polar(0.80, a), W * 0.8)
    cv.save('rune_ring.png')


def cracks():
    rng = random.Random(3)
    cv = Canvas(1024)

    def branch(x, y, ang, length, w, depth):
        steps = int(6 + length * 22)
        step = length / steps
        for s in range(steps):
            ang += rng.uniform(-0.45, 0.45)
            nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
            taper = 1 - s / steps * 0.7
            cv.seg((x, y), (nx, ny), w * taper, 1.0, glow=5.0, gw=0.45)
            if depth < 3 and rng.random() < 0.13:
                branch(nx, ny, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.1),
                       length * rng.uniform(0.3, 0.5), w * 0.6, depth + 1)
            x, y = nx, ny
            if math.hypot(x, y) > 0.95:
                return

    for i in range(13):
        a = 2 * math.pi * i / 13 + rng.uniform(-0.2, 0.2)
        branch(0, 0, a, rng.uniform(0.65, 0.92), 0.006, 0)
    cv.save('cracks.png', WHITE, ICE * 0.55 + VIOLET * 0.45)


def flare():
    cv = Canvas(512)
    x, y = cv.x, cv.y
    r = np.hypot(x, y)
    core = np.exp(-(r / 0.05) ** 2)
    spikes = (np.exp(-np.abs(y) / 0.006) * np.exp(-np.abs(x) / 0.35)
              + np.exp(-np.abs(x) / 0.006) * np.exp(-np.abs(y) / 0.35))
    u, v = (x + y) / math.sqrt(2), (x - y) / math.sqrt(2)
    diag = 0.5 * (np.exp(-np.abs(v) / 0.004) * np.exp(-np.abs(u) / 0.14)
                  + np.exp(-np.abs(u) / 0.004) * np.exp(-np.abs(v) / 0.14))
    cv.core = np.clip(core + spikes * 0.9 + diag, 0, 1)
    cv.glow = 0.5 * np.exp(-(r / 0.22) ** 2) + 0.12 * np.exp(-((r - 0.55) / 0.02) ** 2)
    cv.save('flare.png')


def glow():
    cv = Canvas(256)
    r = np.hypot(cv.x, cv.y)
    cv.glow = np.exp(-(r / 0.42) ** 2) * (1 - np.clip(r, 0, 1)) ** 1.5
    cv.save('glow.png', WHITE, WHITE)


def rays():
    rng = np.random.default_rng(5)
    cv = Canvas(1024)
    r = np.hypot(cv.x, cv.y)
    ang = np.arctan2(cv.y, cv.x)
    field = np.zeros_like(r)
    for _ in range(56):
        a0 = rng.uniform(-math.pi, math.pi)
        width = rng.uniform(0.004, 0.03)
        reach = rng.uniform(0.45, 1.0)
        d = np.angle(np.exp(1j * (ang - a0)))
        field += rng.uniform(0.35, 1.0) * np.exp(-(d / width) ** 2) * np.exp(-(r / reach) ** 2 * 2.5)
    field *= np.clip(r / 0.05, 0, 1)
    cv.core = np.clip(field * 0.6, 0, 1) * np.exp(-(r / 0.5) ** 2)
    cv.glow = np.clip(field * 0.6, 0, 1.2)
    cv.save('rays.png')


def shock():
    rng = np.random.default_rng(9)
    cv = Canvas(512)
    r = np.hypot(cv.x, cv.y)
    ang = np.arctan2(cv.y, cv.x)
    wob = sum(rng.uniform(0.2, 0.5) * np.sin(k * ang + rng.uniform(0, 6.28)) for k in (5, 9, 17, 31))
    band = np.exp(-((r - 0.9 - wob * 0.006) / 0.018) ** 2)
    trail = np.where(r < 0.9, np.exp(-(0.9 - r) / 0.12), 0) * (0.55 + 0.25 * wob)
    cv.core = band
    cv.glow = np.clip(trail * 0.6 + band * 0.4, 0, 1)
    cv.save('shock.png')


def shards():
    rng = random.Random(11)
    cv = Canvas(512)
    fill = np.zeros((512, 512), np.float32)
    for q in range(4):
        cx, cy = (-0.5 if q % 2 == 0 else 0.5), (-0.5 if q < 2 else 0.5)
        k = rng.randint(4, 6)
        angs = sorted(rng.uniform(0, 2 * math.pi) for _ in range(k))
        pts = [(cx + math.cos(a) * rng.uniform(0.22, 0.40) * (1.2 if i == 0 else 1),
                cy + math.sin(a) * rng.uniform(0.22, 0.40)) for i, a in enumerate(angs)]
        for i in range(k):
            cv.seg(pts[i], pts[(i + 1) % k], 0.008, 1.0, glow=3.0, gw=0.3)
        cv.seg(pts[0], pts[k // 2], 0.004, 0.7, glow=2.5, gw=0.2)
        inside = np.ones((512, 512), bool)
        for i in range(k):
            (ax, ay), (bx, by) = pts[i], pts[(i + 1) % k]
            inside &= (bx - ax) * (cv.y - ay) - (by - ay) * (cv.x - ax) >= 0
        d0 = np.hypot(cv.x - pts[0][0], cv.y - pts[0][1])
        fill += inside * (0.18 + 0.3 * np.exp(-(d0 / 0.25) ** 2))
    cv.glow += fill
    cv.save('shards.png', WHITE, np.array([1.0, 0.86, 0.6]), fade_edge=False)


def mote():
    cv = Canvas(64)
    x, y = cv.x, cv.y
    r = np.hypot(x, y)
    cv.core = np.clip(np.exp(-(r / 0.12) ** 2) + np.exp(-np.abs(y) / 0.03) * np.exp(-np.abs(x) / 0.45)
                      + np.exp(-np.abs(x) / 0.03) * np.exp(-np.abs(y) / 0.45), 0, 1)
    cv.glow = 0.5 * np.exp(-(r / 0.4) ** 2)
    cv.save('mote.png')


def blade():
    """A feathered wing blade: root at the bottom, tip at the top, white spine, soft vaned edges."""
    w, h = 128, 512
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    u = (xs + 0.5) / w * 2 - 1
    v = 1 - (ys + 0.5) / h
    half = np.clip(np.sin(np.pi * np.clip(v, 0, 1) ** 0.75), 0, 1) * (1 - v ** 3) * 0.95 + 1e-3
    x = u / half
    vane = np.clip(1 - np.abs(x), 0, 1) ** 1.6
    barbs = 0.75 + 0.25 * np.sin((v * 60 - np.abs(u) * 18) * np.pi)
    spine = np.exp(-(u / 0.035) ** 2) * np.clip(1 - v * 0.8, 0, 1)
    fade = np.clip(v / 0.06, 0, 1) * np.clip((1 - v) / 0.04, 0, 1)
    core = np.clip(spine, 0, 1) * fade
    glow = np.clip(vane * barbs * 0.8, 0, 1) * fade * (np.abs(x) < 1)
    a = np.clip(core + glow, 0, 1)[..., None]
    rgb = (WHITE * core[..., None] + np.array([0.92, 0.9, 1.0]) * glow[..., None]) / np.maximum(
        (core + glow)[..., None], 1e-6)
    img = np.concatenate([rgb, a], -1)
    path = os.path.join(OUT, '..', '..', 'entity', 'tetrarch_blade.png')
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA').save(path)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for f in (seal, rune_ring, cracks, flare, glow, rays, shock, shards, mote, blade):
        f()
        print('painted', f.__name__)
