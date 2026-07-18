# LJSpeech production audio

MIearn's current production fallback voice is Piper `en_US-ljspeech-high`.
The upstream model card identifies the training dataset, LJSpeech, as public
domain. The model, configuration and model card are build inputs stored outside
the repository; only generated Ogg/Opus files are packaged in the app.

Pinned build inputs:

- Model SHA-256: `5d4f08ba6a2a48c44592eed3ce56bf85e9de3dd4e20df90541ae68a8310c029a`
- Configuration SHA-256: `7e1f4634af596d83cca997fb7a931ba80b70f8a316a2655ee69c55365e0ace14`
- Model-card SHA-256: `fbdb9c09bd33e73f6876ba48fc2eeea120b9984d07763bfa21b3c8192fd4ba86`
- Model source: <https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/ljspeech/high>
- Dataset: <https://keithito.com/LJ-Speech-Dataset/> (public domain)

The production command writes to external staging and is resumable. It never
uses the packaged Lessac files as synthesis input. Piper renders each planned
expression to a fresh PCM WAV; FFmpeg then encodes that WAV to mono, 48 kHz,
40 kbps Ogg/Opus. Multi-expression complete audio is assembled from those PCM
WAVs with 500 ms of zero-sample silence between expressions. The seven
allowlisted public-domain Wikimedia recordings are fetched from their original
URLs, verified against their pinned hashes, and decoded once for normalization.

## Synthesis parameters

Production keeps `noiseScale = 0`, `noiseWScale = 0` and `lengthScale = 1`.
This is an evidence-based choice rather than a determinism shortcut. On
2026-07-15, a fixed 95-asset sample was audited with Faster-Whisper `base.en`.
The sample contained 45 current mismatches, 25 passing short expressions and
25 passing longer expressions:

| Parameters | Passed | Fixed | Regressed |
| --- | ---: | ---: | ---: |
| `0 / 0 / 1` | 50 / 95 | baseline | baseline |
| `0.35 / 0.5 / 1` | 44 / 95 | 3 | 9 |
| `0.667 / 0.8 / 1` | 46 / 95 | 5 | 9 |

The noisier profiles improved a few expressions such as `pneumatic hoist`, but
regressed more already-correct terms. Do not change the global synthesis
parameters from a handful of favorable samples. Remaining failures must be
handled with hash-bound, expression-specific pronunciation evidence.

```powershell
python tools/generate_audio_production.py `
  --content app/src/main/assets/content/words_v1.json `
  --overrides tools/audio/pronunciation_overrides.json `
  --model D:/Android/AudioModels/piper/en_US-ljspeech-high/en_US-ljspeech-high.onnx `
  --ffmpeg D:/ffmpeg/ffmpeg-master-latest-win64-gpl/bin/ffmpeg.exe `
  --human-attributions app/src/main/assets/content/audio_attributions_v231.json `
  --human-audio-root D:/Android/BuildCache/MIearn/human-audio-v231 `
  --model-audio-attributions D:/Android/BuildCache/MIearn/kokoro-corrections-v232/kokoro_corrections_v1.json `
  --model-audio-root D:/Android/BuildCache/MIearn/kokoro-corrections-v232 `
  --output D:/Android/BuildCache/MIearn/audio-production-ljspeech
```

## Hash-bound Kokoro corrections

The global Piper profile remains unchanged. After the first strict ASR pass,
the rejected expressions are extracted from the hash-bound report and rendered
with Kokoro-82M 1.0 (`af_heart`, Apache-2.0). Only exact normalized expression
matches can adopt those sources. The correction manifest records the displayed
text, synthesis hint, model provenance, source WAV size and SHA-256.

```powershell
python tools/generate_kokoro_corrections.py `
  --content app/src/main/assets/content/words_v1.json `
  --production-manifest D:/Android/BuildCache/MIearn/audio-production-ljspeech/audio_manifest_v1.json `
  --asr-report D:/Android/BuildCache/MIearn/audio-production-ljspeech/asr-faster-audit.json `
  --output D:/Android/BuildCache/MIearn/kokoro-corrections-v232
```

The V2.32 release uses 53 unique Kokoro correction sources. Across complete and
variant records, 120 production audio nodes refer to those sources. The final
manifest still contains one complete asset for every word and one independent
asset for every displayed segment.

Promotion is allowed only after all 3,741 complete and variant files pass the
manifest, hash, decode, non-silence, source-provenance, segment and 500 ms pause
checks, followed by a fresh hash-bound ASR audit:

```powershell
python tools/validate_audio_production.py `
  --root D:/Android/BuildCache/MIearn/audio-production-ljspeech `
  --content app/src/main/assets/content/words_v1.json `
  --ffprobe D:/ffmpeg/ffmpeg-master-latest-win64-gpl/bin/ffprobe.exe `
  --ffmpeg D:/ffmpeg/ffmpeg-master-latest-win64-gpl/bin/ffmpeg.exe `
  --overrides tools/audio/pronunciation_overrides.json `
  --human-attributions app/src/main/assets/content/audio_attributions_v231.json `
  --human-audio-root D:/Android/BuildCache/MIearn/human-audio-v231

python tools/pronunciation/audit_audio_recognition.py `
  --manifest D:/Android/BuildCache/MIearn/audio-production-ljspeech/audio_manifest_v1.json `
  --assets D:/Android/BuildCache/MIearn/audio-production-ljspeech `
  --backend faster-whisper `
  --model base.en `
  --compute-type int8 `
  --cpu-threads 12 `
  --model-cache D:/Android/BuildCache/MIearn/faster-whisper-models `
  --overrides tools/pronunciation/audio_audit_overrides.json `
  --report D:/Android/BuildCache/MIearn/audio-production-ljspeech/asr-faster-audit.json

python tools/promote_audio_production.py `
  --root D:/Android/BuildCache/MIearn/audio-production-ljspeech `
  --assets app/src/main/assets/audio `
  --manifest app/src/main/assets/content/audio_manifest_v1.json
```

The ASR dependency is a build-time tool installed outside the repository; it is
not packaged in either app. Reviews created for
Lessac audio are intentionally invalid because every generated asset hash has
changed. A release may not reuse those old exceptions as evidence for the new
voice; remaining ASR mismatches require fresh listening and hash-bound review.

For V2.32, Faster-Whisper `base.en` with `small.en` retry audited all 3,741
assets against manifest SHA-256
`337138901fa9220cdbdda3591597f99e1f864ca15f95747be631fe1263a9d934`.
The final report has `unreviewedFailureCount = 0`; no audio audit whitelist was
used to waive a mismatch.
