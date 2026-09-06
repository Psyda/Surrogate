#!/usr/bin/env python3
"""Synthesizes the sound effects for the cinematic layer and writes sounds.json.

Run from the mod root:  python3 tools/gen_sounds.py
Requires numpy, scipy and an ffmpeg on the PATH.  Output goes to src/main/resources/assets/surrogate/sounds.

Every effect is a placeholder built from noise and sine waves. To replace one with a real recording, drop an
.ogg with the same name over the generated file and run this script again (it only regenerates files that are
missing, unless --force is given). Recorded voice lines go in sounds/voice/<line>.ogg, named after the lang key
with its "<type>.surrogate." prefix removed, for example voice/prologue.wake1.ogg or voice/okafor.radio.1.ogg;
this script lists whatever it finds there in sounds.json and the game plays them under the subtitles.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "surrogate")
SOUNDS = os.path.join(ROOT, "sounds")
RATE = 44100
FORCE = "--force" in sys.argv
rng = np.random.default_rng(1207)


# --------------------------------------------------------------------------------------
# Building blocks
# --------------------------------------------------------------------------------------
def seconds(n):
    return int(n * RATE)


def t(n):
    return np.arange(n) / RATE


def white(n):
    return rng.standard_normal(n)


def lowpass(x, cutoff):
    """One pole IIR low pass."""
    a = np.exp(-2.0 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    b = 1.0 - a
    for i in range(len(x)):
        acc = b * x[i] + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, lo, hi):
    return highpass(lowpass(x, hi), lo)


def env(n, attack, release, hold=None):
    """Linear attack, flat, linear release. Times in seconds."""
    e = np.ones(n)
    a = min(n, seconds(attack))
    r = min(n, seconds(release))
    if a > 0:
        e[:a] = np.linspace(0.0, 1.0, a)
    if r > 0:
        e[n - r:] = np.minimum(e[n - r:], np.linspace(1.0, 0.0, r))
    return e


def decay(n, tau):
    return np.exp(-t(n) / tau)


def sine(freq, n, phase=0.0):
    return np.sin(2.0 * np.pi * freq * t(n) + phase)


def sweep(f0, f1, n):
    """Sine whose frequency glides exponentially from f0 to f1."""
    tt = t(n)
    k = np.log(f1 / f0) / tt[-1]
    phase = 2.0 * np.pi * f0 * (np.exp(k * tt) - 1.0) / k
    return np.sin(phase)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def normalize(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x * (peak / m)


def save(name, samples):
    path = os.path.join(SOUNDS, name + ".ogg")
    if os.path.exists(path) and not FORCE:
        print("keeping", os.path.relpath(path, ROOT))
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.clip(normalize(samples), -1.0, 1.0)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        wavfile.write(wav, RATE, (data * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    print("wrote", os.path.relpath(path, ROOT))


# --------------------------------------------------------------------------------------
# The effects
# --------------------------------------------------------------------------------------
def title():
    n = seconds(5.0)
    drone = 0.5 * sine(55.0, n) + 0.35 * sine(82.4, n) + 0.25 * sine(110.5, n) + 0.15 * sine(164.8, n)
    wash = 0.25 * bandpass(white(n), 200, 900)
    swell = env(n, 2.6, 2.2)
    return (drone + wash * np.linspace(0.2, 1.0, n)) * swell


def radio_open():
    n = seconds(0.24)
    burst = bandpass(white(n), 900, 3200) * decay(n, 0.05)
    blip = 0.6 * sine(1400.0, seconds(0.04)) * env(seconds(0.04), 0.004, 0.02)
    return mix(burst, blip)


def radio_close():
    n = seconds(0.2)
    blip = 0.5 * sine(950.0, seconds(0.035)) * env(seconds(0.035), 0.004, 0.02)
    tail = bandpass(white(n), 700, 2600) * decay(n, 0.045) * 0.7
    return mix(blip, tail)


def static():
    n = seconds(2.2)
    hiss = bandpass(white(n), 300, 4200)
    gate = lowpass(np.abs(white(n)), 18.0)
    gate = gate / (np.max(gate) + 1e-9)
    crackle = bandpass(white(n), 1500, 6000) * (rng.random(n) > 0.995) * 3.0
    return (hiss * (0.5 + 0.8 * gate) + crackle) * env(n, 0.01, 1.6)


def objective():
    a = seconds(0.16)
    b = seconds(0.34)
    first = (sine(659.3, a) + 0.3 * sine(1318.5, a)) * env(a, 0.01, 0.08)
    second = (sine(987.8, b) + 0.3 * sine(1975.5, b)) * env(b, 0.01, 0.25)
    return np.concatenate([first, second]) * 0.8


def quake():
    n = seconds(6.5)
    brown = np.cumsum(white(n))
    brown = highpass(brown, 4.0)
    rumble = lowpass(brown, 110.0)
    rumble = rumble / (np.max(np.abs(rumble)) + 1e-9)
    sub = 0.6 * sine(31.0, n) * (0.6 + 0.4 * sine(0.45, n))
    shape = np.concatenate([np.linspace(0.0, 0.5, seconds(0.9)), np.full(n - seconds(0.9), 0.5)])
    hit = seconds(1.25)
    shape[hit:] = np.maximum(shape[hit:], decay(n - hit, 2.4))
    shape *= env(n, 0.05, 1.5)
    return (rumble + sub) * shape


def breach():
    n = seconds(1.9)
    boom = lowpass(white(n), 420.0) * decay(n, 0.42)
    boom = boom / (np.max(np.abs(boom)) + 1e-9) * 1.4
    sub = 0.9 * sweep(70.0, 28.0, seconds(0.6)) * env(seconds(0.6), 0.005, 0.4)
    debris = bandpass(white(n), 1800, 6500) * (rng.random(n) > 0.985) * 4.0 * decay(n, 0.7)
    shards = bandpass(white(seconds(1.2)), 3500, 9000) * decay(seconds(1.2), 0.25) * 0.5
    return mix(boom, sub, debris, shards)


def alarm():
    note = seconds(0.4)
    tones = []
    for i in range(8):
        freq = 620.0 if i % 2 == 0 else 470.0
        s = sine(freq, note) + 0.3 * sine(freq * 3, note) + 0.12 * sine(freq * 5, note)
        tones.append(s * env(note, 0.03, 0.05))
    out = np.concatenate(tones)
    return out * env(len(out), 0.01, 0.3) * 0.7


def signal_lost():
    a = seconds(0.95)
    fall = sweep(900.0, 170.0, a) * (1.0 + 0.04 * sine(9.0, a)) * env(a, 0.01, 0.02)
    gap = np.zeros(seconds(0.06))
    b = seconds(0.45)
    burst = bandpass(white(b), 400, 5000) * env(b, 0.005, 0.02)
    return np.concatenate([fall, gap, burst * 0.9])


EFFECTS = {
    "cinematic/title": title,
    "cinematic/radio_open": radio_open,
    "cinematic/radio_close": radio_close,
    "cinematic/static": static,
    "cinematic/objective": objective,
    "prologue/quake": quake,
    "prologue/breach": breach,
    "prologue/alarm": alarm,
    "prologue/signal_lost": signal_lost,
}

# Event name -> (file, subtitle key, stream)
EVENTS = {
    "cinematic.title": ("cinematic/title", "subtitles.surrogate.title", True),
    "cinematic.radio_open": ("cinematic/radio_open", "subtitles.surrogate.radio", False),
    "cinematic.radio_close": ("cinematic/radio_close", "subtitles.surrogate.radio", False),
    "cinematic.static": ("cinematic/static", "subtitles.surrogate.static", False),
    "cinematic.objective": ("cinematic/objective", "subtitles.surrogate.objective", False),
    "prologue.quake": ("prologue/quake", "subtitles.surrogate.quake", True),
    "prologue.breach": ("prologue/breach", "subtitles.surrogate.breach", False),
    "prologue.alarm": ("prologue/alarm", "subtitles.surrogate.alarm", False),
    "prologue.signal_lost": ("prologue/signal_lost", "subtitles.surrogate.signal_lost", False),
}


# --------------------------------------------------------------------------------------
# The ship
# --------------------------------------------------------------------------------------
def loopify(x, fade):
    """Cross-fades the head into the tail so the sample repeats without a click."""
    f = seconds(fade)
    ramp = np.linspace(0.0, 1.0, f)
    out = x[f:].copy()
    out[-f:] = x[-f:] * (1.0 - ramp) + x[:f] * ramp
    return out


def hum():
    n = seconds(6.0)
    base = 0.5 * sine(48.0, n) + 0.3 * sine(96.0, n) + 0.12 * sine(144.0, n)
    air = 0.22 * lowpass(white(n), 380.0)
    wobble = 1.0 + 0.08 * sine(0.5, n)
    return loopify(base * wobble + air, 0.5)


def klaxon():
    note = seconds(0.5)
    parts = []
    for i in range(4):
        freq = 640.0 if i % 2 == 0 else 470.0
        s = sine(freq, note) + 0.35 * sine(freq * 2, note) + 0.15 * sine(freq * 3, note)
        parts.append(s * env(note, 0.02, 0.08))
    out = np.concatenate(parts)
    return loopify(out, 0.05) * 0.8


def engine_cut():
    n = seconds(3.2)
    base = 0.5 * sweep(48.0, 14.0, n) + 0.3 * sweep(96.0, 28.0, n) + 0.2 * lowpass(white(n), 300.0)
    fall = np.linspace(1.0, 0.0, n) ** 1.6
    clank = bandpass(white(seconds(0.25)), 900, 3000) * decay(seconds(0.25), 0.06)
    out = base * fall
    start = seconds(2.6)
    out[start:start + len(clank)] += 0.5 * clank
    return out


def engine_relight():
    n = seconds(3.6)
    brown = highpass(np.cumsum(white(n)), 5.0)
    rumble = lowpass(brown, 90.0)
    rumble = rumble / (np.max(np.abs(rumble)) + 1e-9)
    ramp = np.concatenate([np.zeros(seconds(0.5)), np.linspace(0.0, 1.0, seconds(0.7)), np.ones(n)])[:n]
    boom = np.zeros(n)
    b = seconds(0.9)
    boom[seconds(0.8):seconds(0.8) + b] = sweep(70.0, 26.0, b) * env(b, 0.005, 0.6)
    hum_in = (0.5 * sine(48.0, n) + 0.3 * sine(96.0, n)) * np.linspace(0.0, 1.0, n)
    return (rumble * ramp + boom * 1.2 + hum_in * 0.6) * env(n, 0.01, 0.4)


def thaw():
    n = seconds(4.6)
    out = 0.12 * lowpass(white(n), 600.0) * np.linspace(0.2, 1.0, n)
    blip = sine(880.0, seconds(0.08)) * env(seconds(0.08), 0.005, 0.03)
    for i in range(5):
        start = seconds(0.4 + i * 0.85)
        out[start:start + len(blip)] += 0.7 * blip
    pad = 0.25 * (sine(220.0, n) + 0.5 * sine(330.0, n)) * np.linspace(0.0, 1.0, n) ** 2
    return out + pad * env(n, 1.0, 0.8)


def flatline():
    n = seconds(3.4)
    out = np.zeros(n)
    beep = sine(1000.0, seconds(0.07)) * env(seconds(0.07), 0.004, 0.02)
    for i in range(4):
        start = seconds(0.1 + i * 0.3)
        out[start:start + len(beep)] += 0.8 * beep
    tone_start = seconds(1.5)
    tone = sine(1000.0, n - tone_start) * env(n - tone_start, 0.01, 0.9)
    out[tone_start:] += 0.7 * tone
    return out


def intercom():
    a = seconds(0.13)
    b = seconds(0.22)
    first = (sine(660.0, a) + 0.25 * sine(1320.0, a)) * env(a, 0.01, 0.05)
    second = (sine(880.0, b) + 0.25 * sine(1760.0, b)) * env(b, 0.01, 0.14)
    return np.concatenate([first, second]) * 0.7


def hatch():
    n = seconds(1.5)
    hiss = bandpass(white(n), 1500, 5500) * decay(n, 0.35) * 0.6
    clunk = np.zeros(n)
    c = seconds(0.3)
    clunk[seconds(0.75):seconds(0.75) + c] = lowpass(white(c), 160.0) * decay(c, 0.07)
    clunk = clunk / (np.max(np.abs(clunk)) + 1e-9)
    return mix(hiss, clunk * 1.1)


def decompress():
    n = seconds(3.6)
    bang = lowpass(white(seconds(0.5)), 320.0) * decay(seconds(0.5), 0.12)
    bang = bang / (np.max(np.abs(bang)) + 1e-9) * 1.3
    hiss = bandpass(white(n), 700, 6500) * decay(n, 1.4)
    return mix(bang, hiss * 0.8)


def bleed():
    n = seconds(1.7)
    warble = sine(420.0, n) * (0.5 + 0.5 * sine(7.0, n))
    static_burst = bandpass(white(n), 500, 5000) * (0.4 + 0.6 * (rng.random(n) > 0.6))
    gate = lowpass(np.abs(white(n)), 9.0)
    gate = gate / (np.max(gate) + 1e-9)
    return (warble * 0.5 + static_burst * gate) * env(n, 0.02, 0.3)


def descent():
    n = seconds(9.0)
    brown = highpass(np.cumsum(white(n)), 4.0)
    rumble = lowpass(brown, 70.0)
    rumble = rumble / (np.max(np.abs(rumble)) + 1e-9)
    wind = bandpass(white(n), 300, 1600) * np.linspace(0.1, 1.0, n) ** 2
    ramp = np.linspace(0.2, 1.0, n)
    return (rumble * ramp + wind * 0.5) * env(n, 0.5, 1.5)


def sedate():
    n = seconds(5.0)
    out = 0.35 * sine(55.0, n) * np.linspace(1.0, 0.0, n)
    thump = lowpass(white(seconds(0.18)), 120.0) * decay(seconds(0.18), 0.04)
    thump = thump / (np.max(np.abs(thump)) + 1e-9)
    for t in (0.0, 0.25, 1.1, 1.4, 2.5, 2.85, 4.2):
        start = seconds(t)
        end = min(n, start + len(thump))
        out[start:end] += 0.8 * thump[:end - start] * (1.0 - t / 6.0)
    return out


EFFECTS.update({
    "transit/hum": hum,
    "transit/klaxon": klaxon,
    "transit/engine_cut": engine_cut,
    "transit/engine_relight": engine_relight,
    "transit/thaw": thaw,
    "transit/flatline": flatline,
    "transit/intercom": intercom,
    "transit/hatch": hatch,
    "transit/decompress": decompress,
    "transit/bleed": bleed,
    "transit/descent": descent,
    "transit/sedate": sedate,
})

EVENTS.update({
    "transit.hum": ("transit/hum", "subtitles.surrogate.hum", True),
    "transit.klaxon": ("transit/klaxon", "subtitles.surrogate.klaxon", True),
    "transit.engine_cut": ("transit/engine_cut", "subtitles.surrogate.engine_cut", False),
    "transit.engine_relight": ("transit/engine_relight", "subtitles.surrogate.engine_relight", False),
    "transit.thaw": ("transit/thaw", "subtitles.surrogate.thaw", False),
    "transit.flatline": ("transit/flatline", "subtitles.surrogate.flatline", False),
    "transit.intercom": ("transit/intercom", "subtitles.surrogate.intercom", False),
    "transit.hatch": ("transit/hatch", "subtitles.surrogate.hatch", False),
    "transit.decompress": ("transit/decompress", "subtitles.surrogate.decompress", False),
    "transit.bleed": ("transit/bleed", "subtitles.surrogate.bleed", False),
    "transit.descent": ("transit/descent", "subtitles.surrogate.descent", True),
    "transit.sedate": ("transit/sedate", "subtitles.surrogate.sedate", False),
})



def microwave_hum():
    n = seconds(1.0)
    buzz = 0.5 * sine(60.0, n) + 0.25 * sine(120.0, n) + 0.12 * sine(180.0, n)
    whine = 0.08 * sine(2400.0, n) * (1.0 + 0.3 * sine(7.0, n))
    fan = 0.12 * lowpass(white(n), 900.0)
    return loopify(buzz + whine + fan, 0.05) * 0.8


def microwave_ding():
    n = seconds(1.4)
    bell = sine(2093.0, n) + 0.5 * sine(2637.0, n) + 0.25 * sine(4186.0, n)
    return bell * decay(n, 0.35) * 0.8


EFFECTS.update({
    "block/microwave_hum": microwave_hum,
    "block/microwave_ding": microwave_ding,
})

EVENTS.update({
    "block.microwave_hum": ("block/microwave_hum", "subtitles.surrogate.microwave_hum", False),
    "block.microwave_ding": ("block/microwave_ding", "subtitles.surrogate.microwave_ding", False),
})


# The crawler's docking console.
def dock_beep():
    """A short, clicky range beep: a tick of noise and a high blip."""
    n = seconds(0.06)
    click = 0.5 * bandpass(white(n), 2500, 6000) * decay(n, 0.006)
    blip = sine(1500.0, n) * env(n, 0.003, 0.03)
    return mix(click, 0.8 * blip)


def dock_lock():
    """The lock going home: a heavy thunk, then a rising two-note confirm."""
    n = seconds(1.1)
    thunk = lowpass(white(seconds(0.25)), 180.0) * decay(seconds(0.25), 0.06)
    body = 0.6 * sine(70.0, seconds(0.25)) * decay(seconds(0.25), 0.08)
    a = sine(880.0, seconds(0.16)) * env(seconds(0.16), 0.005, 0.06)
    b = sine(1318.5, seconds(0.5)) * env(seconds(0.5), 0.005, 0.3)
    out = np.zeros(n)
    out[: len(thunk)] += 1.2 * thunk + body
    start = seconds(0.22)
    out[start:start + len(a)] += 0.6 * a
    start = seconds(0.4)
    out[start:start + len(b)] += 0.6 * b
    return out


def dock_error():
    """A clunk off the collar and a flat buzz: not on it."""
    n = seconds(0.6)
    clunk = lowpass(white(seconds(0.2)), 400.0) * decay(seconds(0.2), 0.04)
    buzz = (sine(220.0, seconds(0.35)) + 0.5 * sine(233.0, seconds(0.35))) * env(seconds(0.35), 0.01, 0.1)
    out = np.zeros(n)
    out[: len(clunk)] += 1.0 * clunk
    start = seconds(0.12)
    out[start:start + len(buzz)] += 0.5 * buzz
    return out


EFFECTS.update({
    "crawler/dock_beep": dock_beep,
    "crawler/dock_lock": dock_lock,
    "crawler/dock_error": dock_error,
})

EVENTS.update({
    "crawler.dock_beep": ("crawler/dock_beep", "subtitles.surrogate.dock_beep", False),
    "crawler.dock_lock": ("crawler/dock_lock", "subtitles.surrogate.dock_lock", False),
    "crawler.dock_error": ("crawler/dock_error", "subtitles.surrogate.dock_error", False),
})


def main():
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found on PATH", file=sys.stderr)
        sys.exit(1)
    for name, make in EFFECTS.items():
        save(name, make())

    sounds = {}
    for event, (file, subtitle, stream) in EVENTS.items():
        entry = {"name": "surrogate:" + file}
        if stream:
            entry["stream"] = True
        sounds[event] = {"sounds": [entry], "subtitle": subtitle}

    voice_dir = os.path.join(SOUNDS, "voice")
    if os.path.isdir(voice_dir):
        for file in sorted(os.listdir(voice_dir)):
            if not file.endswith(".ogg"):
                continue
            line = file[:-4]
            sounds["voice." + line] = {"sounds": [{"name": "surrogate:voice/" + line, "stream": True}]}
            print("voice line", line)

    path = os.path.join(ROOT, "sounds.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    print("wrote", os.path.relpath(path, ROOT), "with", len(sounds), "events")


if __name__ == "__main__":
    main()
