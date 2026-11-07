#!/usr/bin/env python3
"""Generates the bundled soft chimes: python3 tools/gen_sounds.py app/src/main/res/raw"""
import math, struct, wave, sys
SR = 44100
def note(freq, dur, amp=0.5, decay=6.0):
    n = int(SR * dur); out = []
    for i in range(n):
        t = i / SR
        env = min(1.0, t / 0.008) * math.exp(-decay * t)       # soft attack, bell-like decay
        s = math.sin(2*math.pi*freq*t) + 0.25*math.sin(2*math.pi*2*freq*t)*math.exp(-4*t) \
            + 0.08*math.sin(2*math.pi*3*freq*t)*math.exp(-8*t)
        out.append(amp * env * s / 1.33)
    return out
def mix(parts, total):
    buf = [0.0] * int(SR * total)
    for start, samples in parts:
        o = int(SR * start)
        for i, s in enumerate(samples):
            if o + i < len(buf): buf[o + i] += s
    # fade out last 30 ms to avoid clicks
    f = int(SR * 0.03)
    for i in range(f): buf[-1 - i] *= i / f
    return buf
def write(path, buf):
    peak = max(abs(x) for x in buf) or 1
    g = 0.7 / peak
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(x * g * 32767)) for x in buf))
d = sys.argv[1]
# Green: calm rising major third + fifth (C5 E5 G5 arpeggio) = "all good"
write(f"{d}/alert_green.wav", mix([(0.0, note(523.25, 0.9, decay=5)), (0.13, note(659.25, 0.9, decay=5)),
                                   (0.26, note(783.99, 1.0, decay=4.5))], 1.2))
# Yellow: two equal mid notes (A4 A4) = "attention"
write(f"{d}/alert_yellow.wav", mix([(0.0, note(440.0, 0.5, decay=7)), (0.28, note(440.0, 0.7, decay=6))], 1.0))
# Red: three descending notes (E5 C5 A4) = "danger", then 1.2 s silence so a looped rule chimes periodically
write(f"{d}/alert_red.wav", mix([(0.0, note(659.25, 0.4, decay=8)), (0.2, note(523.25, 0.4, decay=8)),
                                 (0.4, note(440.0, 0.8, decay=5))], 2.4))
