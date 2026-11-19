#!/usr/bin/env python3
"""Synthesizes Pingly's original sound pack into android/app/src/main/res/raw.

Every sound is generated from scratch (additive bells, Karplus-Strong plucks, filtered thumps),
so the app ships no third-party audio. Re-run after tweaking:

    python3 branding/tools/generate_sounds.py
"""
import wave
from pathlib import Path

import numpy as np

SR = 32_000
OUT = Path(__file__).resolve().parents[2] / "android/app/src/main/res/raw"
rng = np.random.default_rng(7)


def note(name: str) -> float:
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[pitch] + 12 * (octave - 4)) / 12)


def silence(seconds: float) -> np.ndarray:
    return np.zeros(int(SR * seconds))


def t_axis(seconds: float) -> np.ndarray:
    return np.arange(int(SR * seconds)) / SR


def bell(freq, seconds=1.4, decay=3.0, partials=((1, 1.0), (2.0, 0.35), (2.76, 0.25), (5.4, 0.08)), attack=0.004):
    t = t_axis(seconds)
    sig = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * decay * (1 + 0.6 * i)) for i, (r, a) in enumerate(partials))
    env = np.minimum(1, t / attack)
    return sig * env


def pluck(freq, seconds=2.0, damping=0.996):
    """Karplus-Strong plucked string."""
    n = int(SR * seconds)
    period = int(SR / freq)
    # Low-passed excitation gives a warm nylon-like pluck instead of a bright noise burst.
    buf = np.convolve(rng.uniform(-1, 1, period + 8), np.ones(8) / 8, mode="valid")[:period]
    buf += 0.6 * np.sin(2 * np.pi * np.arange(period) / period)
    out = np.empty(n)
    for i in range(n):
        out[i] = buf[i % period]
        buf[i % period] = damping * 0.5 * (buf[i % period] + buf[(i + 1) % period])
    return out


def mix(length: float, *events):
    """events: (start_seconds, signal, gain)"""
    out = silence(length)
    for start, sig, gain in events:
        s = int(start * SR)
        e = min(len(out), s + len(sig))
        out[s:e] += sig[: e - s] * gain
    return out


def room(sig, delays=(0.031, 0.047, 0.071), feedback=0.28, wet=0.22):
    """A light multi-tap echo so the sounds feel airy instead of dry."""
    out = sig.copy()
    for d in delays:
        n = int(d * SR)
        tap = np.zeros_like(sig)
        tap[n:] = sig[:-n] * feedback
        for _ in range(3):
            tap[n:] += tap[:-n] * feedback
        out += tap * wet
    return out


def finish(sig, fade=0.12, peak=0.89):
    sig = sig - np.mean(sig)
    f = int(fade * SR)
    sig[-f:] *= np.linspace(1, 0, f)
    sig[: int(0.002 * SR)] *= np.linspace(0, 1, int(0.002 * SR))
    return sig / (np.max(np.abs(sig)) + 1e-9) * peak


def write(name, sig):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = (finish(sig) * 32767).astype("<i2")
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"{name}.wav  {len(pcm) / SR:.2f}s")


def sound_ping():
    # Pingly's signature: a bright two-note "pi-ling" with a tiny sparkle on top.
    return room(mix(1.5,
                    (0.00, bell(note("E6"), 1.0, 5.0), 0.8),
                    (0.11, bell(note("B6"), 1.3, 3.6), 1.0),
                    (0.13, bell(note("E7"), 0.6, 9.0), 0.25)))


def sound_chime():
    notes = ["C6", "E6", "G6", "A6", "D7", "C7", "G6", "E7"]
    events = [(0.09 * i + rng.uniform(0, 0.05), bell(note(n), 1.8, 2.2, ((1, 1), (2.76, 0.3), (5.4, 0.1))), rng.uniform(0.5, 1.0))
              for i, n in enumerate(notes)]
    return room(mix(2.6, *events), feedback=0.35, wet=0.3)


def musicbox_tone(freq, seconds=1.1):
    t = t_axis(seconds)
    sig = (np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 3 * t) * np.exp(-t * 8)
           + 0.12 * np.sin(2 * np.pi * freq * 4.2 * t) * np.exp(-t * 14))
    return sig * np.exp(-t * 4.2) * np.minimum(1, t / 0.002)


def sound_musicbox():
    melody = [("G5", 0), ("C6", 1), ("E6", 2), ("D6", 3), ("C6", 4), ("E6", 5.5), ("G6", 6), ("A6", 7), ("G6", 8.5)]
    beat = 0.2
    events = [(b * beat, musicbox_tone(note(n)), 1.0) for n, b in melody]
    events += [(b * beat, musicbox_tone(note(n) / 2) * 0.35, 1.0) for n, b in melody[::3]]
    return room(mix(2.9, *events), feedback=0.3, wet=0.25)


def pop(f0, f1, seconds=0.07):
    t = t_axis(seconds)
    freq = np.linspace(f0, f1, len(t))
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase) * np.exp(-t * 45) * np.minimum(1, t / 0.003)


def sound_bubbles():
    events, time = [], 0.0
    for _ in range(11):
        base = rng.uniform(260, 520)
        events.append((time, pop(base, base * rng.uniform(2.2, 3.4)), rng.uniform(0.6, 1.0)))
        time += rng.uniform(0.06, 0.16)
    return room(mix(time + 0.4, *events), feedback=0.2, wet=0.15)


def sound_bells():
    partials = ((0.5, 0.6), (1, 1.0), (1.183, 0.5), (1.506, 0.35), (2.0, 0.25), (2.514, 0.15), (2.662, 0.1))
    return room(mix(3.2,
                    (0.0, bell(note("A5"), 2.6, 1.6, partials), 1.0),
                    (0.55, bell(note("E5"), 2.6, 1.6, partials), 0.85)), feedback=0.38, wet=0.32)


def thump(seconds=0.35, f0=95, f1=48):
    t = t_axis(seconds)
    freq = np.linspace(f0, f1, len(t))
    body = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t * 14)
    click = np.convolve(rng.normal(0, 1, len(t)), np.ones(24) / 24, mode="same") * np.exp(-t * 200) * 0.25
    return (body + click) * np.minimum(1, t / 0.004)


def sound_heartbeat():
    events = []
    for i in range(3):
        start = i * 0.86
        events += [(start, thump(), 1.0), (start + 0.22, thump(0.3, 85, 45), 0.7)]
    return mix(2.9, *events)


def sound_sparkle():
    scale = ["C6", "D6", "E6", "G6", "A6", "C7", "D7", "E7", "G7", "A7", "C8"]
    events = [(i * 0.045, bell(note(n), 0.9, 6.0, ((1, 1), (2.0, 0.2))), 0.55 + 0.04 * i) for i, n in enumerate(scale)]
    return room(mix(1.9, *events), feedback=0.4, wet=0.35)


def sound_harp():
    chord = ["C4", "E4", "G4", "C5", "E5", "G5", "C6", "E6"]
    events = [(i * 0.085, pluck(note(n), 2.2), 0.9) for i, n in enumerate(chord)]
    return room(mix(2.9, *events), feedback=0.3, wet=0.25)


if __name__ == "__main__":
    for name, fn in {
        "sound_ping": sound_ping,
        "sound_chime": sound_chime,
        "sound_musicbox": sound_musicbox,
        "sound_bubbles": sound_bubbles,
        "sound_bells": sound_bells,
        "sound_heartbeat": sound_heartbeat,
        "sound_sparkle": sound_sparkle,
        "sound_harp": sound_harp,
    }.items():
        write(name, fn())
