"""Paints the Celestial school's energy textures (white RGB, shape in alpha; colour comes from the vertex).

    python scripts/gen_celestial_textures.py [--preview out.png]

* streak.png  512x64  - flowing wisps along U, tiles seamlessly in U (scrolled along beams and pillars),
                         fades to nothing at both long edges.
* nebula.png  256x256 - soft cloudy energy, tiles in both directions (scrolled up the light pillar).
* shock.png   512x512 - a shock ring: crisp bright leading edge, soft trailing falloff inside.
* cloud.png   256x256 - a free-floating puff of energy: nebula masked by a soft, uneven round falloff.

Noise is made by filtering white noise in the frequency domain, which is periodic by construction,
so every texture tiles without seams. Fixed seed: re-running produces the same files.
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources",
                   "assets", "rotasutils", "textures", "vfx", "celestial")
RNG = np.random.default_rng(7)


def periodic_noise(h, w, beta=2.2, stretch_x=1.0):
    """Tileable 1/f^beta noise; stretch_x > 1 suppresses change along x, so features run long along x."""
    white = RNG.standard_normal((h, w))
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :] * stretch_x
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    field = np.real(np.fft.ifft2(np.fft.fft2(white) / f ** (beta / 2)))
    field -= field.min()
    return field / field.max()


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def save(name, alpha):
    alpha = np.clip(alpha, 0, 1)
    rgba = np.zeros(alpha.shape + (4,), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = (alpha * 255 + 0.5).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(os.path.join(OUT, name))
    return alpha


def stretch_x(field, w):
    """Band-limited upsample along x (zero-padding in the frequency domain): smooth, still tileable."""
    h, n = field.shape
    spec = np.fft.fft(field, axis=1)
    padded = np.zeros((h, w), complex)
    half = n // 2
    padded[:, :half] = spec[:, :half]
    padded[:, -half:] = spec[:, -half:]
    out = np.real(np.fft.ifft(padded, axis=1))
    out -= out.min()
    return out / out.max()


def streak():
    h, w = 64, 512
    y = np.linspace(0, 1, h)[:, None]
    # Noise on a short grid, stretched 16x along U: long flowing wisps, varied across V.
    wisps = stretch_x(periodic_noise(h, 32, beta=2.0), w)
    fine = stretch_x(periodic_noise(h, 64, beta=1.6), w)
    body = smoothstep(0.45, 0.95, wisps) * 0.85 + smoothstep(0.6, 1.0, fine) * 0.35
    core = np.exp(-((y - 0.5) / 0.16) ** 2)                   # brighter down the middle
    edge = smoothstep(0.0, 0.3, y) * smoothstep(0.0, 0.3, 1 - y)
    return save("streak.png", (body * (0.55 + 0.45 * core) + 0.12 * core) * edge)


def nebula():
    n = 256
    cloud = periodic_noise(n, n, beta=3.4)
    detail = periodic_noise(n, n, beta=2.4)
    return save("nebula.png", smoothstep(0.3, 0.95, cloud) * (0.8 + 0.2 * detail))


def shock():
    n = 512
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.sqrt(xx * xx + yy * yy)
    ring_r = 0.86
    lead = np.exp(-((r - ring_r) / 0.012) ** 2)                # crisp leading edge
    trail = np.where(r < ring_r, np.exp(-((ring_r - r) / 0.16) ** 2) * 0.55, 0)
    ang = np.arctan2(yy, xx)
    ripples = 0.92 + 0.08 * np.sin(ang * 9 + 0.7) * np.sin(ang * 4 + 1.3)
    return save("shock.png", (lead + trail) * ripples * smoothstep(1.0, 0.95, r))


def cloud():
    n = 256
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.sqrt(xx * xx + yy * yy)
    ang = np.arctan2(yy, xx)
    # An uneven silhouette: the falloff radius wobbles round the edge, so it never reads as a circle.
    wobble = 0.78 + 0.1 * np.sin(ang * 3 + 0.4) + 0.06 * np.sin(ang * 5 + 2.1)
    mask = smoothstep(wobble, wobble * 0.35, r)
    body = periodic_noise(n, n, beta=3.0)
    core = np.clip(1 - r, 0, 1)  # denser in the middle, so the puff stays centred
    return save("cloud.png", mask * (0.3 + 0.45 * smoothstep(0.2, 0.85, body) + 0.35 * core))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    maps = [streak(), nebula(), shock(), cloud()]
    if "--preview" in sys.argv:
        tiles = []
        for a in maps:
            img = Image.new("RGBA", a.shape[::-1], (40, 18, 64, 255))
            layer = Image.fromarray(np.dstack([np.full(a.shape, 255), np.full(a.shape, 255),
                                               np.full(a.shape, 255), a * 255]).astype(np.uint8), "RGBA")
            img.alpha_composite(layer)
            tiles.append(img.resize((256, 256)))
        sheet = Image.new("RGBA", (256 * len(tiles), 256))
        for i, t in enumerate(tiles):
            sheet.paste(t, (256 * i, 0))
        sheet.save(sys.argv[sys.argv.index("--preview") + 1])
    print("celestial textures written")
