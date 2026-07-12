# MIearn v2.3 Full Audio Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all 2,704 built-in pronunciation assets with the approved Piper Lessac High 40 kbps profile, preserve per-variant playback and 500 ms pauses, and ship the app metadata as version 2.3.

**Architecture:** Reuse the isolated A/B trial profile, pronunciation override, synthesis, encoding, and validation code, then add a production-only staging pipeline. The generator writes a complete candidate pack and deterministic manifest under `tmp/audio-production-v2.3`; a separate promotion command replaces committed assets only after validation passes. Android runtime behavior and database schemas remain unchanged.

**Tech Stack:** Python 3, Piper TTS `en_US-lessac-high`, FFmpeg/FFprobe, Ogg/Opus, pytest/unittest, Kotlin/Jetpack Compose, Gradle Kotlin DSL.

## Global Constraints

- Built-in content count is exactly 2,704 entries.
- Production voice is `en_US-lessac-high` with model SHA-256 `4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09`.
- Output is mono Ogg/Opus at 48 kHz and 40 kbps with Opus application `audio`.
- Complete multi-expression audio contains exactly 500 ms digital silence between variants; variant files add no artificial pause.
- The external Piper model, virtual environments, temporary audio, APKs, and signing material never enter Git.
- No Android runtime, Room schema, StudyScreen, SM-2, queue, answer, or import behavior changes.
- `versionName` becomes `2.3`; `versionCode` increments from 6 to 7.
- The APK must contain no `INTERNET` permission and the debug APK must remain below the existing 65,000,000-byte gate.

---

### Task 1: Bring the validated audio profile tooling onto the implementation branch

**Files:**
- Create: `tools/audio_profiles.py`
- Create: `tools/audio/pronunciation_overrides.json`
- Create: `tools/audio_trial.py`
- Create: `tools/generate_audio_trial.py`
- Create: `tools/validate_audio_trial.py`
- Create: `tools/tests/test_audio_encoding_profile.py`
- Create: `tools/tests/test_pronunciation_overrides.py`
- Create: `tools/tests/test_audio_trial.py`
- Create: `tools/tests/test_audio_trial_generation.py`
- Create: `tools/tests/test_validate_audio_trial.py`
- Modify: `tools/generate_audio.py`

**Interfaces:**
- Consumes: existing `spoken_text()`, `ffmpeg_encode_args()`, `normalize_ogg_serial()`, and variant parsing helpers.
- Produces: `LESSAC_HIGH: AudioProfile`, `load_pronunciation_overrides(path)`, `resolve_spoken_text(word, display_text, overrides)`, and the already accepted 50-entry A/B trial commands.

- [ ] **Step 1: Apply the three reviewed audio-tooling commits without the separate word-detail UI work**

```powershell
git cherry-pick 045f2d0d cb24af52 ac65fba2
```

Expected: three commits apply cleanly and only audio profile/trial files are introduced.

- [ ] **Step 2: Run the focused audio profile tests**

```powershell
python -m unittest tools.tests.test_audio_encoding_profile tools.tests.test_pronunciation_overrides tools.tests.test_audio_trial tools.tests.test_audio_trial_generation tools.tests.test_validate_audio_trial -v
```

Expected: all tests pass.

- [ ] **Step 3: Confirm production assets were not changed by the cherry-picks**

```powershell
git diff HEAD~3 --name-only -- app/src/main/assets/audio app/src/main/assets/content/audio_manifest_v1.json
```

Expected: no output.

---

### Task 2: Add a deterministic full-production generation plan

**Files:**
- Create: `tools/audio_production.py`
- Test: `tools/tests/test_audio_production.py`

**Interfaces:**
- Consumes: `load_words(path)`, the app-compatible variant splitter, `PronunciationOverrides`, and `resolve_spoken_text()`.
- Produces: `plan_production(words: list[dict], overrides: PronunciationOverrides) -> list[ProductionEntryPlan]`, `assert_safe_staging_path(path: Path) -> None`, and `content_sha256(path: Path) -> str`.

- [ ] **Step 1: Write failing tests for exact coverage, stable order, override resolution, and path isolation**

```python
def test_plan_production_preserves_every_word_and_variant():
    words = [
        {"id": "mec_0001_x", "english": "fixture；jig", "audioText": "fixture；jig", "kind": "TERM"},
        {"id": "mee_0001_x", "english": "PLC", "audioText": "PLC", "kind": "TERM"},
    ]
    overrides = PronunciationOverrides(
        by_word_id={},
        exact_text={"PLC": PronunciationRule("P L C", "abbreviation")},
    )

    plan = plan_production(words, overrides)

    assert [entry.word_id for entry in plan] == ["mec_0001_x", "mee_0001_x"]
    assert [segment.display_text for segment in plan[0].segments] == ["fixture", "jig"]
    assert plan[1].segments[0].spoken_text == "P L C"


def test_staging_path_rejects_production_assets(tmp_path):
    with pytest.raises(ValueError, match="production audio"):
        assert_safe_staging_path(Path("app/src/main/assets/audio"))
    assert_safe_staging_path(tmp_path / "audio-production-v2.3")
```

- [ ] **Step 2: Run the tests and confirm they fail before implementation**

```powershell
python -m pytest tools/tests/test_audio_production.py -v
```

Expected: FAIL because `tools.audio_production` does not exist.

- [ ] **Step 3: Implement immutable production plan types and safe staging checks**

```python
@dataclass(frozen=True)
class ProductionSegmentPlan:
    index: int
    display_text: str
    spoken_text: str
    override_key: str | None


@dataclass(frozen=True)
class ProductionEntryPlan:
    word_id: str
    english: str
    segments: tuple[ProductionSegmentPlan, ...]


def assert_safe_staging_path(path: Path) -> None:
    normalized = path.resolve()
    production = Path("app/src/main/assets/audio").resolve()
    if normalized == production or production in normalized.parents:
        raise ValueError("production audio directory cannot be used as staging")
```

Implement `plan_production()` by loading variants with the same Python splitter already verified against `EnglishVariantParser`, rejecting empty IDs, duplicate IDs, empty segment lists, and duplicate output paths. Preserve JSON order.

- [ ] **Step 4: Run the focused production planning tests**

```powershell
python -m pytest tools/tests/test_audio_production.py -v
```

Expected: PASS.

- [ ] **Step 5: Commit the production planner**

```powershell
git add tools/audio_production.py tools/tests/test_audio_production.py
git commit -m "feat: plan complete Piper High audio coverage"
```

---

### Task 3: Generate a staged Piper High production pack and manifest

**Files:**
- Create: `tools/generate_audio_production.py`
- Modify: `tools/audio_production.py`
- Test: `tools/tests/test_generate_audio_production.py`

**Interfaces:**
- Consumes: `ProductionEntryPlan`, `LESSAC_HIGH`, `combine_wavs()`, `ffmpeg_encode_args()`, and the external model/FFmpeg paths.
- Produces: `generate_production(content_path, overrides_path, model, ffmpeg, output) -> dict`, staged `audio/<wordId>.ogg`, staged `audio/variants/<wordId>_<index>.ogg`, `audio_manifest_v1.json`, and `release_audit.json`.

- [ ] **Step 1: Write failing integration tests using fake synthesis and encoding adapters**

```python
def test_generate_production_writes_full_variant_manifest_and_audit(tmp_path):
    words = tmp_path / "words.json"
    words.write_text(json.dumps({"words": SAMPLE_WORDS}), encoding="utf-8")

    report = generate_production(
        content_path=words,
        overrides_path=FIXTURE_OVERRIDES,
        model=FIXTURE_MODEL,
        ffmpeg=FIXTURE_FFMPEG,
        output=tmp_path / "audio-production-v2.3",
        synthesize=write_fixture_wav,
        encode=write_fixture_ogg,
        probe=probe_fixture_ogg,
    )

    assert report["entryCount"] == len(SAMPLE_WORDS)
    assert report["profile"]["name"] == "en_US-lessac-high"
    assert report["profile"]["bitRateKbps"] == 40
    assert report["entries"]["mec_0001_x"]["pauseBetweenSegmentsMs"] == 500
    assert (tmp_path / "audio-production-v2.3/audio/variants/mec_0001_x_01.ogg").is_file()
```

- [ ] **Step 2: Run the test and confirm the generator is missing**

```powershell
python -m pytest tools/tests/test_generate_audio_production.py -v
```

Expected: FAIL because `generate_audio_production` is not implemented.

- [ ] **Step 3: Implement resumable staging generation**

For every planned segment, synthesize a WAV using the fixed Piper synthesis parameters, encode the independent variant, combine WAVs with `combine_wavs()` for multi-segment complete audio, and encode the complete file. Write `.part` files and promote them with `os.replace()` so interruption cannot leave apparently complete files.

The manifest root must contain these exact production fields:

```python
manifest = {
    "schemaVersion": 2,
    "contentSha256": content_sha256(content_path),
    "entryCount": len(plans),
    "profile": {
        "name": LESSAC_HIGH.name,
        "modelSha256": LESSAC_HIGH.model_sha256,
        "modelConfigSha256": sha256(Path(f"{model}.json")),
        "bitRateKbps": 40,
        "application": "audio",
        "channels": 1,
        "encodedSampleRate": 48_000,
        "piperVersion": importlib.metadata.version("piper-tts"),
        "ffmpegVersion": ffmpeg_version(ffmpeg),
    },
    "entries": entries_by_id,
}
```

Each entry records full path, bytes, SHA-256, duration, codec, channels, sample rate, segments, spoken text, override key, and `pauseBetweenSegmentsMs` (`500` for multi-segment entries and `0` otherwise). Skip an existing staged file only when its recorded hash and probe metadata still match.

- [ ] **Step 4: Generate a compact manual-audit index**

`release_audit.json` must include one entry from each category plus matching source entries for `fixture`, `jig`, `GD&T`, `PLC`, `mylar`, a meeting sentence, and a business sentence. Missing named terms belong in `missingSuggestedSamples`; they do not create fake content.

- [ ] **Step 5: Run generator tests**

```powershell
python -m pytest tools/tests/test_generate_audio_production.py tools/tests/test_audio_production.py -v
```

Expected: PASS.

- [ ] **Step 6: Commit production generation code**

```powershell
git add tools/audio_production.py tools/generate_audio_production.py tools/tests/test_generate_audio_production.py
git commit -m "feat: generate staged Piper High production audio"
```

---

### Task 4: Validate and safely promote the complete pack

**Files:**
- Create: `tools/validate_audio_production.py`
- Create: `tools/promote_audio_production.py`
- Test: `tools/tests/test_validate_audio_production.py`
- Test: `tools/tests/test_promote_audio_production.py`

**Interfaces:**
- Consumes: staged audio directory and staged `audio_manifest_v1.json`.
- Produces: `validate_production(root, words, probe) -> dict`, `validation_report.json`, and `promote_production(staging, assets, manifest_target) -> None`.

- [ ] **Step 1: Write failing validation tests for every hard gate**

```python
@pytest.mark.parametrize("mutation, expected", [
    ("missing-full", "missing complete audio"),
    ("missing-variant", "missing variant audio"),
    ("hash-mismatch", "hash mismatch"),
    ("wrong-codec", "codec is not Opus"),
    ("wrong-channels", "channel count is not mono"),
    ("wrong-rate", "sample rate is not 48000"),
    ("silent", "silent or near-silent"),
    ("pause", "pause mismatch"),
])
def test_validation_rejects_invalid_pack(valid_pack, mutation, expected):
    mutate_pack(valid_pack, mutation)
    report = validate_production(valid_pack.root, valid_pack.words, valid_pack.probe)
    assert not report["passed"]
    assert any(expected in error for error in report["errors"])
```

- [ ] **Step 2: Write failing promotion tests proving failed packs cannot touch assets**

```python
def test_promote_requires_passing_validation(tmp_path):
    assets = tmp_path / "assets/audio"
    assets.mkdir(parents=True)
    marker = assets / "existing.ogg"
    marker.write_bytes(b"old")

    with pytest.raises(RuntimeError, match="validation"):
        promote_production(tmp_path / "staging", assets, tmp_path / "manifest.json")

    assert marker.read_bytes() == b"old"
```

- [ ] **Step 3: Run both test modules and confirm failure**

```powershell
python -m pytest tools/tests/test_validate_audio_production.py tools/tests/test_promote_audio_production.py -v
```

Expected: FAIL because production validation and promotion functions do not exist.

- [ ] **Step 4: Implement full-pack validation**

Require exactly 2,704 word records, a one-to-one manifest ID set, every full and variant file, matching byte counts and hashes, decodable non-silent mono Opus at 48 kHz, positive durations, and aggregate pause duration within `0.12 + pause_count * 0.005` seconds. Write the complete list of errors to `validation_report.json` and exit non-zero when any error exists.

- [ ] **Step 5: Implement guarded promotion**

Promotion reads `validation_report.json` and requires `passed == true` plus `validatedEntries == 2704`. Copy staged audio and manifest into sibling `.v2.3-new` paths, verify copied hashes, rename current assets to `.v2.2-backup`, rename new paths into production, then delete the backup only after both audio and manifest are installed. On any exception, restore the backup before re-raising.

- [ ] **Step 6: Run focused validation and promotion tests**

```powershell
python -m pytest tools/tests/test_validate_audio_production.py tools/tests/test_promote_audio_production.py -v
```

Expected: PASS.

- [ ] **Step 7: Commit validation and promotion tools**

```powershell
git add tools/validate_audio_production.py tools/promote_audio_production.py tools/tests/test_validate_audio_production.py tools/tests/test_promote_audio_production.py
git commit -m "feat: validate and promote production audio safely"
```

---

### Task 5: Generate, validate, and promote all 2,704 entries

**Files:**
- Replace: `app/src/main/assets/audio/*.ogg`
- Replace: `app/src/main/assets/audio/variants/*.ogg`
- Replace: `app/src/main/assets/content/audio_manifest_v1.json`
- Create: `docs/audio/v2.3-release-audit.json`
- Modify: `THIRD_PARTY_NOTICES.md`

**Interfaces:**
- Consumes: external model `D:\Android\AudioModels\piper\en_US-lessac-high\en_US-lessac-high.onnx` and FFmpeg `D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe`.
- Produces: the committed v2.3 offline production audio pack and audit evidence.

- [ ] **Step 1: Run all Python pipeline tests before the expensive generation**

```powershell
python -m pytest tools/tests -v
```

Expected: all tests pass.

- [ ] **Step 2: Generate the full pack into ignored staging storage**

```powershell
python tools/generate_audio_production.py --content app/src/main/assets/content/words_v1.json --overrides tools/audio/pronunciation_overrides.json --model D:\Android\AudioModels\piper\en_US-lessac-high\en_US-lessac-high.onnx --ffmpeg D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe --output tmp/audio-production-v2.3
```

Expected final line: `complete entries=2704 profile=en_US-lessac-high`.

- [ ] **Step 3: Validate every generated file**

```powershell
python tools/validate_audio_production.py --root tmp/audio-production-v2.3 --content app/src/main/assets/content/words_v1.json --ffprobe D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffprobe.exe --ffmpeg D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe
```

Expected final line: `PASS entries=2704` and zero validation errors.

- [ ] **Step 4: Promote only the validated pack**

```powershell
python tools/promote_audio_production.py --root tmp/audio-production-v2.3 --assets app/src/main/assets/audio --manifest app/src/main/assets/content/audio_manifest_v1.json
```

Expected final line: `promoted entries=2704`.

- [ ] **Step 5: Commit only production artifacts and audit metadata**

```powershell
Copy-Item tmp/audio-production-v2.3/release_audit.json docs/audio/v2.3-release-audit.json
git add app/src/main/assets/audio app/src/main/assets/content/audio_manifest_v1.json docs/audio/v2.3-release-audit.json THIRD_PARTY_NOTICES.md
git commit -m "feat: replace pronunciation pack with Piper High"
```

Expected: `tmp/`, model files, virtual environments, and executables remain untracked/ignored.

---

### Task 6: Set v2.3 metadata and run Android release gates

**Files:**
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/miearn/app/audio/SpeechAssetPlanTest.kt`

**Interfaces:**
- Consumes: promoted manifest and audio assets.
- Produces: `versionCode = 7`, `versionName = "2.3"`, and verified debug application artifacts.

- [ ] **Step 1: Strengthen the Android asset-plan test for full manifest coverage**

```kotlin
@Test
fun productionManifestUsesPiperHighForEveryBuiltInWord() {
    val words = loadWords()
    val manifest = loadManifest()

    assertEquals(2704, words.size)
    assertEquals(words.map { it.id }.toSet(), manifest.entries.keys)
    assertEquals("en_US-lessac-high", manifest.profile.name)
    assertEquals(40, manifest.profile.bitRateKbps)
    assertEquals(48_000, manifest.profile.encodedSampleRate)
}
```

- [ ] **Step 2: Run the focused Android test before updating its manifest parser**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.miearn.app.audio.SpeechAssetPlanTest
```

Expected: FAIL until the test fixture/parser accepts the new production manifest metadata.

- [ ] **Step 3: Make the minimal test-side manifest model update and set app version**

```kotlin
versionCode = 7
versionName = "2.3"
```

Do not change runtime pronunciation or learning code.

- [ ] **Step 4: Run required project verification**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat verifyDebugApkSize
```

Expected: all commands exit 0 and `app-debug.apk` is at most 65,000,000 bytes.

- [ ] **Step 5: Verify APK permissions**

```powershell
$aapt = Join-Path $env:ANDROID_SDK_ROOT 'build-tools\36.0.0\aapt.exe'
& $aapt dump permissions app\build\outputs\apk\debug\app-debug.apk
```

Expected: output does not contain `android.permission.INTERNET`.

- [ ] **Step 6: Commit v2.3 metadata and verification test**

```powershell
git add app/build.gradle.kts app/src/test/java/com/miearn/app/audio/SpeechAssetPlanTest.kt
git commit -m "chore: set MIearn version 2.3"
```

---

### Task 7: Final repository and manual audio audit

**Files:**
- Inspect: `docs/audio/v2.3-release-audit.json`
- Inspect: `app/src/main/assets/audio/`
- Inspect: `app/src/main/assets/content/audio_manifest_v1.json`

**Interfaces:**
- Consumes: completed v2.3 commits and verification reports.
- Produces: a clean implementation branch ready for review; no GitHub Release is published in this plan.

- [ ] **Step 1: Confirm ignored intermediates and repository cleanliness**

```powershell
git status --short
git check-ignore tmp/audio-production-v2.3 tools/.piper-models tools/.venv .superpowers
```

Expected: no unintended tracked changes; every generated/cache path is ignored.

- [ ] **Step 2: Re-run a manifest-to-filesystem count audit**

```powershell
python tools/validate_audio_production.py --root app/src/main/assets --content app/src/main/assets/content/words_v1.json --manifest app/src/main/assets/content/audio_manifest_v1.json --ffprobe D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffprobe.exe --ffmpeg D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe
```

Expected: `PASS entries=2704`.

- [ ] **Step 3: Manually listen to the paths listed by the audit report**

Confirm complete and independent playback for `fixture`/`jig`, natural letter pronunciation for `GD&T` and `PLC`, unclipped `mylar`, one long meeting sentence, one business sentence, and one sample from every category. Record rejected word IDs before any release decision.

- [ ] **Step 4: Review the final diff summary**

```powershell
git diff main...HEAD --stat
git log --oneline main..HEAD
```

Expected: changes are limited to audio pipeline source/tests, production audio, manifest/audit/notices, test metadata, and app version.

