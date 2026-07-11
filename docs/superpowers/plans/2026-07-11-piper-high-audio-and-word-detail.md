# Piper High Audio Trial and Word Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a non-destructive 50-entry Lessac Medium/High audio trial, then add the approved full-screen word detail page for ordinary and segmented English expressions.

**Architecture:** Phase 1 extends the Python content pipeline with a deterministic sample selector, pronunciation override resolver, explicit audio profiles, and isolated trial output. Phase 2 adds a transient `WordDetailRequest` to the existing `MainViewModel`-driven navigation, reuses `WordEntity` and `AudioPronouncer`, and does not change Room, learning scheduling, SM-2, or session state.

**Tech Stack:** Python 3, Piper TTS, FFmpeg/FFprobe, JSON, Kotlin, Jetpack Compose, MVVM, JUnit, Compose UI tests.

## Global Constraints

- Phase 1 must not write to `app/src/main/assets/audio` or `app/src/main/assets/content/audio_manifest_v1.json`.
- Use Piper `en_US-lessac-high`, model SHA-256 `4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09`.
- Candidate output is mono Ogg/Opus, 40 kbps, 48 kHz, `application=audio`.
- Multiple expressions retain separate variant files and exact 500 ms digital silence in the complete sequence.
- Store the downloaded model outside Git and outside packaged Android assets.
- Do not add `INTERNET` permission, Room fields, third-party Android dependencies, version changes, APK publishing, or GitHub Release work.
- Phase 2 begins after the Phase 1 listening bundle is delivered; full-pack replacement remains gated on explicit user approval of candidate B.
- Leaving the detail page stops audio started from that page and preserves the current study card/session.
- After Kotlin changes run `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lint`.

---

### Task 1: Isolate audio profiles and pronunciation overrides

**Files:**
- Create: `tools/audio/pronunciation_overrides.json`
- Create: `tools/audio_profiles.py`
- Modify: `tools/generate_audio.py`
- Modify: `tools/tests/test_audio_generator.py`
- Create: `tools/tests/test_pronunciation_overrides.py`

**Interfaces:**
- Produces: `AudioProfile(name, model_sha256, bitrate_kbps, application, source_sample_rate)`.
- Produces: `load_pronunciation_overrides(path: Path) -> PronunciationOverrides`.
- Produces: `resolve_spoken_text(word: dict, display_text: str, overrides: PronunciationOverrides) -> tuple[str, str | None]`, where the second item is the matched rule key.
- Consumes: existing `spoken_text(word)` normalization as the fallback.

- [ ] **Step 1: Write failing profile and override tests**

```python
class PronunciationOverridesTest(unittest.TestCase):
    def test_word_id_rule_wins_over_exact_text_rule(self):
        rules = PronunciationOverrides(
            by_word_id={"mechanical_0050": "G D and T"},
            by_exact_text={"GD&T": "geometric dimensioning and tolerancing"},
        )
        spoken, key = resolve_spoken_text(
            {"id": "mechanical_0050", "audioText": "GD&T"}, "GD&T", rules
        )
        self.assertEqual("G D and T", spoken)
        self.assertEqual("wordId:mechanical_0050", key)

    def test_empty_and_duplicate_rules_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "non-empty"):
            PronunciationOverrides.from_json({"exactText": {"PLC": ""}})
```

```python
class AudioProfileTest(unittest.TestCase):
    def test_lessac_high_profile_is_40k_audio(self):
        self.assertEqual(40, LESSAC_HIGH.bit_rate_kbps)
        self.assertEqual("audio", LESSAC_HIGH.application)
        self.assertEqual(48000, LESSAC_HIGH.encoded_sample_rate)
```

- [ ] **Step 2: Run tests and verify they fail because the new modules do not exist**

Run: `python -m unittest tools.tests.test_pronunciation_overrides tools.tests.test_audio_generator -v`

Expected: FAIL with import errors for `tools.audio_profiles` and override interfaces.

- [ ] **Step 3: Implement immutable profiles and strict JSON parsing**

```python
@dataclass(frozen=True)
class AudioProfile:
    name: str
    model_sha256: str
    bit_rate_kbps: int
    application: str
    source_sample_rate: int = 22050
    encoded_sample_rate: int = 48000

LESSAC_HIGH = AudioProfile(
    name="en_US-lessac-high",
    model_sha256="4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09",
    bit_rate_kbps=40,
    application="audio",
)
```

Use this initial override content:

```json
{
  "schemaVersion": 1,
  "wordId": {},
  "exactText": {
    "GD&T": {"spokenText": "G D and T", "type": "symbolExpansion", "note": "Read the abbreviation, not the ampersand."},
    "CMM": {"spokenText": "C M M", "type": "initialism", "note": "Coordinate measuring machine."},
    "PLC": {"spokenText": "P L C", "type": "initialism", "note": "Programmable logic controller."}
  }
}
```

- [ ] **Step 4: Refactor `generate_audio.py` to build FFmpeg arguments from an `AudioProfile`**

```python
def ffmpeg_encode_args(profile: AudioProfile, source: Path, target: Path) -> list[str]:
    return [
        "-i", str(source), "-ac", "1", "-af", "aresample=22050",
        "-ar", str(profile.encoded_sample_rate), "-c:a", "libopus",
        "-b:a", f"{profile.bit_rate_kbps}k", "-application", profile.application,
        "-flags:a", "+bitexact", "-map_metadata", "-1", "-f", "ogg", str(target),
    ]
```

Keep the current Medium constants as the production default so this task cannot alter packaged audio accidentally.

- [ ] **Step 5: Run focused Python tests**

Run: `python -m unittest tools.tests.test_pronunciation_overrides tools.tests.test_audio_generator -v`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add tools/audio tools/audio_profiles.py tools/generate_audio.py tools/tests/test_audio_generator.py tools/tests/test_pronunciation_overrides.py
git commit -m "feat: add auditable pronunciation profiles"
```

---

### Task 2: Build deterministic 50-entry A/B trial selection

**Files:**
- Create: `tools/audio_trial.py`
- Create: `tools/tests/test_audio_trial.py`

**Interfaces:**
- Consumes: `AudioProfile`, `PronunciationOverrides`, `EnglishVariantParser`-equivalent Python segmentation already used by the variant generator.
- Produces: `select_trial_words(words: list[dict], total: int = 50) -> list[dict]`.
- Produces: `TrialEntry(id, category, english, variants, required_matches)` serialized to `trial_selection.json`.

- [ ] **Step 1: Write failing deterministic selection tests**

```python
def test_trial_selection_is_deterministic_and_stratified():
    first = select_trial_words(load_words(FIXTURE), total=50)
    second = select_trial_words(load_words(FIXTURE), total=50)
    assert [word["id"] for word in first] == [word["id"] for word in second]
    assert len(first) == 50
    assert len({word["categoryId"] for word in first}) == 5


def test_required_terms_are_selected_when_present():
    selected = select_trial_words(load_words(FIXTURE), total=50)
    joined = " ".join(word["english"].lower() for word in selected)
    for term in ("fixture", "jig", "gd&t", "cmm", "plc", "mylar"):
        assert term in joined
```

- [ ] **Step 2: Run the test and verify failure**

Run: `python -m unittest tools.tests.test_audio_trial -v`

Expected: FAIL because `select_trial_words` is undefined.

- [ ] **Step 3: Implement stable scoring rather than random sampling**

Select required-term matches first, then fill category/type buckets using `sha256(f"miearn-audio-trial-v1:{word_id}")` ordering. Reject duplicate IDs, return exactly 50, and report absent required terms under `missingRequiredTerms` instead of synthesizing fake entries.

- [ ] **Step 4: Write selection output only under the caller-supplied trial directory**

```python
def write_trial_selection(output: Path, selection: TrialSelection) -> None:
    output.mkdir(parents=True, exist_ok=True)
    (output / "trial_selection.json").write_text(
        json.dumps(selection.to_json(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
```

Reject an output path that resolves to or beneath `app/src/main/assets/audio`.

- [ ] **Step 5: Run tests and commit**

Run: `python -m unittest tools.tests.test_audio_trial -v`

Expected: PASS with exactly 50 unique IDs.

```powershell
git add tools/audio_trial.py tools/tests/test_audio_trial.py
git commit -m "feat: select deterministic audio trial entries"
```

---

### Task 3: Generate and validate the isolated Lessac High trial bundle

**Files:**
- Modify: `tools/audio_trial.py`
- Create: `tools/validate_audio_trial.py`
- Create: `tools/tests/test_validate_audio_trial.py`
- Modify: `THIRD_PARTY_NOTICES.md`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `tmp/audio-trial/a-current/<wordId>.ogg` and `tmp/audio-trial/b-high/<wordId>.ogg`.
- Produces: variant assets beneath each side's `variants/` directory.
- Produces: `tmp/audio-trial/trial_report.json` and `tmp/audio-trial/README.md`.
- Produces: `validate_trial(root: Path, ffprobe: Path) -> ValidationReport`.

- [ ] **Step 1: Write failing validation tests**

Cover missing A/B files, zero duration, silent PCM, wrong codec/channel/sample rate, wrong B bitrate/profile metadata, mismatched hashes, and a multi-variant full file without a 500 ms silence window.

```python
def test_candidate_metadata_requires_audio_profile():
    report = validate_fixture(candidate_application="voip", candidate_bitrate=32)
    assert "candidate profile" in report.errors[0]
```

- [ ] **Step 2: Run and verify failure**

Run: `python -m unittest tools.tests.test_validate_audio_trial -v`

Expected: FAIL because `validate_trial` does not exist.

- [ ] **Step 3: Add explicit CLI arguments and an isolation guard**

```text
python tools/audio_trial.py \
  --content app/src/main/assets/content/words_v1.json \
  --overrides tools/audio/pronunciation_overrides.json \
  --current-audio app/src/main/assets/audio \
  --model D:/Android/AudioModels/piper/en_US-lessac-high/en_US-lessac-high.onnx \
  --ffmpeg D:/ffmpeg/ffmpeg-master-latest-win64-gpl/bin/ffmpeg.exe \
  --output tmp/audio-trial
```

The tool copies current A assets, synthesizes B assets, applies overrides before synthesis, creates variant files, inserts exact 500 ms PCM silence between complete-sequence variants, normalizes Ogg serials, and never opens production assets for writing.

- [ ] **Step 4: Download the official model and config outside the repository, then verify SHA-256**

Use the official `rhasspy/piper-voices/en/en_US/lessac/high` model and JSON config. Abort before loading if the ONNX SHA differs from the fixed value in Global Constraints. Record the downloaded config hash in `trial_report.json`.

- [ ] **Step 5: Implement validation and report generation**

The report records source word, actual spoken text, override key, A/B path, bytes, duration, codec, channels, sample rate, SHA-256, pause measurement, model/config hash, Piper version, and FFmpeg version. `passed` is true only when every mandatory check succeeds.

- [ ] **Step 6: Run all pipeline tests**

Run: `python -m unittest discover -s tools/tests -v`

Expected: PASS.

- [ ] **Step 7: Generate the 50-entry bundle and validate it**

Run the command from Step 3, then:

```powershell
python tools/validate_audio_trial.py --root tmp/audio-trial --ffprobe D:/ffmpeg/ffmpeg-master-latest-win64-gpl/bin/ffprobe.exe
```

Expected: `PASS entries=50`, all B files report Opus/mono/48000 Hz and candidate profile 40 kbps/application audio.

- [ ] **Step 8: Update notices and ignore generated/model directories**

Document Lessac High source, model SHA, Piper build-time tool license, dataset license URL, and that the model is not packaged. Ensure `.gitignore` contains `tmp/audio-trial/`, `tools/.piper-models/`, and `.superpowers/`.

- [ ] **Step 9: Commit source and test changes, not generated trial audio**

```powershell
git add tools/audio_trial.py tools/validate_audio_trial.py tools/tests/test_validate_audio_trial.py tools/audio/pronunciation_overrides.json THIRD_PARTY_NOTICES.md .gitignore
git commit -m "feat: generate isolated Piper High audio trial"
```

**Checkpoint:** Deliver the local trial directory and report to the user. Do not replace production audio. Phase 2 may continue because it does not depend on choosing A or B; full audio-pack regeneration may not continue without explicit B approval.

---

### Task 4: Model full-screen word detail state and phonetic resolution

**Files:**
- Create: `app/src/main/java/com/miearn/app/ui/WordDetailModels.kt`
- Create: `app/src/test/java/com/miearn/app/ui/WordDetailModelsTest.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Modify: `app/src/test/java/com/miearn/app/ui/NavigationAndSettingsModelTest.kt`

**Interfaces:**
- Produces: `data class WordDetailRequest(val word: WordEntity, val variantIndex: Int?)`.
- Produces: `data class PhoneticDisplay(val text: String, val isWholeEntry: Boolean)`.
- Produces: `resolveVariantPhonetic(word: WordEntity, variantIndex: Int?) -> PhoneticDisplay?`.
- Produces ViewModel methods: `openWordDetail(word, variantIndex)`, `closeWordDetail()`, `playWordDetail()`, `toggleWordDetailFavorite()`.

- [ ] **Step 1: Write failing resolver tests**

```kotlin
@Test fun `matching phonetic segments use selected index`() {
    val word = word(english = "fixture；jig", phonetic = "/ˈfɪkstʃər/；/dʒɪɡ/")
    assertEquals(PhoneticDisplay("/dʒɪɡ/", false), resolveVariantPhonetic(word, 1))
}

@Test fun `mismatched segment count falls back to whole phonetic`() {
    val word = word(english = "fixture；jig", phonetic = "/ˈfɪkstʃər/")
    assertEquals(PhoneticDisplay("/ˈfɪkstʃər/", true), resolveVariantPhonetic(word, 1))
}

@Test fun `blank phonetic returns null`() {
    assertNull(resolveVariantPhonetic(word(phonetic = ""), 0))
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests com.miearn.app.ui.WordDetailModelsTest`

Expected: FAIL because the models and resolver are absent.

- [ ] **Step 3: Implement the pure resolver and transient ViewModel state**

Use only semicolon variants for phonetic alignment. Clamp/reject an invalid variant index rather than silently selecting another expression. `closeWordDetail()` calls `audioPronouncer.stop()` and clears only `wordDetailRequest`; it must not call `closeStudy()`.

- [ ] **Step 4: Add navigation-state tests**

Assert opening detail does not mutate `studyState`, closing detail clears the request, and `playWordDetail()` dispatches `pronounceVariant` for a segmented request or `pronounce` for a single-expression request.

- [ ] **Step 5: Run tests and commit**

Run: `.\gradlew.bat testDebugUnitTest --tests com.miearn.app.ui.WordDetailModelsTest --tests com.miearn.app.ui.NavigationAndSettingsModelTest`

Expected: PASS.

```powershell
git add app/src/main/java/com/miearn/app/ui/WordDetailModels.kt app/src/main/java/com/miearn/app/ui/MainViewModel.kt app/src/test/java/com/miearn/app/ui/WordDetailModelsTest.kt app/src/test/java/com/miearn/app/ui/NavigationAndSettingsModelTest.kt
git commit -m "feat: model word detail navigation"
```

---

### Task 5: Build the approved full-screen word detail UI

**Files:**
- Create: `app/src/main/java/com/miearn/app/ui/WordDetailScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/EnglishVariants.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/StudyScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- Modify: `app/src/androidTest/java/com/miearn/app/EnglishVariantsTest.kt`
- Create: `app/src/androidTest/java/com/miearn/app/WordDetailScreenTest.kt`

**Interfaces:**
- `EnglishVariants(word, onOpenVariant, onPlayVariant, modifier)` separates text navigation from the speaker control.
- `StudyScreen` adds `onOpenWordDetail: (WordEntity, Int?) -> Unit` without changing answer callbacks.
- `WordDetailScreen(request, phonetic, onBack, onPlay, onFavorite)` is stateless.

- [ ] **Step 1: Write failing Compose tests for navigation intent and semantics**

```kotlin
composeRule.onNodeWithText("jig").performClick()
assertEquals(1, openedVariantIndex)
composeRule.onNodeWithContentDescription("播放 jig").performClick()
assertEquals(1, playedVariantIndex)
```

Assert the detail screen shows the selected expression, matching phonetic, Chinese meaning, existing bilingual example, favorite action, and a top back action with at least a 48 dp touch target.

- [ ] **Step 2: Run instrumented tests and verify failure**

Run: `.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miearn.app.WordDetailScreenTest,com.miearn.app.EnglishVariantsTest`

Expected: FAIL because detail navigation and separated controls are absent.

- [ ] **Step 3: Implement the stateless full-screen layout**

Use the existing soft-space theme: warm/ink background, centered expression, phonetic immediately below it, a circular play control, then one 24 dp rounded meaning/example card. Do not add images, tabs, advertisements, streaks, or unrelated statistics.

- [ ] **Step 4: Wire transient detail navigation above the study screen**

In `MIearnApp`, collect `wordDetailRequest` before rendering `StudyScreen`; when non-null, install `BackHandler(onBack = viewModel::closeWordDetail)` and render `WordDetailScreen`. Returning reveals the same existing `StudyUiState` and current card.

- [ ] **Step 5: Separate variant text and audio touch targets**

Each purple expression chip opens detail when its text/body is tapped and includes a compact speaker action with content description `播放 <variant>`. A single-expression card passes `variantIndex = null`; segmented expressions pass their actual index.

- [ ] **Step 6: Run unit and Compose tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miearn.app.WordDetailScreenTest,com.miearn.app.EnglishVariantsTest
```

Expected: PASS; closing detail leaves the study session on the same word.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/miearn/app/ui/WordDetailScreen.kt app/src/main/java/com/miearn/app/ui/EnglishVariants.kt app/src/main/java/com/miearn/app/ui/StudyScreen.kt app/src/main/java/com/miearn/app/ui/MIearnApp.kt app/src/androidTest/java/com/miearn/app/EnglishVariantsTest.kt app/src/androidTest/java/com/miearn/app/WordDetailScreenTest.kt
git commit -m "feat: add full-screen word details"
```

---

### Task 6: Final regression and handoff

**Files:**
- Modify only if verification exposes a scoped defect in files already listed above.

**Interfaces:**
- Consumes: validated trial bundle and completed word detail page.
- Produces: verification evidence and user handoff; no release artifact.

- [ ] **Step 1: Run the complete Python suite**

Run: `python -m unittest discover -s tools/tests -v`

Expected: PASS.

- [ ] **Step 2: Run Android verification required by `AGENTS.md`**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
```

Expected: all tasks succeed.

- [ ] **Step 3: Verify scope and repository hygiene**

```powershell
git status --short
git diff --check
git check-ignore tmp/audio-trial .superpowers tools/.piper-models
```

Expected: no generated audio/model/visual-companion files are staged; ignored paths are reported; only intentional source and documentation changes remain.

- [ ] **Step 4: Manually verify the two critical flows**

On API 29 or API 36, start a learning session, open an ordinary expression and a purple segmented expression, play each, return using the system gesture, and confirm the same card remains. Exit detail while audio is playing and confirm playback stops.

- [ ] **Step 5: Report the trial bundle without replacing production assets**

Provide the absolute path to `tmp/audio-trial/README.md` and `trial_report.json`, identify any missing required term, and request the user's A/B decision. State explicitly that packaged audio and release version remain unchanged.