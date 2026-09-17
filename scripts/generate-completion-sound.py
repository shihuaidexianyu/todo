"""Soft bell 'done' cue; no external samples. Rebuild with Python 3."""
import math
import struct
import wave
from pathlib import Path

rate = 48000
duration = 0.22
samples = []
for i in range(round(rate * duration)):
    t = i / rate
    attack = min(1.0, t / 0.0006)
    tail = min(1.0, (duration - t) / 0.03)
    # Warm struck-bell partials: a low fundamental with soft, quickly-decaying overtones.
    value = sum(amplitude * math.exp(-t / decay) * math.sin(2 * math.pi * frequency * t)
                for frequency, amplitude, decay in [
                    (880.0, 1.00, 0.090),
                    (1320.0, 0.42, 0.055),
                    (1760.0, 0.18, 0.035),
                    (2640.0, 0.08, 0.020),
                ])
    samples.append(value * attack * tail)
peak = max(map(abs, samples))
path = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/complete.wav'
path.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(path), 'wb') as output:
    output.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
    output.writeframes(b''.join(struct.pack('<h', round(s / peak * 24000)) for s in samples))
print(f'{path.name}: {duration * 1000:.0f} ms, mono PCM 48 kHz, peak -2.7 dBFS')
