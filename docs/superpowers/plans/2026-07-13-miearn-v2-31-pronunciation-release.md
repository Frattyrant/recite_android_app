# MIearn V2.31 Pronunciation Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship MIearn V2.31 with corrected source content, complete General American IPA, legally redistributable verified pronunciation audio, varied examples, and automatic second-pass advancement after an answer.

**Architecture:** Keep Android runtime offline and preserve existing stable IDs for every valid built-in entry. Rebuild content from the reviewed source, remove six non-learning headings, resolve IPA from a pinned US-English dictionary plus reviewed overrides, prefer exact verified CC0 human recordings, and synthesize every remaining entry with Piper Lessac High using corrected spoken text. Validate all audio offline with manifest, decode, silence, duration, source-license, text-plan, and ASR audit gates before promotion.

**Tech Stack:** Python 3, openpyxl/pdfplumber, `open-dict-data/ipa-dict` General American data, Wikimedia Commons/Lingua Libre Action API, Piper `en_US-lessac-high`, FFmpeg/FFprobe, local ASR audit, Kotlin, Jetpack Compose, Room, Coroutines, Gradle Kotlin DSL.

## Global Constraints

- Image insertion is explicitly excluded from V2.31.
- Built-in content contains exactly 2,698 learnable records: mechanical 1,227; electrical 970; customer review 246; meeting 57; business 198.
- Existing IDs for retained entries do not change; only the six invalid heading IDs disappear.
- Display pronunciation is General American IPA and contains no placeholder such as `phonetic not available`.
- Human audio is bundled only when the exact text, English language, reusable license, source URL, author/speaker, and file hash are recorded.
- Unverified or non-US human recordings are rejected; Piper Lessac High is the mandatory fallback.
- Complete multi-expression audio preserves 500 ms silence; independent variant assets contain only the selected expression.
- Audio remains mono 48 kHz Ogg/Opus at 40 kbps; no runtime network permission or runtime model is added.
- The app version becomes `versionName = "2.31"` and `versionCode = 8`.
- Release APK must remain below the existing 55,000,000-byte release gate and contain no `INTERNET` permission.
- User progress for every retained stable ID and every custom word must survive content upgrade.

---

### Task 1: Establish the V2.31 branch and baseline

**Files:**
- Create: `docs/superpowers/plans/2026-07-13-miearn-v2-31-pronunciation-release.md`
- Inspect: `app/src/main/assets/content/words_v1.json`
- Inspect: `app/src/main/assets/content/audio_manifest_v1.json`

**Interfaces:**
- Consumes: clean `main` at v2.3 plus the reviewed source workbook.
- Produces: branch `codex/v2.31-pronunciation` with recorded baseline test evidence.

- [ ] **Step 1: Create the feature branch in the current user-selected worktree**

```powershell
git switch -c codex/v2.31-pronunciation
```

Expected: current branch is `codex/v2.31-pronunciation`; `outputs/` remains untracked and is not committed.

- [ ] **Step 2: Run the Python baseline**

```powershell
python -m unittest discover -s tools/tests -v
```

Expected: exit code 0.

- [ ] **Step 3: Run the Android baseline**

```powershell
.\gradlew.bat test
```

Expected: exit code 0.

---

### Task 2: Make the cleaned 2,698-record source reproducible

**Files:**
- Create: `tools/clean_source_content.py`
- Create: `tools/data/source_content_repairs.json`
- Create: `tools/tests/test_clean_source_content.py`
- Modify: `tools/build_content.py`
- Modify: `tools/tests/test_content_pipeline.py`
- Modify: `app/src/main/assets/content/words_v1.json`

**Interfaces:**
- Consumes: `专业英语.xlsx`, existing PDF, current word IDs, and reviewed field repairs.
- Produces: `build_content(...)->list[dict]` with exactly 2,698 entries and an audit showing six excluded headings.

- [ ] **Step 1: Write failing tests for the corrected counts and exclusions**

```python
def test_clean_content_excludes_non_learning_headings_without_renumbering_valid_rows():
    records = build_fixture_content()
    assert len(records) == 2698
    assert Counter(row["category"] for row in records) == {
        "mechanical": 1227,
        "electrical": 970,
        "customer_review": 246,
        "meeting": 57,
        "business": 198,
    }
    assert not any(row["english"] in {"Customer", "MINO USA", "Proton", "Common Meeting English Expressions"} for row in records)
    assert retained_fixture_id(records, "customer_review", 63) == retained_fixture_id_from_v23()
```

- [ ] **Step 2: Verify the test fails against the current 2,704-record builder**

```powershell
python -m unittest tools.tests.test_clean_source_content tools.tests.test_content_pipeline -v
```

Expected: failure reports 2,704 instead of 2,698 and identifies title rows.

- [ ] **Step 3: Add reviewed source repairs and title filtering**

`source_content_repairs.json` records the exact original value, corrected English/Chinese/note, reason, and source cell for AGV, model beam, C gun, X/P gun, FDS, flange, and the two source rows whose English exists only in the reviewed PDF translation. `build_content.py` applies repairs before stable-ID generation and filters only the six audited headings while retaining original `sourceIndex` values.

- [ ] **Step 4: Rebuild content and verify green**

```powershell
python tools/build_content.py --source-dir "<local-source-directory>" --output app/src/main/assets/content/words_v1.json --report build/reports/content-v231.json
python -m unittest tools.tests.test_clean_source_content tools.tests.test_content_pipeline -v
```

Expected: 2,698 records, zero empty required fields, zero title records, and retained IDs unchanged.

---

### Task 3: Preserve progress while removing obsolete built-in rows

**Files:**
- Modify: `app/src/main/java/com/miearn/app/data/local/Daos.kt`
- Modify: `app/src/main/java/com/miearn/app/data/seed/ContentSeeder.kt`
- Create: `app/src/test/java/com/miearn/app/data/ContentSeederUpgradeTest.kt`

**Interfaces:**
- Consumes: seed IDs from `SeedJsonParser` and existing built-in IDs from Room.
- Produces: `WordDao.builtInIds(): List<String>` and `WordDao.deleteBuiltInIds(ids: List<String>): Int` used in chunks of at most 250.

- [ ] **Step 1: Write a failing Room upgrade test**

```kotlin
@Test
fun contentUpgradeRemovesOnlyObsoleteBuiltInsAndPreservesRetainedProgressAndCustomWords() = runTest {
    seedV23WithInvalidHeadingAndProgress()
    seedV231WithoutInvalidHeading()

    assertNull(wordDao.wordById(INVALID_HEADING_ID))
    assertNotNull(wordDao.wordById(RETAINED_ID))
    assertEquals(4, progressDao.progress(RETAINED_ID)?.repetitions)
    assertNotNull(wordDao.wordById(CUSTOM_ID))
}
```

- [ ] **Step 2: Run the focused test and observe the obsolete row remains**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.miearn.app.data.ContentSeederUpgradeTest
```

Expected: FAIL because `ContentSeeder` currently only upserts.

- [ ] **Step 3: Delete only obsolete built-in IDs inside the existing transaction**

```kotlin
val seedIds = seed.words.mapTo(hashSetOf()) { it.id }
database.wordDao().builtInIds()
    .filterNot(seedIds::contains)
    .chunked(250)
    .forEach { database.wordDao().deleteBuiltInIds(it) }
```

- [ ] **Step 4: Verify the Room test passes**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.miearn.app.data.ContentSeederUpgradeTest
```

Expected: PASS with retained progress and custom rows unchanged.

---

### Task 4: Generate auditable General American IPA

**Files:**
- Create: `tools/pronunciation/ipa_lexicon.py`
- Create: `tools/pronunciation/build_ipa_coverage.py`
- Create: `tools/pronunciation/ipa_overrides.json`
- Create: `tools/pronunciation/source_provenance.json`
- Create: `tools/tests/test_ipa_lexicon.py`
- Create: `tools/tests/test_ipa_coverage.py`
- Modify: `app/src/main/assets/content/words_v1.json`
- Modify: `THIRD_PARTY_NOTICES.md`

**Interfaces:**
- Consumes: pinned `open-dict-data/ipa-dict` `en_US` snapshot, content variants, and reviewed technical overrides.
- Produces: `resolve_ipa(text: str, kind: str)->IpaResolution` and full-entry phonetics formatted as `/.../； /.../` for variants.

- [ ] **Step 1: Write failing tests for known bad and technical cases**

```python
def test_general_american_ipa_resolves_known_terms_and_acronyms():
    assert resolve_ipa("fixture").display == "/ˈfɪkstʃɚ/"
    assert resolve_ipa("jig").display == "/dʒɪɡ/"
    assert resolve_ipa("mylar").display == "/ˈmaɪlɑɹ/"
    assert resolve_ipa("PLC").display == "/ˌpiː ɛl ˈsiː/"
    assert resolve_ipa("GD&T").display == "/ˌdʒiː diː ən ˈtiː/"
```

- [ ] **Step 2: Verify failure because the current PDF phonetics are used directly**

```powershell
python -m unittest tools.tests.test_ipa_lexicon tools.tests.test_ipa_coverage -v
```

- [ ] **Step 3: Implement dictionary-first token resolution**

Use exact phrase override, exact term override, token dictionary, letter/number expansion, then pinned eSpeak-ng General American fallback. Each resolution records source (`ipa-dict`, `reviewedOverride`, or `espeakFallback`), input hash, and unresolved tokens. Never copy the old PDF IPA into output.

- [ ] **Step 4: Apply IPA to every content variant and write the audit**

```powershell
python tools/pronunciation/build_ipa_coverage.py --content app/src/main/assets/content/words_v1.json --ipa-dict D:\Android\Dictionaries\ipa-dict\en_US.txt --output-report build/reports/ipa-v231.json
```

Expected: 2,698 entries covered, zero blank/placeholder phonetics, zero malformed slash groups, and every variant count matches its IPA group count.

---

### Task 5: Retrieve and license exact open human recordings

**Files:**
- Create: `tools/pronunciation/commons_audio.py`
- Create: `tools/pronunciation/fetch_open_audio.py`
- Create: `tools/pronunciation/human_audio_allowlist.json`
- Create: `tools/tests/test_commons_audio.py`
- Create: `app/src/main/assets/content/audio_attributions_v231.json`

**Interfaces:**
- Consumes: parsed single-word variants and Wikimedia Commons Action API `imageinfo` metadata.
- Produces: staged exact recordings plus `HumanAudioRecord(text, url, descriptionUrl, speaker, license, sha256, accentEvidence)`.

- [ ] **Step 1: Write failing metadata acceptance tests**

```python
def test_human_audio_requires_exact_text_reusable_license_and_us_accent_evidence():
    assert accept(candidate(exact=True, license="CC0", accent="en-US"))
    assert not accept(candidate(exact=False, license="CC0", accent="en-US"))
    assert not accept(candidate(exact=True, license="unknown", accent="en-US"))
    assert not accept(candidate(exact=True, license="CC BY-SA 4.0", accent="unknown"))
```

- [ ] **Step 2: Verify tests fail before the policy exists**

```powershell
python -m unittest tools.tests.test_commons_audio -v
```

- [ ] **Step 3: Implement cached, rate-limited Commons retrieval**

The fetcher queries exact English pronunciation filenames, reads `url`, `descriptionurl`, `user`, categories and filtered `extmetadata`, accepts only reviewed CC0 candidates with US-accent evidence, writes downloads under `tmp/audio-human-v231`, and never modifies production assets.

- [ ] **Step 4: Build the reviewed allowlist and attribution manifest**

```powershell
python tools/pronunciation/fetch_open_audio.py --content app/src/main/assets/content/words_v1.json --staging tmp/audio-human-v231 --allowlist tools/pronunciation/human_audio_allowlist.json --attributions app/src/main/assets/content/audio_attributions_v231.json
```

Expected: every accepted file has a stable source URL, speaker/uploader, CC0 declaration, accent evidence, byte count, and SHA-256; rejected candidates remain outside assets.

---

### Task 6: Rebuild every audio asset from verified speech plans

**Files:**
- Modify: `tools/audio_profiles.py`
- Modify: `tools/audio_production.py`
- Modify: `tools/generate_audio_production.py`
- Modify: `tools/audio/pronunciation_overrides.json`
- Modify: `tools/validate_audio_production.py`
- Modify: `tools/tests/test_audio_production.py`
- Modify: `tools/tests/test_validate_audio_production.py`
- Modify: `app/src/main/assets/audio/`
- Modify: `app/src/main/assets/content/audio_manifest_v1.json`

**Interfaces:**
- Consumes: corrected content, IPA resolution, accepted human clips, and reviewed Piper spoken-text overrides.
- Produces: schema-3 manifest entries with `sourceType`, `expectedIpa`, speech-plan hash, licensing metadata, and complete/variant asset hashes.

- [ ] **Step 1: Write failing tests for source priority and slash-free speech input**

```python
def test_audio_plan_prefers_verified_human_clip_then_corrected_piper_fallback():
    human = plan_variant("fixture", verified_human("fixture"))
    piper = plan_variant("push/pusher", None)
    assert human.source_type == "human"
    assert piper.source_type == "piper"
    assert piper.spoken_text == "push"
    assert "/" not in piper.spoken_text
```

- [ ] **Step 2: Verify failure against the schema-2 Piper-only planner**

```powershell
python -m unittest tools.tests.test_audio_production tools.tests.test_validate_audio_production -v
```

- [ ] **Step 3: Generate the complete staged pack**

```powershell
python tools/generate_audio_production.py --content app/src/main/assets/content/words_v1.json --human-audio tmp/audio-human-v231 --output tmp/audio-production-v231 --model D:\Android\Models\piper\en_US-lessac-high.onnx --ffmpeg D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe
```

Expected: 2,698 complete files; independent files for every multi-expression variant; no stale 2,704-pack file is promoted.

- [ ] **Step 4: Validate and atomically promote**

```powershell
python tools/validate_audio_production.py --root tmp/audio-production-v231 --content app/src/main/assets/content/words_v1.json --manifest tmp/audio-production-v231/audio_manifest_v1.json --ffprobe D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffprobe.exe --ffmpeg D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe
python tools/promote_audio_production.py --staging tmp/audio-production-v231 --assets app/src/main/assets/audio --manifest app/src/main/assets/content/audio_manifest_v1.json
```

Expected: validation passes before promotion; removed-heading audio and stale variants are deleted.

---

### Task 7: Detect suspicious recordings with an independent audit

**Files:**
- Create: `tools/pronunciation/audit_audio_recognition.py`
- Create: `tools/pronunciation/audio_audit_overrides.json`
- Create: `tools/tests/test_audio_recognition_audit.py`
- Create: `docs/audio/v2.31-release-audit.json`

**Interfaces:**
- Consumes: every staged complete/variant clip, normalized expected transcript, and a local external ASR model.
- Produces: token error ratio, duration outliers, clipping/silence metrics, and explicit reviewed exceptions.

- [ ] **Step 1: Write failing normalization and threshold tests**

```python
def test_audit_flags_spoken_slash_and_wrong_word_but_allows_letter_expansion():
    assert audit("fixture", "fixture").passed
    assert not audit("fixture", "slash fixture").passed
    assert audit("PLC", "P L C").passed
    assert not audit("jig", "gig").passed
```

- [ ] **Step 2: Verify failure before the audit exists**

```powershell
python -m unittest tools.tests.test_audio_recognition_audit -v
```

- [ ] **Step 3: Run independent recognition over all assets**

```powershell
python tools/pronunciation/audit_audio_recognition.py --content app/src/main/assets/content/words_v1.json --manifest app/src/main/assets/content/audio_manifest_v1.json --assets app/src/main/assets/audio --model D:\Android\Models\asr\english-small --report docs/audio/v2.31-release-audit.json
```

Expected: every file is audited; any failure requires a speech-plan correction and regeneration or an explicit word-ID review record with reason. The release gate rejects unreviewed failures.

---

### Task 8: Generate varied manufacturing examples

**Files:**
- Create: `tools/example_generator.py`
- Create: `tools/tests/test_example_generator.py`
- Modify: `tools/build_content.py`
- Modify: `app/src/main/assets/content/words_v1.json`

**Interfaces:**
- Consumes: category, stable ID, primary English, and concise Chinese meaning.
- Produces: deterministic category-aware `exampleEn` and `exampleZh` without changing Room schema.

- [ ] **Step 1: Write failing tests for variety and fill-blank compatibility**

```python
def test_term_examples_are_deterministic_varied_and_contain_primary_term():
    rows = [example_for(word(i)) for i in range(20)]
    assert len({row.example_en for row in rows}) >= 8
    assert all(word(i).primary_english.casefold() in rows[i].example_en.casefold() for i in range(20))
    assert all(rows[i].example_zh for i in range(20))
```

- [ ] **Step 2: Verify failure because each category currently repeats one template**

```powershell
python -m unittest tools.tests.test_example_generator -v
```

- [ ] **Step 3: Add deterministic template families**

Mechanical examples cover fixture setup, dimensional inspection, maintenance, commissioning and production trials. Electrical examples cover wiring, diagnostics, control panels, interlocks and commissioning. Existing phrase categories retain their source sentence as the example.

- [ ] **Step 4: Rebuild content and verify example quality gates**

```powershell
python -m unittest tools.tests.test_example_generator tools.tests.test_content_pipeline -v
```

Expected: no generic single-template repetition and all testable examples can blank the target term.

---

### Task 9: Auto-advance after second-pass answers

**Files:**
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/StudyScreen.kt`
- Create: `app/src/test/java/com/miearn/app/ui/StudyAnswerAdvanceTest.kt`

**Interfaces:**
- Consumes: `LearningSession.submitAnswer()` and `continueAfterAnswer()`.
- Produces: one-answer-only transition that records the answer, plays one feedback sound, briefly exposes correctness for accessibility, and advances without a second tap.

- [ ] **Step 1: Write a failing coroutine test**

```kotlin
@Test
fun consolidateAnswerRecordsFeedbackOnceAndAutomaticallyShowsNextWord() = runTest {
    viewModel.answerStudy(correctChinese)
    advanceTimeBy(350)
    assertEquals(NEXT_WORD_ID, activeState().word.id)
    assertEquals(1, feedbackPlayer.correctPlayCount)
    assertEquals(1, repository.firstAnswerCount)
}
```

- [ ] **Step 2: Verify failure because the current state waits for `continueStudy()`**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.miearn.app.ui.StudyAnswerAdvanceTest
```

- [ ] **Step 3: Advance in the answer coroutine and remove the continue button**

After persistence succeeds, wait 320 ms so the correct/wrong sound and TalkBack announcement are perceptible, call `continueAfterAnswer()`, save the next session, and autoplay the next word. Guard the job so repeated taps, screen closure, or session replacement cannot double-record or advance.

- [ ] **Step 4: Verify focused and domain tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.miearn.app.ui.StudyAnswerAdvanceTest --tests com.miearn.app.domain.LearningSessionTest --tests com.miearn.app.data.ObjectiveLearningRepositoryTest
```

Expected: PASS; no “继续” button remains after an answer.

---

### Task 10: Set V2.31 metadata and run release gates

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `THIRD_PARTY_NOTICES.md`
- Inspect: `.github/workflows/`
- Produce outside Git: `app/build/outputs/apk/release/app-release.apk`

**Interfaces:**
- Consumes: validated content/audio assets and existing environment-injected release signing configuration.
- Produces: signed V2.31 release APK and verification evidence.

- [ ] **Step 1: Set version metadata**

```kotlin
versionCode = 8
versionName = "2.31"
```

- [ ] **Step 2: Run all required project checks**

```powershell
python -m unittest discover -s tools/tests -v
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat verifyDebugApkSize
```

Expected: all commands exit 0.

- [ ] **Step 3: Build and verify the signed release**

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat verifyReleaseApkSize
$aapt = Join-Path $env:ANDROID_SDK_ROOT 'build-tools\36.0.0\aapt.exe'
& $aapt dump permissions app\build\outputs\apk\release\app-release.apk
```

Expected: signed APK exists, is below 55,000,000 bytes, and permissions do not contain `android.permission.INTERNET`.

- [ ] **Step 4: Run final manifest and repository hygiene audit**

```powershell
python tools/validate_audio_production.py --root app/src/main/assets --content app/src/main/assets/content/words_v1.json --manifest app/src/main/assets/content/audio_manifest_v1.json --ffprobe D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffprobe.exe --ffmpeg D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe
git status --short
```

Expected: `PASS entries=2698`; no model, virtual environment, temporary download, cache, keystore, password, APK, or source workbook is staged.

- [ ] **Step 5: Commit the verified release source**

```powershell
git add app tools docs README.md THIRD_PARTY_NOTICES.md
git commit -m "release: prepare MIearn v2.31 pronunciation update"
```

Expected: commit contains only reproducible source, reviewed data, packaged audio, manifests, notices and version metadata.
