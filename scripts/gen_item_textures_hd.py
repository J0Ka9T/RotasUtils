"""Paints the mod's item icons at 64x64: lit, bevelled, textured renders instead of flat pixel art.

Everything is painted at 512px and box-filtered down to 64, so edges are clean. Shapes are masks;
their depth is a height field from a distance transform, and light from the top left (diffuse and
specular from the height field's normals) picks colours from hue-shifted ramps. Stone, paper and
metal get layered noise so no surface is a flat gradient.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

OUT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources',
                   'assets', 'rotasutils', 'textures', 'item')
N = 512
OUTN = 64
Y, X = np.mgrid[0:N, 0:N].astype(np.float32) / N
LIGHT = np.array([-0.55, -0.65, 0.52], np.float32)
LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1], np.float32)
HALF /= np.linalg.norm(HALF)


def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255


def ramp(t, stops):
    ps = [s[0] for s in stops]
    cs = np.array([rgb(s[1]) for s in stops])
    return np.stack([np.interp(t, ps, cs[:, k]) for k in range(3)], -1).astype(np.float32)


# ---- masks --------------------------------------------------------------------------------------------
def _mask(draw):
    im = Image.new('L', (N, N), 0)
    draw(ImageDraw.Draw(im))
    return np.asarray(im, np.float32) / 255


def poly(pts):
    return _mask(lambda d: d.polygon([(x * N, y * N) for x, y in pts], fill=255))


def circle(cx, cy, r):
    return _mask(lambda d: d.ellipse([(cx - r) * N, (cy - r) * N, (cx + r) * N, (cy + r) * N], fill=255))


def ellipse(cx, cy, rx, ry):
    return _mask(lambda d: d.ellipse([(cx - rx) * N, (cy - ry) * N, (cx + rx) * N, (cy + ry) * N], fill=255))


def stroke(pts, w0, w1=None):
    """A polyline of round dabs, width easing from w0 to w1 along it."""
    w1 = w0 if w1 is None else w1
    segs = []
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        segs.append(math.hypot(bx - ax, by - ay))
    total = sum(segs) or 1e-6

    def draw(d):
        run = 0.0
        for (ax, ay), (bx, by), ln in zip(pts, pts[1:], segs):
            steps = max(2, int(ln * N / 1.5))
            for i in range(steps + 1):
                f = i / steps
                x, y = ax + (bx - ax) * f, ay + (by - ay) * f
                w = w0 + (w1 - w0) * (run + ln * f) / total
                d.ellipse([(x - w) * N, (y - w) * N, (x + w) * N, (y + w) * N], fill=255)
            run += ln
    return _mask(draw)


def chaikin(pts, n=3, closed=True):
    for _ in range(n):
        out = []
        m = len(pts)
        for i in range(m if closed else m - 1):
            a, b = pts[i], pts[(i + 1) % m]
            out.append((a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25))
            out.append((a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75))
        pts = out
    return pts


def scaled(pts, s, cx=0.5, cy=0.5):
    return [(cx + (x - cx) * s, cy + (y - cy) * s) for x, y in pts]


def blur(m, s):
    return ndi.gaussian_filter(m, s)


def edt(m):
    return ndi.distance_transform_edt(m > 0.5).astype(np.float32)


def dome(m, r):
    """Rounded height, 0 at the edge rising to 1 by r (unit) inside."""
    return np.sin(np.clip(edt(m) / (r * N), 0, 1) * math.pi / 2)


def fbm(seed, scales=(80, 30, 11, 4), weights=(1, 0.6, 0.35, 0.2)):
    rng = np.random.default_rng(seed)
    out = np.zeros((N, N), np.float32)
    for s, w in zip(scales, weights):
        a = ndi.gaussian_filter(rng.random((N, N)).astype(np.float32), s)
        out += w * (a - a.mean()) / (a.std() + 1e-6)
    return out / sum(weights)


def light(h, k=60.0):
    gy, gx = np.gradient(h)
    nx, ny = -gx * k * N / 100, -gy * k * N / 100
    inv = 1 / np.sqrt(nx * nx + ny * ny + 1)
    nx, ny, nz = nx * inv, ny * inv, inv
    diff = np.clip(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2], 0, 1)
    spec = np.clip(nx * HALF[0] + ny * HALF[1] + nz * HALF[2], 0, 1) ** 28
    return diff, spec


# ---- canvas -------------------------------------------------------------------------------------------
class Canvas:
    def __init__(self):
        self.p = np.zeros((N, N, 3), np.float32)   # premultiplied colour
        self.a = np.zeros((N, N), np.float32)

    def put(self, col, alpha):
        alpha = np.clip(alpha, 0, 1)
        self.p = col * alpha[..., None] + self.p * (1 - alpha[..., None])
        self.a = alpha + self.a * (1 - alpha)

    def glow(self, col, intensity):
        """Additive light that also lights empty pixels."""
        i = np.clip(intensity, 0, 1)
        self.p = self.p + col * i[..., None] * (1 - 0.0)
        self.a = np.clip(self.a + i * (1 - self.a), 0, 1)

    def outline(self, colour, w, darken=0.0):
        """A rim round the current silhouette, drawn behind it."""
        d = ndi.distance_transform_edt(self.a < 0.5)
        ring = ((d > 0) & (d <= w * N)).astype(np.float32)
        ring = blur(ring, 0.8)
        old_p, old_a = self.p, self.a
        self.p = np.zeros_like(old_p)
        self.a = np.zeros_like(old_a)
        self.put(colour, ring)
        self.p = old_p + self.p * (1 - old_a[..., None])
        self.a = np.clip(old_a + self.a * (1 - old_a), 0, 1)

    def save(self, name):
        a = self.a
        col = self.p / np.maximum(a[..., None], 1e-5)
        f = N // OUTN
        pa = (self.p.reshape(OUTN, f, OUTN, f, 3).mean((1, 3)))
        aa = a.reshape(OUTN, f, OUTN, f).mean((1, 3))
        out = np.concatenate([pa / np.maximum(aa[..., None], 1e-5), aa[..., None]], -1)
        img = Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')
        img.save(os.path.join(OUT, name + '.png'))


def solid(cv, mask, stops, depth=0.05, k=60.0, tex=None, texamt=0.12, spec=0.5, extra_h=None):
    """A bevelled, lit shape painted onto the canvas."""
    h = dome(mask, depth)
    if extra_h is not None:
        h = h + extra_h
    diff, sp = light(h, k)
    t = diff
    if tex is not None:
        t = np.clip(t + tex * texamt, 0, 1)
    col = ramp(t, stops)
    col = col + sp[..., None] * spec
    cv.put(np.clip(col, 0, 1), mask)


# ---- palettes -----------------------------------------------------------------------------------------
GOLD = [(0, '#3a1f0c'), (0.35, '#a8641c'), (0.65, '#e2a736'), (0.85, '#ffdf7a'), (1, '#fff6c8')]
STEEL = [(0, '#15161f'), (0.35, '#4a4e63'), (0.65, '#8a90aa'), (0.85, '#c9cfe4'), (1, '#f4f6ff')]
IRON = [(0, '#101018'), (0.35, '#34364a'), (0.65, '#5d617a'), (0.85, '#9096b0'), (1, '#d8dcf0')]
BRONZE = [(0, '#2a1416'), (0.35, '#7a4636'), (0.65, '#bc7f66'), (0.85, '#e8b39c'), (1, '#fff0e4')]
CRIMSON = [(0, '#1e070c'), (0.35, '#6a1a22'), (0.65, '#b23a3a'), (0.85, '#ee7b6c'), (1, '#ffd0c4')]
VIOLET = [(0, '#120a26'), (0.35, '#3c2478'), (0.65, '#7a52d0'), (0.85, '#b898f4'), (1, '#f0e6ff')]
GREEN = [(0, '#07160f'), (0.35, '#1c4a34'), (0.65, '#3a8a58'), (0.85, '#7fcf94'), (1, '#dcffe4')]
VOID = [(0, '#050308'), (0.35, '#1a1424'), (0.65, '#382e4a'), (0.85, '#665a7c'), (1, '#b8aed0')]
STONE = [(0, '#1c1a22'), (0.3, '#46414e'), (0.6, '#76707e'), (0.85, '#a8a2ae'), (1, '#dcd8de')]


# ---- gems ---------------------------------------------------------------------------------------------
def gem(cv, cx, cy, s, pal, seed=1, sparkle=False):
    """A cut crystal: six crown facets round a table, each lit by the way it faces."""
    O = [(0, -0.5), (0.36, -0.26), (0.36, 0.24), (0, 0.5), (-0.36, 0.24), (-0.36, -0.26)]
    T = [(0, -0.2), (0.17, -0.08), (0.17, 0.13), (0, 0.27), (-0.17, 0.13), (-0.17, -0.08)]
    tf = lambda p: (cx + p[0] * s, cy + p[1] * s)
    O = [tf(p) for p in O]
    T = [tf(p) for p in T]
    silhouette = poly(O)
    cv.put(rgb(pal['dark']), silhouette)
    L = np.array([-0.6, -0.75])
    L /= np.linalg.norm(L)
    grad = np.clip(0.5 + 0.5 * ((X - cx) * L[0] + (Y - cy) * L[1]) / (0.5 * s), 0, 1)
    noise = fbm(seed, (30, 9), (1, 0.5))
    stops = [(0, pal['dark']), (0.4, pal['mid']), (0.75, pal['light']), (1, pal['hi'])]
    for i in range(6):
        j = (i + 1) % 6
        m = poly([O[i], O[j], T[j], T[i]])
        mid = np.array([(O[i][0] + O[j][0]) / 2 - cx, (O[i][1] + O[j][1]) / 2 - cy])
        mid /= np.linalg.norm(mid)
        b = 0.5 + 0.5 * float(mid @ L)
        t = np.clip(0.12 + b * 0.62 + 0.28 * grad + noise * 0.04, 0, 1)
        cv.put(ramp(t, stops), m)
    table = poly(T)
    t = np.clip(0.55 + 0.4 * grad + noise * 0.05, 0, 1)
    cv.put(ramp(t, stops), table)
    # bright and dark facet edges
    for i in range(6):
        j = (i + 1) % 6
        e = stroke([O[i], T[i]], 0.0035 * s * 2)
        f = np.clip(0.5 + 0.5 * ((O[i][0] - cx) * L[0] + (O[i][1] - cy) * L[1]) / (0.5 * s), 0, 1)
        cv.put(ramp(np.array(0.35 + 0.65 * f), stops) * (0.85 + 0.15 * f), e * 0.65)
        e2 = stroke([T[i], T[j]], 0.003 * s * 2)
        cv.put(rgb(pal['hi']), e2 * 0.55)
    # inner glow in the table, and a specular flash on the lit crown
    core = np.exp(-(((X - cx) / (0.16 * s)) ** 2 + ((Y - cy - 0.03 * s) / (0.2 * s)) ** 2))
    cv.put(rgb(pal['hi']), core * 0.55 * table)
    flash = ellipse(cx - 0.15 * s, cy - 0.2 * s, 0.05 * s, 0.09 * s)
    cv.put(np.array([1, 1, 1], np.float32), blur(flash, 1.2) * 0.85)
    cv.put(np.array([1, 1, 1], np.float32), blur(circle(cx - 0.06 * s, cy - 0.36 * s, 0.018 * s), 1) * 0.9)
    edge = edt(silhouette)
    cv.put(rgb(pal['dark']) * 0.6, np.clip(1 - edge / 5, 0, 1) * silhouette * 0.7)
    if sparkle:
        for (px, py, r) in ((cx - 0.55 * s, cy - 0.5 * s, 0.09 * s), (cx + 0.52 * s, cy + 0.42 * s, 0.07 * s),
                            (cx + 0.5 * s, cy - 0.55 * s, 0.05 * s)):
            star(cv, px, py, r, rgb(pal['hi']))


def star(cv, cx, cy, r, col):
    """A four-point sparkle: two thin diamonds and a soft core."""
    a = poly([(cx, cy - r), (cx + r * 0.13, cy), (cx, cy + r), (cx - r * 0.13, cy)])
    b = poly([(cx - r, cy), (cx, cy - r * 0.13), (cx + r, cy), (cx, cy + r * 0.13)])
    cv.glow(col, blur(np.maximum(a, b), 1.0) * 0.95)
    cv.glow(np.array([1, 1, 1], np.float32), blur(circle(cx, cy, r * 0.16), 1.5))


GEMS = {
    'elunium': dict(dark='#0e3326', mid='#26a66c', light='#8be8a6', hi='#eaffe8'),
    'oridecon': dict(dark='#10244a', mid='#3a7fd8', light='#8cc4fa', hi='#e6f4ff'),
}


def gems():
    for name, pal in GEMS.items():
        cv = Canvas()
        gem(cv, 0.5, 0.52, 0.86, pal, seed=3)
        cv.outline(rgb(pal['dark']) * 0.45, 0.012)
        cv.save(name)
        cv = Canvas()
        cv.glow(rgb(pal['light']) * 0.9, blur(circle(0.5, 0.52, 0.34), 40) * 1.2)
        gem(cv, 0.5, 0.52, 0.78, pal, seed=4, sparkle=True)
        cv.outline(rgb(pal['dark']) * 0.45, 0.012)
        cv.save('enriched_' + name)


# ---- glyph masks (outer, inner) -----------------------------------------------------------------------
def flame():
    pts = chaikin([(0.5, 0.2), (0.58, 0.34), (0.56, 0.42), (0.66, 0.5), (0.68, 0.62), (0.6, 0.74), (0.5, 0.78),
                   (0.4, 0.74), (0.32, 0.62), (0.35, 0.5), (0.43, 0.42), (0.42, 0.32)], 4)
    inner = chaikin([(0.5, 0.42), (0.57, 0.54), (0.58, 0.64), (0.5, 0.72), (0.42, 0.64), (0.43, 0.54)], 4)
    return poly(pts), poly(inner)


def snowflake():
    outer = np.zeros((N, N), np.float32)
    inner = np.zeros((N, N), np.float32)
    for k in range(6):
        a = math.pi / 3 * k
        dx, dy = math.cos(a), math.sin(a)
        tip = (0.5 + dx * 0.3, 0.5 + dy * 0.3)
        outer = np.maximum(outer, stroke([(0.5, 0.5), tip], 0.022))
        for f, ln in ((0.5, 0.11), (0.75, 0.08)):
            bx, by = 0.5 + dx * 0.3 * f, 0.5 + dy * 0.3 * f
            for sgn in (-1, 1):
                b = a + sgn * math.pi / 3
                outer = np.maximum(outer, stroke([(bx, by), (bx + math.cos(b) * ln, by + math.sin(b) * ln)], 0.016))
        inner = np.maximum(inner, stroke([(0.5, 0.5), (0.5 + dx * 0.27, 0.5 + dy * 0.27)], 0.008))
    outer = np.maximum(outer, poly([(0.5 + 0.06 * math.cos(math.pi / 3 * k + 0.5), 0.5 + 0.06 * math.sin(math.pi / 3 * k + 0.5)) for k in range(6)]))
    return outer, inner


def bolt():
    pts = [(0.58, 0.16), (0.36, 0.5), (0.5, 0.5), (0.4, 0.84), (0.68, 0.42), (0.53, 0.42), (0.66, 0.16)]
    inner = scaled(pts, 0.62, 0.52, 0.5)
    return poly(pts), poly(inner)


def heart():
    pts = []
    for i in range(80):
        t = math.pi * 2 * i / 80
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((0.5 + x * 0.0195, 0.47 + y * 0.0195))
    return poly(pts), poly(scaled(pts, 0.6, 0.5, 0.5))


def droplet():
    pts = []
    for i in range(60):
        t = math.pi * 2 * i / 60
        r = 0.2 * (1 - math.sin(t) * 0.0)
        # teardrop: pointed top, round bottom
        x = 0.5 + 0.2 * math.sin(t) * (0.5 - 0.5 * math.cos(t) * 0 + 0.5 * (1 - math.cos(t)) * 0.0)
        y = 0.6 - 0.22 * math.cos(t)
        pts.append((x, y))
    top = [(0.5, 0.16), (0.63, 0.44), (0.7, 0.58), (0.66, 0.7), (0.58, 0.77), (0.5, 0.79), (0.42, 0.77), (0.34, 0.7),
           (0.3, 0.58), (0.37, 0.44)]
    outer = poly(chaikin(top, 4))
    inner = poly(chaikin(scaled(top, 0.5, 0.5, 0.64), 4))
    return outer, inner


SIGNS = {
    'fire': (flame, '#ffb14a', '#ff5a22', '#fff3b0'),
    'frost': (snowflake, '#9be8ff', '#3d96d8', '#f0ffff'),
    'fury': (bolt, '#ffd84a', '#e6821a', '#fff8c0'),
    'lifesteal': (heart, '#ff6274', '#c2213c', '#ffc4cc'),
    'venom': (droplet, '#9ae65a', '#2f9a2f', '#e6ffc0'),
}


def runes():
    rng = np.random.default_rng(7)
    for name, (fn, mid, deep, hot) in SIGNS.items():
        cv = Canvas()
        # a rough slab: super-ellipse with jittered edge, one chipped corner
        pts = []
        for i in range(36):
            t = math.pi * 2 * i / 36
            c, s = math.cos(t), math.sin(t)
            e = 4.0
            rx = 0.36 / (abs(c) ** e + abs(s) ** e) ** (1 / e) if (abs(c) + abs(s)) else 0.36
            r = rx * (1 + rng.uniform(-0.03, 0.03))
            pts.append((0.5 + c * r * 0.92, 0.5 + s * r * 1.06))
        slab = poly(chaikin(pts, 2))
        chip = poly([(0.78, 0.13), (0.9, 0.13), (0.9, 0.27)])
        slab = np.clip(slab - chip, 0, 1)
        noise = fbm(11 + len(name))
        grain = fbm(5, (2.5,), (1,))
        cracks = np.zeros((N, N), np.float32)
        for _ in range(3):
            x, y = rng.uniform(0.25, 0.75), rng.uniform(0.2, 0.8)
            a = rng.uniform(0, 6.28)
            path = [(x, y)]
            for _ in range(9):
                a += rng.uniform(-0.6, 0.6)
                x += math.cos(a) * 0.035
                y += math.sin(a) * 0.035
                path.append((x, y))
            cracks = np.maximum(cracks, stroke(path, 0.0035, 0.0012))
        h_extra = noise * 0.05 + grain * 0.012 - blur(cracks, 1.2) * 0.1
        solid(cv, slab, STONE, depth=0.07, k=70, tex=noise, texamt=0.16, spec=0.12, extra_h=h_extra)
        cv.put(rgb('#14121a'), cracks * slab * 0.85)
        # speckles of lighter mineral
        spk = (fbm(23, (1.6,), (1,)) > 1.7).astype(np.float32)
        cv.put(rgb('#cfc9d4'), spk * slab * 0.25)
        outer, inner = fn()
        outer = np.clip(outer * slab, 0, 1)
        # the carved groove, then the light held in it
        groove = blur(outer, 2.4)
        cv.put(rgb('#0a0810'), np.clip(groove * 1.4, 0, 1) * slab * 0.8)
        d = edt(outer)
        t = np.clip(d / (d.max() + 1e-6) * 1.2, 0, 1)
        glow_col = ramp(t, [(0, deep), (0.45, mid), (1, hot)])
        cv.put(glow_col, outer)
        cv.put(rgb(hot), np.clip(inner, 0, 1) * outer * 0.75)
        bloom = blur(outer, 16) * 0.9
        cv.p = cv.p + rgb(mid) * (bloom * slab)[..., None] * 0.55
        cv.outline(rgb('#12101a'), 0.011)
        cv.save('rune_' + name)


# ---- scrolls ------------------------------------------------------------------------------------------
def paper(cv, x0, y0, x1, y1, seed, tone):
    m = poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)])
    ragged = fbm(seed, (6,), (1,))
    edge = edt(m)
    m = m * (edge > (3 + 2.5 * (ragged + 1))).astype(np.float32) if False else m
    n = fbm(seed + 1, (60, 14, 3), (1, 0.6, 0.35))
    fibre = fbm(seed + 2, (1.2,), (1,))
    t = np.clip(0.62 + n * 0.09 + fibre * 0.03 + 0.1 * (0.5 - (Y - y0) / (y1 - y0)), 0, 1)
    stops = [(0, tone[0]), (0.5, tone[1]), (0.8, tone[2]), (1, tone[3])]
    col = ramp(t, stops)
    burn = np.clip(1 - edge / (0.05 * N), 0, 1) ** 1.6
    col = col * (1 - 0.35 * burn[..., None]) + rgb('#5a3a1c') * (0.35 * burn[..., None]) * 0.5
    cv.put(col, m)
    return m


def roll(cv, x0, y0, x1, y1, wood):
    """A rolled end: a horizontal cylinder with rounded caps."""
    body = poly([(x0 + (y1 - y0) / 2, y0), (x1 - (y1 - y0) / 2, y0), (x1 - (y1 - y0) / 2, y1), (x0 + (y1 - y0) / 2, y1)])
    r = (y1 - y0) / 2
    m = np.maximum(body, np.maximum(circle(x0 + r, (y0 + y1) / 2, r), circle(x1 - r, (y0 + y1) / 2, r)))
    v = np.clip((Y - y0) / (y1 - y0), 0, 1)
    prof = np.clip(np.sin(v * math.pi), 0, 1)
    grain = fbm(31, (2.5, 14), (0.5, 1))
    t = np.clip(0.18 + 0.72 * prof ** 0.9 - 0.25 * (v > 0.8) + grain * 0.04 + 0.18 * (v < 0.4) * prof, 0, 1)
    cv.put(ramp(t, [(0, wood[0]), (0.45, wood[1]), (0.8, wood[2]), (1, wood[3])]), m)
    cap = np.maximum(circle(x0 + r * 0.7, (y0 + y1) / 2, r * 0.75), circle(x1 - r * 0.7, (y0 + y1) / 2, r * 0.75))
    cv.put(rgb(wood[1]) * 0.8, cap * 0.35 * m)
    cv.put(rgb(wood[3]), blur(stroke([(x0 + r * 1.2, y0 + r * 0.55), (x1 - r * 1.2, y0 + r * 0.55)], 0.006), 1.2) * 0.5)
    return m


def wax(cv, cx, cy, r, colour, seed, symbol=None):
    rng = np.random.default_rng(seed)
    pts = []
    for i in range(24):
        t = math.pi * 2 * i / 24
        rr = r * (1 + rng.uniform(-0.09, 0.09))
        pts.append((cx + math.cos(t) * rr, cy + math.sin(t) * rr))
    m = poly(chaikin(pts, 2))
    blob = np.maximum(m, circle(cx + r * 0.5, cy + r * 1.0, r * 0.34))
    stops = [(0, colour[0]), (0.4, colour[1]), (0.75, colour[2]), (1, colour[3])]
    ring = np.clip(np.abs(edt(m) - r * N * 0.62) / (r * N * 0.1), 0, 1)
    h = dome(blob, r * 0.5) * 0.9 + (1 - ring) * 0.16 * m
    diff, sp = light(h, 70)
    cv.put(np.clip(ramp(diff, stops) + sp[..., None] * 0.55, 0, 1), blob)
    if symbol is not None:
        cv.put(rgb(colour[0]), blur(symbol, 1.0) * 0.55)
        cv.put(rgb(colour[3]), blur(np.roll(np.roll(symbol, -3, 0), -3, 1), 1.0) * 0.2 * m)


def ink_lines(cv, rows, x0, x1, seed, colour, width=0.0085):
    rng = np.random.default_rng(seed)
    for y in rows:
        x = x0
        while x < x1 - 0.05:
            ln = rng.uniform(0.06, 0.2)
            xe = min(x1, x + ln)
            wob = [(x + (xe - x) * f, y + rng.uniform(-0.003, 0.003)) for f in np.linspace(0, 1, 6)]
            cv.put(rgb(colour), blur(stroke(wob, width, width * 0.8), 0.9) * 0.78)
            x = xe + rng.uniform(0.02, 0.045)


def scrolls():
    tone = ['#7a5a3a', '#d9c08a', '#efdcae', '#fff3d2']
    wood = ['#2a1408', '#7a4626', '#b47a44', '#e2b07a']
    red = ['#3a0a10', '#8c1c24', '#c93a3a', '#ff9080']
    grn = ['#0c2a14', '#2f7a3a', '#5fbf5a', '#d0ffb8']
    vio = ['#1c0e3a', '#5a34a8', '#8e64e0', '#e0ccff']
    specs = {
        'blessing_scroll': (grn, 'leaf', '#3d6a34'),
        'certificate_scroll': (vio, 'ribbon', '#4a3a8a'),
        'protection_scroll': (red, 'shield', '#7a2a24'),
    }
    for i, (name, (wc, kind, ink)) in enumerate(specs.items()):
        cv = Canvas()
        x0, x1 = 0.17, 0.83
        paper(cv, x0, 0.25, x1, 0.75, 40 + i, tone)
        roll(cv, 0.11, 0.13, 0.89, 0.29, wood)
        roll(cv, 0.11, 0.71, 0.89, 0.87, wood)
        # shadow the roll casts on the sheet
        sh = blur(poly([(x0, 0.29), (x1, 0.29), (x1, 0.33), (x0, 0.33)]), 6)
        cv.put(rgb('#3a2410'), sh * 0.28 * (cv.a > 0.5))
        ink_lines(cv, [0.36, 0.43, 0.5, 0.57], x0 + 0.07, x1 - 0.07, 60 + i, ink)
        if kind == 'ribbon':
            for sgn, dx in ((-1, 0.0), (1, 0.05)):
                rib = poly([(0.6 + dx, 0.62), (0.68 + dx, 0.62), (0.7 + dx + sgn * 0.03, 0.9), (0.64 + dx, 0.86), (0.58 + dx + sgn * 0.03, 0.9)])
                solid(cv, rib, [(0, vio[0]), (0.4, vio[1]), (0.75, vio[2]), (1, vio[3])], depth=0.03, k=40)
        sym = None
        if kind == 'leaf':
            sym = poly(chaikin([(0.68, 0.6), (0.73, 0.65), (0.68, 0.72), (0.63, 0.65)], 3)) * 0
            sym = stroke([(0.66, 0.72), (0.69, 0.6)], 0.008) + poly(chaikin([(0.69, 0.6), (0.75, 0.64), (0.7, 0.71), (0.66, 0.68)], 3))
        elif kind == 'shield':
            sym = poly(chaikin([(0.64, 0.6), (0.73, 0.6), (0.73, 0.68), (0.685, 0.74), (0.64, 0.68)], 2))
        wax(cv, 0.685, 0.665, 0.085, wc, 70 + i, sym)
        cv.outline(rgb('#1c1008'), 0.008)
        cv.save(name)
    # the two sky scrolls carry one glowing glyph instead of writing
    def abyss_glyph():
        pts = chaikin([(0.5, 0.36), (0.6, 0.44), (0.56, 0.5), (0.62, 0.62), (0.5, 0.58), (0.38, 0.62), (0.44, 0.5), (0.4, 0.44)], 3)
        return poly(pts)

    def cele_glyph():
        pts = [(0.5, 0.34), (0.55, 0.47), (0.68, 0.5), (0.55, 0.53), (0.5, 0.66), (0.45, 0.53), (0.32, 0.5), (0.45, 0.47)]
        return poly(pts)

    for name, fn, cols, tn in (('scroll_abyss', abyss_glyph, ('#5a2f96', '#b070ff', '#f0dcff'), ['#4a3a5a', '#b9a4c6', '#dccbe6', '#f6ecff']),
                                ('scroll_celestial', cele_glyph, ('#2f5fa8', '#7fc0ff', '#ffffff'), ['#4a5a6a', '#a8c2d6', '#d6e6f2', '#f4fbff'])):
        cv = Canvas()
        paper(cv, 0.17, 0.25, 0.83, 0.75, 90, tn)
        roll(cv, 0.11, 0.13, 0.89, 0.29, wood)
        roll(cv, 0.11, 0.71, 0.89, 0.87, wood)
        g = fn()
        ink_lines(cv, [0.33, 0.68], 0.25, 0.75, 91, cols[0], 0.006)
        cv.put(rgb(cols[0]), blur(g, 2.5) * 0.7)
        d = edt(g)
        t = np.clip(d / (d.max() + 1e-6) * 1.2, 0, 1)
        cv.put(ramp(t, [(0, cols[0]), (0.5, cols[1]), (1, cols[2])]), g)
        cv.glow(rgb(cols[1]), blur(g, 14) * 0.8 * (cv.a > 0.3))
        cv.outline(rgb('#1c1008'), 0.008)
        cv.save(name)


# ---- sigils -------------------------------------------------------------------------------------------
def hexagon(r=0.46):
    return poly([(0.5 + r * math.cos(math.pi / 3 * i + math.pi / 6), 0.5 + r * math.sin(math.pi / 3 * i + math.pi / 6)) for i in range(6)])


def medal_mask(kind):
    if kind == 'round':
        return circle(0.5, 0.5, 0.46)
    if kind == 'hex':
        return blur(hexagon(0.48), 5) > 0.5
    if kind == 'diamond':
        return blur(poly([(0.5, 0.03), (0.95, 0.5), (0.5, 0.97), (0.05, 0.5)]), 4) > 0.5
    return blur(poly([(0.1, 0.08), (0.9, 0.08), (0.9, 0.5), (0.72, 0.8), (0.5, 0.95), (0.28, 0.8), (0.1, 0.5)]), 4) > 0.5


def eye_glyph():
    top = [(-0.3 + 0.6 * i / 20, -0.16 * (1 - ((i / 10) - 1) ** 2) * 1.0) for i in range(21)]
    pts = [(0.5 + x, 0.5 + y) for x, y in top] + [(0.5 + x, 0.5 - y) for x, y in reversed(top)]
    outer = poly(pts)
    iris = circle(0.5, 0.5, 0.115) * outer
    pupil = ellipse(0.5, 0.5, 0.04, 0.095)
    return outer, iris, pupil


def emblem_masks(name):
    """Returns (outer, inner) masks in the medal's centre."""
    if name == 'eye':
        outer, iris, pupil = eye_glyph()
        return outer, np.clip(iris - pupil, 0, 1)
    if name == 'flame':
        a, b = flame()
        return poly([(0, 0)]) * 0 + a, b
    if name == 'sun':
        rays = np.zeros((N, N), np.float32)
        for k in range(12):
            a = math.pi * 2 * k / 12
            w = 0.05 if k % 2 == 0 else 0.032
            ln = 0.3 if k % 2 == 0 else 0.23
            rays = np.maximum(rays, poly([(0.5 + math.cos(a - w) * 0.14, 0.5 + math.sin(a - w) * 0.14),
                                          (0.5 + math.cos(a) * ln, 0.5 + math.sin(a) * ln),
                                          (0.5 + math.cos(a + w) * 0.14, 0.5 + math.sin(a + w) * 0.14)]))
        disc = circle(0.5, 0.5, 0.15)
        return np.maximum(rays, disc), circle(0.5, 0.5, 0.09)
    if name == 'crown':
        outer = poly([(0.24, 0.66), (0.22, 0.36), (0.36, 0.5), (0.5, 0.28), (0.64, 0.5), (0.78, 0.36), (0.76, 0.66)])
        return outer, poly([(0.3, 0.6), (0.29, 0.46), (0.37, 0.55), (0.5, 0.4), (0.63, 0.55), (0.71, 0.46), (0.7, 0.6)])
    if name == 'star':
        pts = []
        for k in range(16):
            a = math.pi * 2 * k / 16 - math.pi / 2
            r = 0.32 if k % 4 == 0 else (0.13 if k % 2 == 0 else 0.07)
            pts.append((0.5 + math.cos(a) * r, 0.5 + math.sin(a) * r))
        return poly(pts), poly(scaled(pts, 0.5))
    if name == 'crack':
        pts = [(0.53, 0.14), (0.44, 0.34), (0.55, 0.42), (0.42, 0.62), (0.52, 0.66), (0.4, 0.88), (0.6, 0.62), (0.5, 0.58),
               (0.62, 0.4), (0.51, 0.34), (0.6, 0.16)]
        return poly(pts), poly(scaled(pts, 0.4, 0.52, 0.5))
    if name == 'blades':
        m = np.zeros((N, N), np.float32)
        core = np.zeros((N, N), np.float32)
        for sgn in (-1, 1):
            hx, hy = 0.5 + sgn * 0.13, 0.6
            tx, ty = 0.5 - sgn * 0.25, 0.2
            dx, dy = tx - hx, ty - hy
            ln = math.hypot(dx, dy)
            px, py = -dy / ln, dx / ln
            m = np.maximum(m, stroke([(hx, hy), (tx, ty)], 0.034, 0.006))
            core = np.maximum(core, stroke([(hx, hy), (tx, ty)], 0.012, 0.002))
            m = np.maximum(m, stroke([(hx - px * 0.09, hy - py * 0.09), (hx + px * 0.09, hy + py * 0.09)], 0.017))
            m = np.maximum(m, stroke([(hx, hy), (0.5 + sgn * 0.2, 0.76)], 0.02))
            m = np.maximum(m, circle(0.5 + sgn * 0.2, 0.77, 0.03))
        return m, core
    if name == 'swirl':
        pts = []
        for i in range(90):
            t = i / 89
            a = t * math.pi * 3.4
            r = 0.3 * (1 - t) + 0.02
            pts.append((0.5 + math.cos(a) * r, 0.5 + math.sin(a) * r))
        outer = stroke(pts, 0.045, 0.012)
        return outer, stroke(pts[8:], 0.02, 0.006)
    # maw
    outer = circle(0.5, 0.5, 0.3)
    return outer, np.zeros((N, N), np.float32)


def medal(name, kind, metal, field, emblem, epal, gem_col=None):
    cv = Canvas()
    m = medal_mask(kind).astype(np.float32)
    d = edt(m)
    rw = 0.075 * N
    # rim: a raised band, then a recessed field
    rim = np.where(d < rw, 0.6 + 0.4 * np.sin(np.clip(d / rw, 0, 1) * math.pi), 0.42 + 0.06 * np.clip((d - rw) / (0.05 * N), 0, 1))
    brushed = fbm(12, (1.5, 40), (1, 0.6))
    h = rim + brushed * 0.007
    diff, sp = light(h, 55)
    t = np.clip(diff + brushed * 0.03, 0, 1)
    col = np.clip(ramp(t, metal) + sp[..., None] * 0.55, 0, 1)
    cv.put(col, m)
    inner = (d > rw * 1.02).astype(np.float32)
    # the field: dark, lit softly from the top left, with a faint engraved ring
    fld = np.clip(0.55 + 0.45 * ((-(X - 0.5) - (Y - 0.5)) * 0.9), 0, 1) * 0.6
    cv.put(ramp(fld, [(0, field[0]), (0.6, field[1]), (1, field[2])]), inner * m)
    engr = np.clip(1 - np.abs(d - rw * 1.9) / 2.2, 0, 1) * inner
    cv.put(rgb(field[0]) * 0.6, engr * 0.55)
    cv.put(ramp(np.array(0.75), metal), np.clip(1 - np.abs(d - rw * 1.9 - 3) / 1.5, 0, 1) * inner * 0.25)
    # inner shadow at the field's top-left edge
    ish = np.clip(1 - (d - rw) / 14, 0, 1) * inner * np.clip(0.5 + 0.5 * ((-(X - 0.5) - (Y - 0.5))), 0, 1)
    cv.put(np.zeros(3, np.float32), ish * 0.5)
    # studs on the rim
    ys, xs = np.nonzero((d > rw * 0.35) & (d < rw * 0.65))
    outer, inn = emblem_masks(emblem)
    outer = np.clip(outer, 0, 1) * inner
    # the emblem, raised and lit, with a hot core
    eh = dome(outer, 0.03) + 0.1
    diff2, sp2 = light(eh, 50)
    ecol = ramp(np.clip(diff2 * 0.9, 0, 1), [(0, epal[0]), (0.55, epal[1]), (1, epal[2])])
    cv.put(np.clip(ecol + sp2[..., None] * 0.4, 0, 1), outer)
    if emblem != 'maw':
        cv.put(rgb(epal[2]), np.clip(inn, 0, 1) * outer * 0.85)
    else:
        teeth = np.zeros((N, N), np.float32)
        for k in range(7):
            x = 0.24 + 0.52 * k / 6
            teeth = np.maximum(teeth, poly([(x - 0.04, 0.32), (x + 0.04, 0.32), (x, 0.47)]))
            teeth = np.maximum(teeth, poly([(x - 0.04, 0.68), (x + 0.04, 0.68), (x, 0.53)]))
        cv.put(rgb('#0a0508'), circle(0.5, 0.5, 0.26) * outer)
        cv.put(rgb('#e8e0f0'), teeth * outer)
    cv.p = cv.p + rgb(epal[1]) * (blur(outer, 12) * inner)[..., None] * 0.35
    cv.outline(rgb(metal[0][1]), 0.012)
    cv.save(name)


def sigils():
    red = ('#5a1418', '#e8503c', '#ffd8c0')
    vio = ('#2a1a5a', '#a86cf4', '#f4e8ff')
    gld = ('#7a4a10', '#ffc840', '#fff6c8')
    grn = ('#0e3a24', '#54d47a', '#e0ffe8')
    wht = ('#8a5a1c', '#ffdf8a', '#ffffff')
    fields = {
        'crimson': ['#1e070c', '#4a1018', '#8a2a2e'], 'violet': ['#120a26', '#2c1a54', '#5a3aa0'],
        'gold': ['#2a1808', '#5a3a16', '#a8721e'], 'bronze': ['#20100e', '#4a2c30', '#8a5a58'],
        'iron': ['#0c0c14', '#1c1c28', '#3a3a52'], 'green': ['#06140c', '#14382a', '#2c7a54'],
        'void': ['#050308', '#120e18', '#282034'],
    }
    metals = {'crimson': CRIMSON, 'violet': VIOLET, 'gold': GOLD, 'bronze': BRONZE, 'iron': IRON, 'green': GREEN, 'void': VOID}
    specs = [
        ('crimson_sigil', 'round', 'crimson', 'eye', red),
        ('crimson_herald_sigil', 'hex', 'crimson', 'crown', red),
        ('eldritch_sigil', 'round', 'violet', 'eye', vio),
        ('gold_sigil', 'round', 'gold', 'sun', gld),
        ('gold_herald_sigil', 'hex', 'gold', 'crown', gld),
        ('herald_sigil', 'hex', 'bronze', 'star', ('#8a4a44', '#f0b0a0', '#fff0e8')),
        ('rift_sigil', 'diamond', 'violet', 'crack', vio),
        ('sky_clash_sigil', 'shield', 'iron', 'blades', ('#5a3aa0', '#ffd860', '#fff8d8')),
        ('sundering_sigil', 'round', 'gold', 'crack', wht),
        ('tentacle_rift', 'round', 'green', 'swirl', grn),
        ('void_maw', 'round', 'void', 'maw', ('#403450', '#a898c0', '#f0e8f8')),
        ('void_sigil', 'round', 'green', 'eye', grn),
    ]
    for name, shape, metal, emb, epal in specs:
        medal(name, shape, metals[metal], fields[metal], emb, epal)
    # convergence: a gold ring round four coloured quarters
    cv = Canvas()
    m = circle(0.5, 0.5, 0.46)
    d = edt(m)
    rw = 0.075 * N
    rim = np.where(d < rw, 0.6 + 0.4 * np.sin(np.clip(d / rw, 0, 1) * math.pi), 0.42)
    diff, sp = light(rim + fbm(12, (1.5, 40), (1, 0.6)) * 0.02, 55)
    cv.put(np.clip(ramp(diff, GOLD) + sp[..., None] * 0.55, 0, 1), m)
    inner = (d > rw * 1.02).astype(np.float32)
    quarters = [('#e8483c', '#ff9a86', '#8a1c1c', (X < 0.5) & (Y < 0.5)), ('#4ac878', '#a8f0b8', '#1c6a3a', (X >= 0.5) & (Y < 0.5)),
                ('#ffc040', '#fff0a0', '#9a6410', (X < 0.5) & (Y >= 0.5)), ('#8f6cff', '#dcccff', '#3a2a90', (X >= 0.5) & (Y >= 0.5))]
    dd = np.hypot(X - 0.5, Y - 0.5)
    for mid, hi, lo, sel in quarters:
        sm = (sel.astype(np.float32) * inner)
        t = np.clip(0.3 + 0.7 * (1 - dd / 0.4) * 0.8 + 0.2 * ((0.5 - X) + (0.5 - Y)), 0, 1)
        cv.put(ramp(t, [(0, lo), (0.5, mid), (1, hi)]), sm)
    cv.put(rgb('#3a1f0c'), stroke([(0.5, 0.06), (0.5, 0.94)], 0.008) * inner * 0.9)
    cv.put(rgb('#3a1f0c'), stroke([(0.06, 0.5), (0.94, 0.5)], 0.008) * inner * 0.9)
    cv.put(np.array([1, 1, 1], np.float32), blur(ellipse(0.36, 0.3, 0.045, 0.02), 1.5) * 0.7 * inner)
    cv.outline(rgb('#3a1f0c'), 0.012)
    cv.save('convergence_sigil')


# ---- key and rings ------------------------------------------------------------------------------------
def metal_shape(cv, mask, stops, depth=0.035, seed=5):
    n = fbm(seed, (1.5, 30), (1, 0.6))
    solid(cv, mask, stops, depth=depth, k=65, tex=n, texamt=0.08, spec=0.6, extra_h=n * 0.015)


def keys():
    cv = Canvas()
    bow = circle(0.31, 0.31, 0.2) - circle(0.31, 0.31, 0.11)
    bow = np.clip(bow, 0, 1)
    shaft = stroke([(0.44, 0.44), (0.82, 0.82)], 0.035)
    teeth = np.maximum(stroke([(0.7, 0.7), (0.62, 0.78)], 0.03), stroke([(0.78, 0.78), (0.7, 0.86)], 0.03))
    collar = np.maximum(stroke([(0.5, 0.5), (0.5, 0.5)], 0.055), circle(0.55, 0.55, 0.04))
    metal_shape(cv, np.clip(bow + shaft + teeth + collar, 0, 1), VIOLET, 0.03, 6)
    gem(cv, 0.31, 0.31, 0.15, dict(dark='#2a1060', mid='#8a5ce8', light='#c8a8ff', hi='#f6eeff'), seed=8)
    cv.glow(rgb('#b898f4'), blur(circle(0.31, 0.31, 0.1), 14) * 0.6)
    cv.outline(rgb('#0c0618'), 0.012)
    cv.save('rift_key')


def rings():
    specs = {
        'affinity_ring_abyss': (IRON, dict(dark='#2a1060', mid='#8a3ce0', light='#c88cff', hi='#f8ecff'), '#b070ff'),
        'affinity_ring_celestial': ([(0, '#2a3450'), (0.35, '#7c8aa8'), (0.65, '#c8d4ea'), (0.85, '#f0f4fc'), (1, '#ffffff')],
                                   dict(dark='#0c3a70', mid='#3aa0f0', light='#96d4ff', hi='#f0fbff'), '#80c8ff'),
    }
    for name, (metal, gpal, glowc) in specs.items():
        cv = Canvas()
        band = np.clip(ellipse(0.5, 0.62, 0.3, 0.3) - ellipse(0.5, 0.62, 0.2, 0.2), 0, 1)
        prongs = np.maximum(poly([(0.4, 0.34), (0.46, 0.24), (0.5, 0.36)]), poly([(0.6, 0.34), (0.54, 0.24), (0.5, 0.36)]))
        base = ellipse(0.5, 0.36, 0.13, 0.055)
        metal_shape(cv, np.clip(band + prongs + base, 0, 1), metal, 0.045, 9)
        # a sheen along the band's upper left
        sheen = blur(stroke([(0.27, 0.55), (0.34, 0.4)], 0.008), 3)
        cv.put(np.array([1, 1, 1], np.float32), sheen * 0.4 * band)
        gem(cv, 0.5, 0.27, 0.26, gpal, seed=10)
        cv.glow(rgb(glowc), blur(circle(0.5, 0.27, 0.1), 16) * 0.55)
        cv.outline(rgb('#0c0810'), 0.012)
        cv.save(name)


# ---- Red Reversal: a dark crimson core wrapped in unstable plasma ---------------------------------------
def red_reversal(max_=False):
    cv = Canvas()
    m = circle(0.5, 0.5, 0.36)
    n = fbm(61, (40, 14, 5), (1, 0.7, 0.4))
    stops = [(0, '#080105'), (0.3, '#3a050c'), (0.6, '#a01420'), (0.85, '#e8422c'), (1, '#ffb08a')]
    h = dome(m, 0.36) * 0.9 + n * 0.05
    diff, sp = light(h, 70)
    t = np.clip(diff * 0.9 + (n * 0.5 + 0.5) * 0.25 - 0.1, 0, 1)
    cv.glow(rgb('#ff2a1a') * 0.9, blur(m, 22) * 0.75)
    cv.put(np.clip(ramp(t, stops) + sp[..., None] * 0.35, 0, 1), m)
    # a near-black heart, so the shell reads as a shell
    core = circle(0.5, 0.5, 0.17)
    cv.put(rgb('#0a0206'), blur(core, 3) * 0.85 * m)
    # broken filaments arcing round it
    rng = np.random.default_rng(9)
    for k in range(9):
        a0 = rng.uniform(0, 6.28)
        span = rng.uniform(0.6, 1.4)
        r = rng.uniform(0.2, 0.34)
        pts = [(0.5 + math.cos(a0 + span * i / 12) * r * (1 + 0.05 * math.sin(i * 1.7)),
                0.5 + math.sin(a0 + span * i / 12) * r * (1 + 0.05 * math.sin(i * 1.7))) for i in range(13)]
        arc = stroke(pts, 0.012, 0.004)
        cv.put(rgb('#ff5a3a'), blur(arc, 1.0) * m * 0.9)
    hot = blur(circle(0.4, 0.37, 0.05), 6)
    cv.put(rgb('#fff0d8'), hot * 0.7)
    if max_:
        # MAX: a turning gold halo round the core, with four points like a crown
        halo = [(0.5 + math.cos(6.2832 * i / 48) * 0.44, 0.5 + math.sin(6.2832 * i / 48) * 0.44) for i in range(49)]
        cv.glow(rgb('#ffb040') * 0.8, blur(stroke(halo, 0.02), 8) * 0.6)
        cv.put(rgb('#ffc860'), blur(stroke(halo, 0.014, 0.014), 0.8))
        for k in range(4):
            a = 0.785 + k * 1.5708
            tip = [(0.5 + math.cos(a) * 0.40, 0.5 + math.sin(a) * 0.40), (0.5 + math.cos(a) * 0.49, 0.5 + math.sin(a) * 0.49)]
            cv.put(rgb('#fff0c0'), blur(stroke(tip, 0.022, 0.004), 0.8))
    cv.outline(rgb('#12030a'), 0.012)
    cv.save('red_reversal_max' if max_ else 'red_reversal')


def red_reversal_max():
    red_reversal(True)


if __name__ == '__main__':
    for f in (gems, runes, scrolls, sigils, keys, rings, red_reversal, red_reversal_max):
        f()
        print('painted', f.__name__)
