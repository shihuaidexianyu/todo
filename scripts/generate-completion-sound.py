"""Original short glass/pluck cue; no external samples. Rebuild with Python 3."""
import math
import struct
import wave
from pathlib import Path

rate = 48000
duration = 0.165
samples = []
for i in range(round(rate * duration)):
    t = i / rate
    attack = min(1.0, t / 0.0008)
    tail = min(1.0, (duration - t) / 0.012)
    # Bright fundamental with quickly decaying, slightly inharmonic glass partials.
    value = sum(amplitude * math.exp(-t / decay) * math.sin(2 * math.pi * frequency * t)
                for frequency, amplitude, decay in [(2349, 1, .036), (4757, .36, .019), (7105, .12, .009)])
    samples.append(value * attack * tail)
peak = max(map(abs, samples))
path = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/complete.wav'
path.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(path), 'wb') as output:
    output.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
    output.writeframes(b''.join(struct.pack('<h', round(s / peak * 24000)) for s in samples))
print(f'{path.name}: {duration * 1000:.0f} ms, mono PCM 48 kHz, peak -2.7 dBFS')
