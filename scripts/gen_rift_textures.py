"""Generates the eldritch rift textures (assets/rotasutils/textures/environment/rift_*.png).

Every layer is painted procedurally from seeded noise so the look can be re-tuned and regenerated:

    python scripts/gen_rift_textures.py

The rift outline here must match EldritchRiftRenderer.halfWidth():
    halfWidth(t) = W * sqrt(1 - t^2) * (1 - |t|)^0.55,  t = y / H,  W = 10.8 deg, H = 24.5 deg
and the rim/abyss box must match EldritchRiftRenderer.BOX_HALF_WIDTH / BOX_HALF_HEIGHT.
"""
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources",
                   "assets", "rotasutils", "textures", "environment")
W, H = 10.8, 24.5
BOX_W, BOX_H = 30.0, 34.0

NAVY = np.array([0.02, 0.03, 0.09])
INDIGO = np.array([0.14, 0.10, 0.62])
ROYAL = np.array([0.16, 0.30, 1.00])
VIOLET = np.array([0.52, 0.25, 1.00])
LAVENDER = np.array([0.80, 0.66, 1.00])
CYAN = np.array([0.30, 0.84, 1.00])
WHITE = np.array([0.88, 0.96, 1.00])


def fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


def value_noise(u, v, gw, gh, rng, wrap_u=False, wrap_v=False):
    """Smooth value noise on a gw x gh lattice; u in [0, gw), v in [0, gh)."""
    grid = rng.random((gh + 1, gw + 1))
    if wrap_u:
        grid[:, gw] = grid[:, 0]
    if wrap_v:
        grid[gh, :] = grid[0, :]
    u = np.mod(u, gw) if wrap_u else np.clip(u, 0, gw - 1e-6)
    v = np.mod(v, gh) if wrap_v else np.clip(v, 0, gh - 1e-6)
    i0 = np.floor(u).astype(int)
    j0 = np.floor(v).astype(int)
    fu = fade(u - i0)
    fv = fade(v - j0)
    a = grid[j0, i0]
    b = grid[j0, i0 + 1]
    c = grid[j0 + 1, i0]
    d = grid[j0 + 1, i0 + 1]
    return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv


def fbm(u, v, gw, gh, rng, octaves=6, wrap_u=False, gain=0.52, ridged=False, wrap_v=False):
    total = np.zeros_like(u)
    amplitude, norm = 1.0, 0.0
    for octave in range(octaves):
        scale = 2 ** octave
        n = value_noise(u * scale, v * scale, gw * scale, gh * scale, rng, wrap_u=wrap_u, wrap_v=wrap_v)
        if ridged:
            n = 1.0 - np.abs(2.0 * n - 1.0)
        total += n * amplitude
        norm += amplitude
        amplitude *= gain
    return total / norm


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t)[..., None]
    return a * (1 - t) + b * t


def stars(shape, rng, density, size_bias=0.9):
    """Soft round stars: a gaussian dot per star, a few with a faint halo. No pixel crosses."""
    field = np.zeros(shape)
    count = int(shape[0] * shape[1] * density)
    ys = rng.random(count) * shape[0]
    xs = rng.random(count) * shape[1]
    brightness = rng.random(count) ** 3
    radius = 0.55 + 0.9 * brightness
    for y, x, b, r in zip(ys, xs, brightness, radius):
        reach = int(np.ceil(r * 3 + (4 if b > size_bias else 0)))
        y0, y1 = max(0, int(y) - reach), min(shape[0], int(y) + reach + 1)
        x0, x1 = max(0, int(x) - reach), min(shape[1], int(x) + reach + 1)
        if y0 >= y1 or x0 >= x1:
            continue
        yy, xx = np.mgrid[y0:y1, x0:x1]
        d2 = (yy + 0.5 - y) ** 2 + (xx + 0.5 - x) ** 2
        dot = np.exp(-d2 / (2 * r * r)) * b
        if b > size_bias:
            dot += np.exp(-d2 / (2 * (r * 3.5) ** 2)) * b * 0.18
        field[y0:y1, x0:x1] += dot
    return np.clip(field, 0, 1)


def save(rgb, alpha, name):
    rgba = np.concatenate([np.clip(rgb, 0, 1), np.clip(alpha, 0, 1)[..., None]], axis=-1)
    dither = np.random.default_rng(len(name)).random(rgba.shape[:2] + (1,)) - 0.5
    rgba[..., :3] = np.clip(rgba[..., :3] * 255 + dither, 0, 255) / 255
    Image.fromarray(np.clip(rgba * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, rgba.shape[1], "x", rgba.shape[0])


def half_width(t):
    t = np.clip(np.abs(t), 0, 1)
    return W * np.sqrt(1 - t * t) * (1 - t) ** 0.55


def vortex(size=1024, seed=11):
    """A circular spiral vortex, black at the heart. The renderer spins it inside the tear."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    x = (x + 0.5) / size * 2 - 1
    y = (y + 0.5) / size * 2 - 1
    r = np.sqrt(x * x + y * y)
    theta = np.arctan2(y, x)
    twisted = theta + 2.4 * np.log(r + 0.035)
    u = (twisted / (2 * np.pi)) % 1.0
    v = -np.log(r + 0.03) + 0.4
    arms = fbm(u * 5, v * 0.9, 5, 6, rng, octaves=6, wrap_u=True)
    fine = fbm(u * 22, v * 2.2, 22, 12, rng, octaves=4, wrap_u=True)
    ridge = fbm(u * 9, v * 1.4, 9, 8, rng, octaves=5, wrap_u=True, ridged=True)
    tint = fbm(u * 3, v * 0.5, 3, 4, rng, octaves=3, wrap_u=True)

    body = smoothstep(0.05, 0.42, r) * (1 - smoothstep(0.80, 0.99, r))
    streak = np.clip(arms * 0.95 + fine * 0.55 - 0.55, 0, 1) ** 1.2
    colour = mix(INDIGO, ROYAL, smoothstep(0.35, 0.75, arms))
    colour = mix(colour, VIOLET, smoothstep(0.45, 0.8, tint) * 0.8)
    rgb = colour * (body * (0.10 + 1.6 * streak))[..., None]

    hot = np.clip(ridge - 0.66, 0, 1) / 0.34
    hot = hot ** 2.2 * body * smoothstep(0.2, 0.55, r)
    rgb += mix(mix(CYAN, LAVENDER, smoothstep(0.4, 0.8, tint)), WHITE, hot) * (hot * 1.3)[..., None]
    lip = np.exp(-((r - 0.72) / 0.14) ** 2) * (0.35 + 0.65 * arms)
    rgb += mix(ROYAL, LAVENDER, tint) * (lip * 0.35)[..., None]

    specks = stars((size, size), rng, 0.0009) * smoothstep(0.08, 0.3, r) * (1 - smoothstep(0.8, 0.98, r))
    rgb += WHITE * (specks * 0.9)[..., None]
    rgb *= (1 - smoothstep(0.9, 0.97, r))[..., None]
    save(rgb, np.ones((size, size)), "rift_vortex.png")


def box_grid(width, height):
    y, x = np.mgrid[0:height, 0:width]
    gx = ((x + 0.5) / width * 2 - 1) * BOX_W
    gy = -((y + 0.5) / height * 2 - 1) * BOX_H
    return gx, gy


def edge_distance(gx, gy):
    """Signed distance in degrees to the tear outline, negative inside, measured sideways."""
    t = gy / H
    d = np.abs(gx) - half_width(t)
    beyond = np.clip(np.abs(t) - 1, 0, None) * H
    return np.where(np.abs(t) <= 1, d, np.sqrt(np.abs(gx) ** 2 + beyond ** 2))


def rim(width=896, height=1024, seed=23, suffix=""):
    """The torn, burning lips of the tear and the plasma boiling off them."""
    rng = np.random.default_rng(seed)
    gx, gy = box_grid(width, height)
    u = (gx / BOX_W + 1) * 0.5
    v = (gy / BOX_H + 1) * 0.5
    warp = fbm(u * 6, v * 7, 6, 7, rng, octaves=6) - 0.5
    warp2 = fbm(u * 18, v * 20, 18, 20, rng, octaves=4) - 0.5
    d = edge_distance(gx, gy) + warp * 3.2 + warp2 * 0.9
    clouds = fbm(u * 5, v * 6, 5, 6, rng, octaves=7)
    tendrils = fbm(u * 10, v * 12, 10, 12, rng, octaves=5, ridged=True)
    tint = fbm(u * 3, v * 3, 3, 3, rng, octaves=3)

    tip = 1 - smoothstep(0.92, 1.12, np.abs(gy) / H)
    breakup = fbm(u * 9, v * 10, 9, 10, rng, octaves=5)
    width = 0.18 + 0.42 * breakup
    core = np.exp(-(d / width) ** 2) * np.clip(breakup ** 2.2 * 2.6 - 0.1, 0.05, 1.4)
    halo = np.exp(-np.abs(d) / 1.3) * (0.35 + 0.65 * clouds)
    inner = np.exp(np.minimum(d, 0) / 3.4) * (d < 0) * 0.95
    outside = np.maximum(d, 0)
    plasma = np.exp(-outside / 5.5) * np.clip(clouds * 1.8 - 0.5, 0, 1) ** 1.1 * (d > -1.4)
    wisps = np.exp(-outside / 7.5) * (np.clip(tendrils - 0.58, 0, 1) / 0.42) ** 1.4 * (d > 0)
    lilac = smoothstep(0.35, 0.75, tint)

    rgb = mix(ROYAL, VIOLET, lilac) * (plasma * 1.7)[..., None]
    rgb += mix(VIOLET, LAVENDER, clouds) * (plasma ** 2 * 0.8)[..., None]
    rgb += mix(ROYAL, LAVENDER, lilac * 0.6) * (wisps * 0.85)[..., None]
    rgb += mix(ROYAL, VIOLET, lilac) * (halo * 0.55)[..., None]
    rgb += mix(ROYAL, INDIGO, 0.2) * (inner * (0.5 + 0.8 * clouds))[..., None]
    rgb += mix(mix(CYAN, LAVENDER, lilac * 0.7), WHITE, np.clip(core - 0.4, 0, 1)) * (core * 1.25)[..., None]
    rgb *= (0.3 + 0.7 * tip)[..., None]
    border = 1 - smoothstep(0.62, 0.97, np.sqrt((gx / BOX_W) ** 2 + (gy / BOX_H) ** 2))
    rgb *= border[..., None]
    save(rgb, np.ones_like(gx), f"rift_rim{suffix}.png")

    hot = np.exp(-(d / 0.18) ** 2) * np.clip(tendrils * 1.4 - 0.5, 0, 1)
    arcs = np.exp(-outside / 2.0) * np.clip(tendrils - 0.8, 0, 1) / 0.2 * (d > 0)
    hot_rgb = mix(CYAN, WHITE, np.clip(hot, 0, 1)) * ((hot * 1.4 + arcs ** 2 * 0.8) * tip * border)[..., None]
    if not suffix:
        save(hot_rgb, np.ones_like(gx), "rift_rim_hot.png")


def abyss(width=448, height=512, seed=37):
    """The dark heart: a soft, near-black eye that swallows the middle of the vortex."""
    rng = np.random.default_rng(seed)
    gx, gy = box_grid(width, height)
    t = gy / (H * 0.72)
    shape = np.abs(gx) / np.maximum(half_width(t) * 0.78, 1e-3)
    e = np.where(np.abs(t) < 1, np.sqrt(shape ** 2 * 0.8 + t ** 2 * 0.5), 9)
    u = (gx / BOX_W + 1) * 0.5
    v = (gy / BOX_H + 1) * 0.5
    ragged = fbm(u * 8, v * 8, 8, 8, rng, octaves=4) - 0.5
    alpha = (1 - smoothstep(0.25, 1.0, e + ragged * 0.35)) * 0.97
    save(np.zeros(gx.shape + (3,)) + NAVY * 0.25, alpha, "rift_abyss.png")

    specks = stars(gx.shape, rng, 0.0016, size_bias=0.95) * (1 - smoothstep(0.1, 0.8, e))
    twinkle = mix(ROYAL, WHITE, 0.6) * specks[..., None]
    save(twinkle, np.ones_like(gx), "rift_specks.png")


def nebula(size=1024, seed=51):
    """Space being dragged toward the tear: twisted clouds and stars, fading out at the edge."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    x = (x + 0.5) / size * 2 - 1
    y = (y + 0.5) / size * 2 - 1
    r = np.sqrt(x * x + y * y)
    theta = np.arctan2(y, x)
    twisted = theta + 1.1 * np.log(r + 0.08)
    u = (twisted / (2 * np.pi)) % 1.0
    v = r * 0.7
    clouds = fbm(u * 7, v * 4, 7, 4, rng, octaves=7, wrap_u=True)
    detail = fbm(u * 26, v * 10, 26, 10, rng, octaves=4, wrap_u=True)
    ridge = fbm(u * 12, v * 6, 12, 6, rng, octaves=5, wrap_u=True, ridged=True)
    tint = fbm(u * 3, v * 2, 3, 2, rng, octaves=3, wrap_u=True)

    falloff = (1 - smoothstep(0.55, 0.98, r)) * smoothstep(0.08, 0.3, r)
    density = np.clip(clouds * 1.5 - 0.45 + (detail - 0.5) * 0.35, 0, 1) ** 1.6
    colour = mix(INDIGO, ROYAL, smoothstep(0.3, 0.7, clouds))
    colour = mix(colour, VIOLET, smoothstep(0.5, 0.85, tint) * 0.85)
    rgb = colour * (density * falloff * 0.75)[..., None]
    glow = np.clip(ridge - 0.78, 0, 1) / 0.22
    rgb += mix(CYAN, LAVENDER, tint) * (glow ** 2 * density * falloff * 0.55)[..., None]
    field = stars((size, size), rng, 0.0022) * (1 - smoothstep(0.7, 0.98, r))
    rgb += WHITE * (field * 0.85)[..., None]
    rgb *= (1 - smoothstep(0.93, 0.99, r))[..., None]
    save(rgb, np.ones((size, size)), "rift_nebula.png")


def sky_veil(width=2048, height=1024, seed=67):
    """A seamless tile for the whole sky, laid out around the rift: u goes round it, v runs away from it.

    Streaks are long in v, so when the renderer scrolls v the streams flow into the tear, and scrolling u
    turns the whole sky around it. Tiles in both directions; the fade by distance is done per vertex.
    """
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:height, 0:width]
    u = (x + 0.5) / width
    v = (y + 0.5) / height
    streams = fbm(u * 24, v * 3, 24, 3, rng, octaves=6, wrap_u=True, wrap_v=True)
    clouds = fbm(u * 8, v * 4, 8, 4, rng, octaves=7, wrap_u=True, wrap_v=True)
    ridge = fbm(u * 40, v * 5, 40, 5, rng, octaves=4, wrap_u=True, wrap_v=True, ridged=True)
    tint = fbm(u * 4, v * 2, 4, 2, rng, octaves=3, wrap_u=True, wrap_v=True)

    density = np.clip(clouds * 1.7 - 0.38, 0, 1) ** 1.25
    lanes = np.clip(streams * 1.9 - 0.62, 0, 1) ** 1.2
    colour = mix(INDIGO, ROYAL, smoothstep(0.3, 0.7, clouds))
    colour = mix(colour, VIOLET, smoothstep(0.5, 0.85, tint) * 0.8)
    rgb = colour * (density * 0.5 + lanes * (0.25 + density) * 0.95)[..., None]
    filaments = (np.clip(ridge - 0.8, 0, 1) / 0.2) ** 2 * density
    rgb += mix(CYAN, LAVENDER, tint) * (filaments * 0.6)[..., None]
    field = stars((height, width), rng, 0.0007)
    rgb += mix(ROYAL, WHITE, 0.75) * (field * 0.8)[..., None]
    save(rgb, np.ones((height, width)), "sky_veil.png")


def glow(size=512, seed=71):
    """A star-bright flare: soft core, violet bloom and a tall anamorphic streak. Seed point and flashes."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    x = (x + 0.5) / size * 2 - 1
    y = (y + 0.5) / size * 2 - 1
    r = np.sqrt(x * x + y * y)
    core = np.exp(-(r / 0.05) ** 2)
    bloom = np.exp(-(r / 0.22) ** 2) * 0.55 + np.exp(-(r / 0.5) ** 2) * 0.18
    streak = np.exp(-(x / 0.012) ** 2) * np.exp(-(np.abs(y) / 0.55)) * 0.8
    streak += np.exp(-(y / 0.01) ** 2) * np.exp(-(np.abs(x) / 0.28)) * 0.25
    theta = np.arctan2(y, x)
    rays = fbm((theta / (2 * np.pi)) % 1.0 * 32, r * 2, 32, 3, rng, octaves=3, wrap_u=True)
    bloom *= 0.75 + 0.5 * rays
    rgb = WHITE * core[..., None] + mix(ROYAL, VIOLET, smoothstep(0.1, 0.5, r)) * bloom[..., None]
    rgb += mix(CYAN, WHITE, 0.5) * streak[..., None]
    rgb *= (1 - smoothstep(0.85, 0.99, r))[..., None] * (1 - smoothstep(0.9, 1.0, np.maximum(np.abs(x), np.abs(y))))[..., None]
    save(rgb, np.ones((size, size)), "rift_glow.png")


def ring(size=1024, seed=73):
    """A torn shockwave ring: bright leading edge, violet wake behind it, broken by noise."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    x = (x + 0.5) / size * 2 - 1
    y = (y + 0.5) / size * 2 - 1
    r = np.sqrt(x * x + y * y)
    theta = np.arctan2(y, x)
    u = (theta / (2 * np.pi)) % 1.0
    breakup = fbm(u * 18, r * 4, 18, 5, rng, octaves=5, wrap_u=True)
    wobble = (fbm(u * 10, np.zeros_like(r) + 0.5, 10, 1, rng, octaves=3, wrap_u=True) - 0.5) * 0.03
    edge = r - (0.9 + wobble)
    front = np.exp(-(edge / 0.02) ** 2)
    wake = np.exp(np.minimum(edge, 0) / 0.2) * (edge < 0) * 0.95
    strength = np.clip(breakup * 1.6 - 0.35, 0.1, 1.2)
    rgb = mix(CYAN, WHITE, 0.4) * (front * strength)[..., None]
    rgb += mix(ROYAL, VIOLET, breakup) * (wake * strength)[..., None]
    rgb *= (1 - smoothstep(0.96, 0.995, r))[..., None]
    save(rgb, np.ones((size, size)), "rift_ring.png")


def crack(width=512, height=128, seed=79):
    """Cross-section of a sky crack, tiling along its length: white-hot core, cyan halo, violet bleed."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:height, 0:width]
    u = (x + 0.5) / width
    v = (y + 0.5) / height * 2 - 1
    along = fbm(u * 16, np.zeros_like(u) + 0.5, 16, 1, rng, octaves=5, wrap_u=True)
    wobble = (fbm(u * 8, np.zeros_like(u) + 0.5, 8, 1, rng, octaves=4, wrap_u=True) - 0.5) * 0.25
    d = np.abs(v - wobble)
    strength = 0.45 + 0.9 * along
    core = np.exp(-(d / 0.05) ** 2) * strength
    halo = np.exp(-(d / 0.2) ** 2) * (0.35 + 0.65 * along)
    bleed = np.exp(-d / 0.35) * 0.35
    rgb = mix(CYAN, WHITE, np.clip(core, 0, 1)) * (core * 1.3)[..., None]
    rgb += mix(ROYAL, CYAN, along) * (halo * 0.8)[..., None]
    rgb += mix(INDIGO, VIOLET, along) * bleed[..., None]
    rgb *= (1 - smoothstep(0.82, 1.0, np.abs(v)))[..., None]
    save(rgb, np.ones((height, width)), "sky_crack.png")


def bloom():
    """Fake camera bloom: the lips and the heart of the vortex blurred wide, as bright light bleeds."""
    from PIL import ImageFilter
    rim_image = Image.open(os.path.join(OUT, "rift_rim.png")).convert("RGB")
    small = rim_image.resize((rim_image.width // 4, rim_image.height // 4), Image.BILINEAR)
    blurred = small.filter(ImageFilter.GaussianBlur(9)).filter(ImageFilter.GaussianBlur(6))
    glow = np.asarray(blurred.resize(rim_image.size, Image.BICUBIC)).astype(np.float64) / 255
    gx, gy = box_grid(rim_image.width, rim_image.height)
    border = 1 - smoothstep(0.55, 0.97, np.sqrt((gx / BOX_W) ** 2 + (gy / BOX_H) ** 2))
    glow = np.clip(glow * 2.2, 0, 1) * border[..., None]
    save(glow, np.ones(glow.shape[:2]), "rift_bloom.png")


def rays(size=1024, seed=83):
    """God rays from the tear: thin, uneven shafts that fade with distance."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    x = (x + 0.5) / size * 2 - 1
    y = (y + 0.5) / size * 2 - 1
    r = np.sqrt(x * x + y * y)
    u = (np.arctan2(y, x) / (2 * np.pi)) % 1.0
    zero = np.zeros_like(r) + 0.5
    shafts = fbm(u * 90, zero, 90, 1, rng, octaves=3, wrap_u=True)
    broad = fbm(u * 14, zero, 14, 1, rng, octaves=3, wrap_u=True)
    length = fbm(u * 30, zero, 30, 1, rng, octaves=2, wrap_u=True)
    beam = np.clip(shafts * 1.9 - 0.85, 0, 1) ** 1.3 * np.clip(broad * 1.7 - 0.3, 0, 1)
    reach = 0.35 + 0.65 * length
    fall = smoothstep(0.12, 0.3, r) * np.exp(-np.maximum(r - 0.3, 0) / (0.25 * reach)) * (1 - smoothstep(0.9, 0.99, r))
    rgb = mix(ROYAL, mix(CYAN, LAVENDER, broad), shafts) * (beam * fall * 2.4)[..., None]
    save(rgb, np.ones((size, size)), "rift_rays.png")


def flare(width=1024, height=256):
    """Cinematic lens flare for the heart of the tear: a long anamorphic streak, a thin halo ring and
    an eight-spike starburst. Additive, so black is transparent."""
    y, x = np.mgrid[0:height, 0:width]
    x = (x + 0.5) / width * 2 - 1
    y = (y + 0.5) / height * 2 - 1
    ax, ay = x, y * 0.25
    r = np.sqrt(ax * ax + ay * ay)
    streak = np.exp(-(y / 0.035) ** 2) * np.exp(-np.abs(x) / 0.32)
    streak += np.exp(-(y / 0.012) ** 2) * np.exp(-np.abs(x) / 0.7) * 0.6
    core = np.exp(-(r / 0.018) ** 2)
    halo = np.exp(-((r - 0.17) / 0.006) ** 2) * 0.35
    theta = np.arctan2(ay, ax)
    spikes = np.abs(np.cos(theta * 4)) ** 60 * np.exp(-r / 0.07) * 0.9
    rgb = WHITE * (core * 1.4 + spikes)[..., None]
    rgb += mix(CYAN, WHITE, 0.35) * streak[..., None]
    rgb += mix(ROYAL, LAVENDER, 0.5) * halo[..., None]
    rgb *= (1 - smoothstep(0.9, 1.0, np.abs(x)))[..., None] * (1 - smoothstep(0.85, 1.0, np.abs(y)))[..., None]
    save(rgb, np.ones((height, width)), "rift_flare.png")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    vortex()
    rim()
    rim(seed=29, suffix="_b")
    abyss()
    nebula()
    sky_veil()
    glow()
    ring()
    crack()
    bloom()
    rays()
