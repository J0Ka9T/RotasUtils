"""Synthesizes every eldritch rift sound (assets/rotasutils/sounds/rift/*.ogg).

    python scripts/gen_rift_sounds.py

No samples: everything is built from oscillators, noise, modal resonators, filters and a convolution
reverb, seeded so a re-run reproduces the same files. Encoding needs ffmpeg with libvorbis on PATH.

Timing that the game relies on (EldritchSkyCinema, opening 14 s / closing 11 s):
  strain  plays at openness 0.30 while opening; its riser peaks 1.65 s later, when the sky splits (0.42).
  collapse plays at 0.80 while closing; it is cut 4.15 s later, when the implosion plays (0.42).
  hum / drone are seamless loops.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources",
                   "assets", "rotasutils", "sounds", "rift")
RNG = np.random.default_rng(1337)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def white(n, rng=RNG):
    return rng.standard_normal(n)


def pink(n, rng=RNG):
    spectrum = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    x = np.fft.irfft(spectrum / np.sqrt(f), n)
    return x / (np.max(np.abs(x)) + 1e-9)


def brown(n, rng=RNG):
    x = np.cumsum(rng.standard_normal(n))
    x = signal.sosfilt(signal.butter(1, 20, "hp", fs=SR, output="sos"), x)
    return x / (np.max(np.abs(x)) + 1e-9)


def filt(x, kind, freq, order=4):
    sos = signal.butter(order, freq, kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def sweep(x, kind, f_start, f_end, curve=1.0, order=2, block=256, q_band=None):
    """Time-varying Butterworth filter, block-wise with carried state. Frequencies glide exponentially."""
    out = np.zeros_like(x)
    n = len(x)
    zi = None
    for start in range(0, n, block):
        frac = (start / max(1, n - 1)) ** curve
        fc = f_start * (f_end / f_start) ** frac
        if kind == "band":
            width = q_band if q_band else 0.5
            freq = [max(20, fc * (1 - width / 2)), min(SR / 2 - 100, fc * (1 + width / 2))]
            sos = signal.butter(order, freq, "bandpass", fs=SR, output="sos")
        else:
            sos = signal.butter(order, min(SR / 2 - 100, max(20, fc)), kind, fs=SR, output="sos")
        if zi is None or zi.shape != (sos.shape[0], 2):
            zi = np.zeros((sos.shape[0], 2))
        out[start:start + block], zi = signal.sosfilt(sos, x[start:start + block], zi=zi)
    return out


def env_exp(t, attack, decay):
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / max(decay, 1e-4))


def env_swell(t, start, peak, end):
    rise = np.clip((t - start) / max(peak - start, 1e-4), 0, 1) ** 2
    fall = 1 - np.clip((t - peak) / max(end - peak, 1e-4), 0, 1)
    return np.where(t < peak, rise, fall ** 1.5)


def glide_sine(t, f0, f1, time_constant, phase=0.0):
    """Sine whose frequency falls or rises exponentially from f0 toward f1."""
    f = f1 + (f0 - f1) * np.exp(-t / time_constant)
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


def modal(t, freqs, decays, amps, rng=RNG):
    """A struck resonant body: a set of damped inharmonic partials - glass, ice, crystal."""
    out = np.zeros_like(t)
    for f, d, a in zip(freqs, decays, amps):
        out += a * np.sin(2 * np.pi * f * t + rng.random() * 6.28) * np.exp(-t / d)
    return out


def saturate(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def ir(seconds, decay, brightness=6000, darkening=0.5, seed=0, predelay=0.02, early=True):
    """Stereo reverb impulse: decaying noise that loses its highs as it decays, plus early reflections."""
    rng = np.random.default_rng(seed)
    t = t_axis(seconds)
    channels = []
    for c in range(2):
        noise = rng.standard_normal(len(t)) * np.exp(-t / decay)
        split = int(len(t) * 0.35)
        bright = filt(noise, "low", brightness, 2)
        dark = filt(noise, "low", brightness * darkening * 0.4, 2)
        blend = np.clip(t / (seconds * 0.6), 0, 1)
        tail = bright * (1 - blend) + dark * blend
        if early:
            for k in range(8):
                pos = int((0.004 + rng.random() * 0.05) * SR)
                if pos < len(tail):
                    tail[pos] += (0.6 - k * 0.06) * (1 if rng.random() > 0.5 else -1)
        pad = np.zeros(int(predelay * SR))
        channels.append(np.concatenate([pad, tail])[:len(t)])
        del split
    stereo = np.stack(channels, axis=1)
    return stereo / np.sqrt(np.sum(stereo ** 2, axis=0, keepdims=True))


def reverb(x, impulse, wet, tail_seconds=0.0):
    """Convolves a mono or stereo signal with a stereo impulse; returns stereo, length + tail."""
    x = stereo(x)
    n = len(x) + int(tail_seconds * SR)
    out = np.zeros((n, 2))
    for c in range(2):
        conv = signal.fftconvolve(x[:, c], impulse[:, c])[:n]
        out[:len(conv), c] += conv * wet
    out[:len(x)] += x * (1 - wet * 0.35)
    return out


def stereo(x, pan=0.0):
    if x.ndim == 2:
        return x
    left = np.cos((pan + 1) * np.pi / 4)
    right = np.sin((pan + 1) * np.pi / 4)
    return np.stack([x * left * 1.414, x * right * 1.414], axis=1)


def widen(x, amount, delay_ms=11, rng=RNG):
    """Decorrelated stereo width from a mono layer: a short, filtered, opposite-polarity side signal."""
    side = np.roll(x, int(delay_ms * SR / 1000))
    side = filt(side, "high", 250, 2)
    return np.stack([x + side * amount, x - side * amount], axis=1)


def autopan(x, rate_hz, depth=1.0, phase=0.0):
    """Circular stereo motion; with an integer number of cycles per loop it loops cleanly."""
    t = np.arange(len(x)) / SR
    pan = depth * np.sin(2 * np.pi * rate_hz * t + phase)
    left = np.cos((pan + 1) * np.pi / 4) * 1.414
    right = np.sin((pan + 1) * np.pi / 4) * 1.414
    return np.stack([x * left, x * right], axis=1)


def fit(x, n):
    x = stereo(x)
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros((n - len(x), 2))])


def taper(x, seconds=0.12):
    """Fades the last moments of a layer so a boom that is still ringing never ends in a click."""
    x = stereo(x).copy()
    k = min(len(x), int(seconds * SR))
    if k > 1:
        x[-k:] *= (np.cos(np.linspace(0, np.pi, k)) * 0.5 + 0.5)[:, None]
    return x


def mixdown(n, *layers):
    out = np.zeros((n, 2))
    for layer, gain, offset in layers:
        layer = taper(layer)
        start = int(offset * SR)
        end = min(n, start + len(layer))
        if end > start:
            out[start:end] += layer[:end - start] * gain
    return out


def finish(x, peak_db=-1.0, fade_in=0.004, fade_out=0.05, limit=1.4):
    x = stereo(x).copy()
    x -= np.mean(x, axis=0, keepdims=True)
    x = filt(x.T, "high", 22, 2).T
    x = saturate(x / (np.max(np.abs(x)) + 1e-9) * limit, 1.3)
    x *= 10 ** (peak_db / 20) / (np.max(np.abs(x)) + 1e-9)
    fi, fo = int(fade_in * SR), int(fade_out * SR)
    if fi:
        x[:fi] *= np.linspace(0, 1, fi)[:, None]
    if fo:
        x[-fo:] *= np.linspace(1, 0, fo)[:, None] ** 2
    return x


def gain_to(x, peak_db):
    """Scales to a peak without touching the waveform's shape, so a folded loop stays seamless."""
    return x * (10 ** (peak_db / 20) / (np.max(np.abs(x)) + 1e-9))


def seamless(x, loop_seconds, crossfade_seconds):
    """Folds the extra tail back over the head with an equal-power crossfade, for a click-free loop."""
    x = stereo(x)
    n = int(loop_seconds * SR)
    c = int(crossfade_seconds * SR)
    x = finish(x, -1.0, fade_in=0, fade_out=0)
    out = x[:n].copy()
    k = np.linspace(0, np.pi / 2, c)[:, None]
    out[:c] = out[:c] * np.sin(k) + x[n:n + c] * np.cos(k)
    return out


def write(name, x, quality=7):
    os.makedirs(OUT, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(data.shape[1])
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    target = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis",
                    "-q:a", str(quality), target], check=True)
    os.remove(path)
    print(f"wrote {name}.ogg  {len(x) / SR:.2f}s  {os.path.getsize(target) // 1024} KB")


def glass_burst(seconds, seed, pitch=1.0, count=34):
    """The instant of fracture: a click and a spray of bright, fast-dying crystal modes."""
    rng = np.random.default_rng(seed)
    t = t_axis(seconds)
    freqs = pitch * np.exp(rng.uniform(np.log(900), np.log(9500), count))
    decays = rng.uniform(0.012, 0.22, count) * (1500 / freqs) ** 0.35
    amps = rng.uniform(0.3, 1.0, count) / np.sqrt(count)
    body = modal(t, freqs, decays, amps, rng)
    click = filt(white(len(t), rng), "high", 2500) * np.exp(-t / 0.004)
    return body + click * 0.8


def crackle_run(seconds, seed, clicks=24, spread=0.8, pitch=1.0, travel=True):
    """A fracture running across the sky: accelerating ticks that pan as they travel."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    out = np.zeros((n, 2))
    times = np.sort(rng.random(clicks) ** 1.6) * spread
    for i, when in enumerate(times):
        length = int(0.06 * SR)
        tt = np.arange(length) / SR
        f = pitch * rng.uniform(1500, 6500)
        tick = np.sin(2 * np.pi * f * tt) * np.exp(-tt / rng.uniform(0.004, 0.02))
        tick += filt(white(length, rng), "high", 3000) * np.exp(-tt / 0.002) * 0.7
        tick *= rng.uniform(0.25, 1.0) * (1 - 0.5 * i / clicks)
        pan = (-0.9 + 1.8 * i / clicks) if travel else rng.uniform(-0.8, 0.8)
        start = int(when * SR)
        end = min(n, start + length)
        out[start:end] += stereo(tick, pan)[:end - start]
    return out


def sub_drop(seconds, f0, f1, time_constant, decay, drive=2.5):
    t = t_axis(seconds)
    boom = glide_sine(t, f0, f1, time_constant) * env_exp(t, 0.004, decay)
    boom += 0.35 * glide_sine(t, f0 * 2, f1 * 2, time_constant) * env_exp(t, 0.002, decay * 0.4)
    return saturate(boom, drive)


def rip(seconds, seed, f_start, f_end, grain_hz=(28, 90)):
    """Torn space: band noise chopped by a jittery, fast amplitude grain, sweeping as it tears."""
    rng = np.random.default_rng(seed)
    t = t_axis(seconds)
    noise = white(len(t), rng)
    rate = rng.uniform(*grain_hz)
    jitter = np.cumsum(rng.uniform(0.6, 1.4, len(t))) / SR
    grain = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * rate * jitter))) * 0.7 + 0.3
    grain = filt(grain, "low", 400, 2)
    band = sweep(noise * grain, "band", f_start, f_end, curve=0.6, q_band=1.2)
    return band * env_exp(t, 0.01, seconds * 0.35)


def choir(seconds, notes, seed, vowel=((700, 1.0), (1220, 0.55), (2600, 0.3)), vibrato=5.2):
    """Detuned saw voices through vowel formants: an unearthly choir pad."""
    rng = np.random.default_rng(seed)
    t = t_axis(seconds)
    voices = np.zeros_like(t)
    for note in notes:
        for _ in range(5):
            detune = 2 ** (rng.normal(0, 0.12) / 12)
            vib = 1 + 0.004 * np.sin(2 * np.pi * (vibrato + rng.normal(0, 0.4)) * t + rng.random() * 6.28)
            phase = np.cumsum(note * detune * vib) / SR
            voices += signal.sawtooth(2 * np.pi * phase + rng.random() * 6.28) / 5
    voices = filt(voices, "low", 4000, 2)
    out = np.zeros_like(t)
    for centre, gain in vowel:
        out += filt(voices, "bandpass", [centre * 0.85, centre * 1.15], 2) * gain
    return out / (np.max(np.abs(out)) + 1e-9)


def omen():
    """Something wrong wakes in the sky: a low swell, a thin ringing, wind that is not wind."""
    seconds = 7.0
    t = t_axis(seconds)
    swell = env_swell(t, 0.0, 3.8, 7.0)
    drone = sum(a * glide_sine(t, f * 0.9, f, 2.5) for f, a in ((36.7, 1.0), (55.0, 0.6), (73.4, 0.35)))
    drone = saturate(drone * 0.5, 1.8) * swell
    ring = sum(np.sin(2 * np.pi * f * t) * (0.5 + 0.5 * np.sin(2 * np.pi * r * t + p))
               for f, r, p in ((2217, 0.31, 0), (2349, 0.23, 2), (3322, 0.41, 4)))
    ring = ring * env_swell(t, 0.8, 4.4, 7.0) * 0.06
    wind = sweep(pink(len(t)), "band", 180, 900, curve=0.8, q_band=0.9) * env_swell(t, 0.3, 4.2, 7.0)
    x = mixdown(len(t), (widen(drone, 0.2), 0.9, 0), (widen(ring, 0.6), 1.0, 0), (autopan(wind, 0.2, 0.7), 0.7, 0))
    x = reverb(x, ir(4.5, 1.4, seed=3), 0.45)
    write("omen", finish(x, -2.0, fade_in=0.3, fade_out=1.0))


def heartbeat(variant):
    """A vast heart behind the sky: lub-dub, felt more than heard."""
    rng = np.random.default_rng(100 + variant)
    seconds = 1.6
    t = t_axis(seconds)

    def thump(pitch, gain):
        body = glide_sine(t, 95 * pitch, 42 * pitch, 0.05) * env_exp(t, 0.003, 0.11)
        skin = filt(white(len(t), rng), "low", 300) * env_exp(t, 0.001, 0.02) * 0.6
        return saturate(body + skin, 2.2) * gain

    x = thump(1.0, 1.0)
    dub = thump(1.12 + variant * 0.03, 0.78)
    offset = int((0.27 + variant * 0.02) * SR)
    x[offset:] += dub[:len(x) - offset]
    x = reverb(widen(x, 0.08), ir(1.2, 0.35, brightness=1800, seed=9), 0.18)
    write(f"heartbeat{variant + 1}", finish(x, -1.0, fade_out=0.2))


def crack(variant):
    """The sky fractures: a glassy snap, the break running across it, a deep settle."""
    seconds = 4.0
    burst = glass_burst(1.5, 200 + variant, pitch=0.7 + variant * 0.12)
    run = crackle_run(1.6, 210 + variant, clicks=30, spread=1.0, pitch=0.8)
    thud = sub_drop(1.5, 110, 38, 0.08, 0.35)
    groan = sweep(brown(int(1.8 * SR)), "low", 700, 120, curve=0.5) * env_exp(t_axis(1.8), 0.05, 0.5)
    x = mixdown(int(seconds * SR), (widen(burst, 0.5), 1.0, 0), (run, 0.7, 0.05), (thud, 0.9, 0),
                (widen(groan, 0.3), 0.5, 0.02))
    x = reverb(x, ir(3.5, 0.9, brightness=8000, seed=11 + variant), 0.4)
    write(f"crack{variant + 1}", finish(x, -1.0, fade_out=0.6))


def strain():
    """The heavens groan under load and wind up toward the split, which lands at 1.65 s."""
    seconds = 2.4
    t = t_axis(seconds)
    climax = 1.65
    build = np.clip(t / climax, 0, 1) ** 2.2
    after = np.exp(-np.maximum(t - climax, 0) / 0.08)
    shape = build * after
    wobble = 1 + 0.03 * np.sin(2 * np.pi * 3.3 * t) + 0.02 * np.sin(2 * np.pi * 7.1 * t)
    index = 1.5 + 5 * build
    modulator = np.sin(2 * np.pi * np.cumsum(105 * wobble) / SR)
    groan = np.sin(2 * np.pi * np.cumsum(70 * wobble) / SR + index * modulator)
    groan = filt(groan, "low", 900) * shape
    rumble = filt(brown(len(t)), "low", 160) * (0.4 + 0.6 * build) * after
    riser = sweep(white(len(t)), "band", 180, 5200, curve=2.2, q_band=0.7) * shape ** 1.5
    whine = np.sin(2 * np.pi * np.cumsum(300 + 1400 * build ** 2) / SR) * shape ** 2 * 0.25
    creaks = np.zeros(len(t))
    rng = np.random.default_rng(301)
    for _ in range(9):
        start = int(rng.uniform(0.1, climax - 0.2) * SR)
        length = int(rng.uniform(0.08, 0.25) * SR)
        tt = np.arange(length) / SR
        f = rng.uniform(260, 620) * (1 + 0.3 * np.sin(2 * np.pi * rng.uniform(8, 25) * tt))
        saw = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR) * np.hanning(length)
        creaks[start:start + length] += filt(saw, "bandpass", [300, 3000], 2) * rng.uniform(0.2, 0.5)
    x = mixdown(len(t), (widen(groan, 0.25), 0.8, 0), (widen(rumble, 0.3), 0.9, 0),
                (autopan(riser, 1.3, 0.6), 0.7, 0), (widen(whine, 0.5), 1.0, 0),
                (autopan(creaks, 0.7, 0.8), 0.6, 0))
    x = reverb(x, ir(2.0, 0.6, seed=13), 0.3)
    write("strain", finish(x, -1.5, fade_in=0.05, fade_out=0.1))


def zap(variant):
    """Sky lightning between the cracks: a buzzing arc that spits and dies."""
    rng = np.random.default_rng(400 + variant)
    seconds = 0.9
    t = t_axis(seconds)
    length = rng.uniform(0.18, 0.4)
    gate = (t < length).astype(float)
    buzz_hz = rng.uniform(95, 150)
    buzz = (0.5 + 0.5 * signal.square(2 * np.pi * buzz_hz * t, duty=0.3)) * 0.6 + 0.4
    stutter = (rng.random(len(t) // 400 + 1) > 0.3).repeat(400)[:len(t)].astype(float)
    stutter = filt(stutter, "low", 900, 1)
    noise = white(len(t), rng)
    arc = filt(noise, "bandpass", [2500, 9000], 2) * buzz * stutter * gate
    snap = filt(noise, "high", 4000) * np.exp(-t / 0.003) * 2
    body = filt(noise, "bandpass", [400, 1200], 2) * buzz * gate * 0.4
    x = (arc + snap + body) * env_exp(t, 0.001, length * 0.8)
    x = reverb(stereo(x, rng.uniform(-0.6, 0.6)), ir(1.2, 0.25, brightness=9000, seed=17 + variant), 0.25)
    write(f"zap{variant + 1}", finish(x, -1.0, fade_out=0.15, limit=2.2))


def split():
    """The tear: snap, rip, a boom you feel in your chest, the shockwave rolling out, then the choir."""
    seconds = 9.0
    n = int(seconds * SR)
    burst = glass_burst(2.0, 501, pitch=0.55, count=48)
    ripped = rip(2.2, 502, 5200, 380)
    boom = sub_drop(5.0, 78, 26, 0.35, 2.4, drive=3.0)
    thunder = filt(brown(int(5 * SR)), "low", 240) * env_exp(t_axis(5), 0.08, 1.5)
    whoosh = sweep(white(int(3.5 * SR)), "low", 9000, 260, curve=0.7) * env_exp(t_axis(3.5), 0.02, 1.1)
    shimmer = choir(7.0, [146.8, 220.0, 293.7, 349.2, 440.0, 659.3], 503)
    shimmer *= env_swell(t_axis(7.0), 0.0, 1.4, 7.0)[:len(shimmer)]
    glassy = sum(np.sin(2 * np.pi * f * t_axis(7.0)) * (0.5 + 0.5 * np.sin(2 * np.pi * r * t_axis(7.0)))
                 for f, r in ((1174.7, 0.5), (1760, 0.33), (2637, 0.7)))
    glassy *= env_swell(t_axis(7.0), 0.3, 2.0, 7.0) * 0.12
    x = mixdown(n, (widen(burst, 0.6), 1.0, 0), (autopan(ripped, 0.6, 0.7), 0.8, 0.0),
                (widen(boom, 0.05), 1.4, 0.0), (widen(thunder, 0.4), 0.7, 0.05),
                (widen(whoosh, 0.9), 0.8, 0.02), (widen(shimmer, 0.7), 0.35, 0.35),
                (widen(glassy, 0.8), 1.0, 0.4))
    x = reverb(x, ir(6.0, 1.9, brightness=7000, seed=19), 0.42)
    write("split", finish(x, -0.8, fade_out=1.5, limit=1.8))


def hum_loop():
    """The open vortex: a deep churning roar that circles the listener. Loops seamlessly at 8 s."""
    loop, fade = 8.0, 1.5
    t = t_axis(loop + fade)
    drone = np.zeros_like(t)
    for f, a, lfo in ((41.25, 1.0, 0.125), (61.875, 0.55, 0.25), (82.5, 0.35, 0.375), (123.75, 0.2, 0.5)):
        drone += a * np.sin(2 * np.pi * f * t) * (0.7 + 0.3 * np.sin(2 * np.pi * lfo * t))
    drone = saturate(drone * 0.6, 2.0)
    churn = pink(len(t))
    centre = 420 * 2 ** (1.1 * np.sin(2 * np.pi * 0.25 * t))
    swirl = np.zeros_like(t)
    block = 512
    zi = None
    for start in range(0, len(t), block):
        fc = centre[start]
        sos = signal.butter(2, [fc * 0.6, fc * 1.6], "bandpass", fs=SR, output="sos")
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        swirl[start:start + block], zi = signal.sosfilt(sos, churn[start:start + block], zi=zi)
    swirl /= np.max(np.abs(swirl)) + 1e-9
    roar = filt(brown(len(t)), "low", 200) * (0.8 + 0.2 * np.sin(2 * np.pi * 0.125 * t))
    x = mixdown(len(t), (widen(drone, 0.1), 0.8, 0), (autopan(swirl, 0.25, 0.9), 0.55, 0),
                (autopan(roar, 0.125, 0.4, 1.3), 0.6, 0))
    x = reverb(x, ir(3.0, 0.9, brightness=3000, seed=23, early=False), 0.3)
    write("hum", gain_to(seamless(x, loop, fade), -3.0))


def drone_loop():
    """The rift singing: a slow, beautiful and wrong cluster high above the roar. Loops at 12 s."""
    loop, fade = 12.0, 2.0
    t = t_axis(loop + fade)
    rng = np.random.default_rng(601)
    notes = [220.0, 261.63, 311.13, 329.63, 440.0, 493.88, 622.25]
    voices = np.zeros_like(t)
    for f in notes:
        for k in range(3):
            freq = round(f * 2 ** ((k - 1) * 0.07 / 12) * loop) / loop
            amp = 0.55 + 0.45 * np.sin(2 * np.pi * (rng.integers(1, 4) / loop) * t + rng.random() * 6.28)
            voices += np.sin(2 * np.pi * freq * t + rng.random() * 6.28) * amp / (1 + f / 400)
    breath = choir(loop + fade, [220.0, 329.63, 440.0], 602, vowel=((400, 1.0), (800, 0.5), (2600, 0.25)))
    glitter = np.zeros_like(t)
    for f in (1760, 2489.0, 3136.0):
        ff = round(f * loop) / loop
        glitter += np.sin(2 * np.pi * ff * t) * np.maximum(0, np.sin(2 * np.pi * (rng.integers(2, 6) / loop) * t)) ** 3
    x = mixdown(len(t), (widen(voices, 0.8), 0.6, 0), (widen(breath, 0.9), 0.3, 0),
                (autopan(glitter, 1 / 6, 0.8), 0.08, 0))
    x = reverb(x, ir(5.0, 1.8, brightness=6000, seed=29, early=False), 0.55)
    write("drone", gain_to(seamless(x, loop, fade), -4.0))


def collapse():
    """The rift drawing everything back in: a reversed storm rising in pitch, cut at 4.15 s by the implosion."""
    seconds = 4.15
    t = t_axis(seconds)
    source = mixdown(int(5 * SR), (widen(glass_burst(2.0, 701, 0.6), 0.6), 0.7, 0),
                     (widen(sub_drop(4.0, 70, 30, 0.3, 1.8), 0.1), 1.0, 0),
                     (widen(filt(brown(int(4 * SR)), "low", 500) * env_exp(t_axis(4), 0.01, 1.3), 0.5), 0.8, 0))
    tail = reverb(source, ir(5.0, 1.6, seed=31), 0.7)
    backwards = tail[::-1][-len(t):]
    suck = sweep(white(len(t)), "band", 150, 4200, curve=2.5, q_band=0.8) * (t / seconds) ** 2.5
    whine = np.sin(2 * np.pi * np.cumsum(180 * 2 ** (2.3 * (t / seconds) ** 2)) / SR) * (t / seconds) ** 3 * 0.35
    x = mixdown(len(t), (backwards, 0.8, 0), (autopan(suck, 0.9, 0.8), 0.6, 0), (widen(whine, 0.6), 1.0, 0))
    write("collapse", finish(x, -1.5, fade_in=0.4, fade_out=0.02))


def implode():
    """The implosion: a gasp inward, a slam, glass falling back into place, and silence rushing out."""
    seconds = 7.0
    n = int(seconds * SR)
    gasp = sweep(white(int(0.35 * SR)), "high", 600, 4000, curve=1.5) * np.linspace(0, 1, int(0.35 * SR)) ** 3
    slam = sub_drop(4.0, 62, 24, 0.12, 1.4, drive=3.5)
    burst = glass_burst(2.0, 801, pitch=0.45, count=40)
    falling = crackle_run(2.5, 802, clicks=40, spread=2.2, pitch=0.6, travel=False)
    vacuum = np.sin(2 * np.pi * np.cumsum(900 * 2 ** (-3 * t_axis(2.5) / 2.5)) / SR) * env_exp(t_axis(2.5), 0.01, 0.8) * 0.3
    debris = filt(brown(int(4 * SR)), "low", 300) * env_exp(t_axis(4), 0.05, 1.0)
    x = mixdown(n, (widen(gasp, 0.7), 0.6, 0), (widen(slam, 0.05), 1.4, 0.33), (widen(burst, 0.5), 0.8, 0.33),
                (falling, 0.5, 0.45), (widen(vacuum, 0.5), 1.0, 0.36), (widen(debris, 0.4), 0.7, 0.36))
    x = reverb(x, ir(5.5, 1.6, brightness=6500, seed=37), 0.4)
    write("implode", finish(x, -0.8, fade_out=1.5, limit=1.8))


def seal():
    """The sky mending: fracture ticks running backwards into a clear chime, and a soft seat."""
    seconds = 4.0
    run = crackle_run(1.2, 901, clicks=26, spread=1.0, pitch=1.1)[::-1]
    t = t_axis(3.0)
    chime = modal(t, [880, 1318.5, 1760, 2637, 3520], [1.4, 1.0, 0.8, 0.5, 0.35], [0.5, 0.4, 0.3, 0.2, 0.12])
    thump = sub_drop(1.2, 90, 45, 0.05, 0.25, drive=1.8)
    x = mixdown(int(seconds * SR), (run, 0.7, 0), (widen(chime, 0.7), 0.8, 1.15), (widen(thump, 0.05), 0.6, 1.15))
    x = reverb(x, ir(3.5, 1.2, brightness=9000, seed=41), 0.45)
    write("seal", finish(x, -2.0, fade_out=0.8))


def vanish():
    """The last of it: an airy exhale and a shimmer thinning to nothing."""
    seconds = 6.0
    t = t_axis(seconds)
    breath = sweep(pink(len(t)), "band", 1200, 180, curve=0.7, q_band=1.0) * env_exp(t, 0.4, 1.6)
    shimmer = sum(np.sin(2 * np.pi * f * t) * np.exp(-t / d) for f, d in ((1760, 2.2), (2217, 1.7), (2637, 1.3)))
    sub = np.sin(2 * np.pi * 36.7 * t) * env_exp(t, 0.2, 1.2)
    x = mixdown(len(t), (autopan(breath, 0.3, 0.8), 0.8, 0), (widen(shimmer, 0.8), 0.1, 0), (widen(sub, 0.05), 0.5, 0))
    x = reverb(x, ir(4.0, 1.4, seed=43), 0.5)
    write("vanish", finish(x, -3.0, fade_in=0.2, fade_out=2.0))


if __name__ == "__main__":
    omen()
    for v in range(2):
        heartbeat(v)
    for v in range(2):
        crack(v)
    strain()
    for v in range(4):
        zap(v)
    split()
    hum_loop()
    drone_loop()
    collapse()
    implode()
    seal()
    vanish()
