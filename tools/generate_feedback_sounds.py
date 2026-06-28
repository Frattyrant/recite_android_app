from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

RATE = 22_050
OUT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "raw"


def write_tone(path: Path, notes: list[tuple[float, float]], volume: float = 0.75) -> None:
    frames: list[int] = []
    for frequency, duration in notes:
        count = int(RATE * duration)
        for index in range(count):
            envelope = min(1.0, index / (RATE * 0.012))
            envelope *= max(0.0, 1.0 - index / count)
            sample = math.sin(2 * math.pi * frequency * index / RATE)
            frames.append(int(32767 * volume * envelope * sample))
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(b"".join(struct.pack("<h", sample) for sample in frames))


write_tone(OUT / "answer_correct.wav", [(660, 0.09), (880, 0.13)])
write_tone(OUT / "answer_wrong.wav", [(260, 0.18)], volume=0.65)
