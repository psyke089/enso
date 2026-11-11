"""Generate Enso's original, offline 12 ms damped wooden selection click (no samples)."""
from pathlib import Path
import math
import random
import struct
import wave

destination = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/adjustment_tick.wav"
rate = 44_100
noise = random.Random(1138)
samples = []
for index in range(round(rate * .012)):
    time = index / rate
    envelope = min(time / .0003, 1) * math.exp(-time * 650)
    signal = .7 * math.sin(2 * math.pi * 1800 * time) + .3 * noise.uniform(-1, 1)
    samples.append(round(signal * envelope * .6 * 32767))
with wave.open(str(destination), "wb") as output:
    output.setparams((1, 2, rate, 0, "NONE", "not compressed"))
    output.writeframes(struct.pack(f"<{len(samples)}h", *samples))
