# MIearn v2.3 Full Audio Coverage Design

## Goal

MIearn v2.3 replaces the packaged production pronunciation set with the approved Piper `en_US-lessac-high` voice for all built-in entries. The app remains offline-first: no network permission, no runtime model, and no cloud speech service. Runtime playback continues to use bundled Ogg/Opus files first and Android TTS only as a fallback for missing or failed assets.

The v2.3 audio pack covers every built-in `words_v1.json` entry and every generated English variant segment. The existing Lessac Medium audio is not kept in the APK after replacement.

## Scope

Included:

- Generate production audio for all 2,704 built-in entries.
- Generate variant audio for every expression split by the app's current English variant parser.
- Use Piper `en_US-lessac-high` with the previously validated 40 kbps Ogg/Opus profile.
- Preserve the 500 ms pause between segments in complete multi-expression audio.
- Apply the version-controlled pronunciation override dictionary before synthesis.
- Update `audio_manifest_v1.json` with deterministic hashes, byte sizes, segment metadata, and generation profile metadata.
- Set the app version to `2.3`.
- Add verification gates that fail on missing audio, invalid decode, mismatched manifest, silent output, wrong profile, missing variant files, or APK size over the configured threshold.
- Produce a short release-audit report for manual spot checks.

Excluded:

- No UI redesign.
- No changes to `StudyScreen`, SM-2, learning queue, answer logic, import logic, Room schema, or user progress.
- No runtime Piper/Kokoro model.
- No online TTS or downloadable audio packs.
- No A/B bundle in the final APK.
- No GitHub Release publication unless requested separately.

## Chosen Approach

Use a single production audio pack generated from Piper Lessac High. The alternative of shipping both Medium and High would make rollback easy but wastes APK size and complicates runtime selection. The alternative of downloading audio later conflicts with the current offline identity of MIearn and would reintroduce network permission concerns.

The full pack is generated in a staging directory first, validated there, then atomically copied into `app/src/main/assets/audio` and `app/src/main/assets/audio/variants`. This keeps the repo stable if generation fails halfway.

## Audio Profile

Production v2.3 audio uses:

- Model: Piper `en_US-lessac-high`.
- Model SHA-256: `4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09`.
- Container: Ogg.
- Codec: Opus.
- Channels: mono.
- Encoded sample rate: 48 kHz.
- Bitrate: 40 kbps.
- Opus application: `audio`.
- Complete multi-expression pause: 500 ms digital silence between segments.
- Variant segment files: no leading or trailing artificial pause.

The model and Piper runtime stay outside the repository and outside the APK. `THIRD_PARTY_NOTICES.md` records the model source, model hash, and relevant licenses.

## Pronunciation Rules

The generation pipeline consumes `tools/audio/pronunciation_overrides.json`. Overrides change only the speech input, not the displayed English, Chinese meaning, search text, or Room data.

Rule priority:

1. Stable word ID rule.
2. Exact variant text rule.
3. Existing normalized spoken text fallback.

The initial required coverage includes known manufacturing abbreviations and symbol expressions such as `GD&T`, `PLC`, and `CMM` when present in the source data. Missing expected terms are reported honestly rather than synthesized as fake vocabulary.

## Data Flow

1. Load `app/src/main/assets/content/words_v1.json`.
2. For each word, derive the complete display text and its variant list using the same split rules as the Android app.
3. Resolve spoken text for the complete entry and each variant through the override dictionary.
4. Synthesize each variant with Piper High.
5. Build the complete entry by concatenating variant PCM with 500 ms pauses, then encode once to Ogg/Opus.
6. Encode each variant independently to Ogg/Opus.
7. Write all files to `tmp/audio-production-v2.3`.
8. Generate an audio manifest and audit report in the staging directory.
9. Validate the staging directory.
10. Replace production asset directories only after validation passes.

## Manifest

`audio_manifest_v1.json` remains the app-facing manifest name, but its content records the v2.3 production profile:

- Schema version.
- Content source hash.
- Generator version.
- Piper model name and SHA-256.
- FFmpeg and Piper versions.
- Complete audio path, bytes, SHA-256, duration, codec, channel count, sample rate, and bitrate target.
- Variant texts, spoken texts, rule keys, paths, bytes, SHA-256, and duration.
- `pauseBetweenSegmentsMs = 500` for entries with multiple variants.

The manifest is deterministic. Re-running the pipeline with identical inputs should produce identical file hashes except where upstream tool versions are intentionally changed and recorded.

## Runtime Behavior

The app's existing playback behavior stays intact:

- `AudioPronouncer.play(word)` plays the complete asset.
- `AudioPronouncer.playVariant(word, index)` plays only the selected variant asset.
- Missing or failed bundled audio falls back to Android TTS.
- TTS fallback preserves the 500 ms pause for complete multi-expression requests.
- Leaving detail or study surfaces continues to stop active pronunciation according to existing lifecycle rules.

No Room migration is required because runtime paths are derived from existing word IDs and manifest conventions.

## Versioning

The app version name becomes `2.3`. The version code should increment by one from the current committed value during implementation. Version changes are part of the implementation plan, not this design-only commit.

## Validation Gates

The v2.3 implementation must fail the build or generation step when any mandatory check fails:

- The JSON word count is exactly 2,704.
- Every word has one complete Ogg file.
- Every parsed variant has one matching variant Ogg file.
- No duplicate output path or duplicate manifest ID exists.
- Every file is decodable by FFprobe.
- Every file is mono Opus at 48 kHz.
- Every file has positive duration and non-silent PCM energy.
- Multi-expression complete files contain 500 ms pauses within tolerance.
- Manifest byte counts and SHA-256 hashes match the actual files.
- The APK has no `INTERNET` permission.
- The debug APK remains under 100,000,000 bytes unless a later explicit release decision changes the threshold.

## Manual Acceptance

After automated validation, manually spot check at least:

- `fixture；jig`: complete playback has a natural pause, each chip plays only itself.
- `GD&T`: reads letters and symbol expansion naturally.
- `PLC`: reads as letters, not as a word.
- `mylar`: sounds clear and not clipped.
- One long meeting sentence and one business sentence: pacing remains natural.
- One sample from each category: mechanical, electrical, customer review, meeting, business.

The audit report should list these suggested samples with direct file paths so they can be opened locally before building a release.

## Failure And Rollback

Generation is staging-first. A failed generation leaves current production audio untouched. If v2.3 audio is committed and later rejected, rollback is a normal Git revert of the asset and manifest commit. Because the old Medium audio is not packaged in v2.3, runtime rollback is not provided.

## Repository Hygiene

Ignored generated directories:

- `tmp/audio-production-v2.3/`
- `tmp/audio-trial/`
- `tools/.piper-models/`
- `.superpowers/`

Committed files after implementation should be limited to source pipeline changes, tests, updated production audio assets, updated manifest, notices, version metadata, and release audit documentation. The external model, Python virtual environments, APKs, keystores, and local cache files must remain outside Git.

## Test Plan

- Python unit tests for override priority, invalid override rejection, profile metadata, deterministic manifest generation, and path isolation.
- Python integration validation for 2,704 complete files and all variant files.
- Audio validation through FFprobe and PCM energy checks.
- Android unit tests for playback request path resolution if runtime code changes are needed.
- `./gradlew assembleDebug`.
- `./gradlew test`.
- `./gradlew lint`.
- APK permission check confirms no `INTERNET`.
- APK size check confirms the configured threshold.

## Open Implementation Notes

- Prefer extending the existing trial generator into a production generator rather than creating a parallel one-off script.
- Keep the 50-entry trial generator for future voice comparisons, but ensure it cannot write into production assets.
- The full-screen word detail feature remains a separate already-designed implementation track. v2.3 audio coverage should not depend on UI changes.
