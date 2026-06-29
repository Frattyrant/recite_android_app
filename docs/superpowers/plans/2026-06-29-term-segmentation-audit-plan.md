# Technical Term Segmentation and Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve mechanical and electrical multiword terms, apply evidence-backed English corrections at build time, and keep UI, TTS, and packaged audio segmentation consistent.

**Architecture:** Kotlin and Python share a delimiter contract: whitespace stays inside TERM variants, semicolons separate alternatives, and slashes separate alternatives except recognized units. A committed correction ledger contains only reviewed changes with source evidence; deterministic content generation applies the ledger after stable IDs are assigned.

**Tech Stack:** Kotlin, Python standard library, Gradle, JSON, Piper audio tooling

---

### Task 1: Preserve Multiword TERM Variants

**Files:**
- Modify: `app/src/test/java/com/miearn/app/domain/EnglishVariantParserTest.kt`
- Modify: `app/src/test/java/com/miearn/app/domain/EnglishTokenParserV21Test.kt`
- Modify: `tools/tests/test_generate_variant_audio.py`
- Modify: `app/src/main/java/com/miearn/app/domain/EnglishVariantParser.kt`
- Modify: `tools/generate_variant_audio.py`

- [ ] **Step 1: Change Kotlin expectations before production code**

Add assertions:

```kotlin
assertEquals(
    listOf("support and clamp block", "NC block"),
    EnglishVariantParser.parse("support and clamp block; NC block"),
)
assertEquals(
    listOf("clamp arm"),
    EnglishVariantParser.parse("clamp arm"),
)
assertEquals(
    listOf("Read at 300mm/s"),
    EnglishVariantParser.parse("Read at 300mm/s"),
)
```

Update legacy expectations so whitespace no longer splits TERM content.

- [ ] **Step 2: Change Python expectations before production code**

```python
def test_terms_keep_multiword_variants_together(self):
    self.assertEqual(
        ["support and clamp block", "NC block"],
        raw_variants("support and clamp block; NC block", "TERM"),
    )
    self.assertEqual(["clamp arm"], raw_variants("clamp arm", "TERM"))
    self.assertEqual(["Read at 300mm/s"], raw_variants("Read at 300mm/s", "TERM"))
```

- [ ] **Step 3: Run focused tests to verify RED**

Run:

```powershell
python -m unittest tools.tests.test_generate_variant_audio -v
.\gradlew.bat testDebugUnitTest --tests "com.miearn.app.domain.EnglishVariantParserTest" --tests "com.miearn.app.domain.EnglishTokenParserV21Test" --no-daemon --max-workers=1
```

Expected: failures show that multiword terms are still split at whitespace.

- [ ] **Step 4: Implement the Kotlin delimiter contract**

Replace whitespace TERM splitting with semicolon splitting followed by a
unit-aware slash scanner. The scanner yields on `/` or `\` unless the left
side ends in a numeric unit and the right side begins with the recognized
denominator unit.

- [ ] **Step 5: Implement the same Python delimiter contract**

Replace `TERM_SEPARATOR` whitespace behavior with `TERM_SEMICOLON` plus a
`_split_term_slashes()` function equivalent to Kotlin.

- [ ] **Step 6: Run focused tests to verify GREEN**

Run the commands from Step 3.

Expected: all focused Kotlin and Python tests pass.

- [ ] **Step 7: Commit parser behavior**

```powershell
git add app/src/main/java/com/miearn/app/domain/EnglishVariantParser.kt app/src/test/java/com/miearn/app/domain/EnglishVariantParserTest.kt app/src/test/java/com/miearn/app/domain/EnglishTokenParserV21Test.kt tools/generate_variant_audio.py tools/tests/test_generate_variant_audio.py
git commit -m "fix: preserve multiword technical terms"
```

### Task 2: Add an Evidence-Backed Correction Ledger

**Files:**
- Create: `tools/data/term_corrections.json`
- Create: `tools/term_corrections.py`
- Create: `tools/tests/test_term_corrections.py`

- [ ] **Step 1: Write failing ledger validation tests**

Tests must require:

```python
{
    "category": "mechanical",
    "sourceIndex": 13,
    "originalEnglish": "inductance proximity switch",
    "correctedEnglish": "inductive proximity switch",
    "reason": "Use the standard adjective for the sensing principle.",
    "evidence": [
        {"organization": "OMRON", "url": "https://www.ia.omron.com/data_pdf/guide/41/proximity_tg_e_6_2.pdf", "accessedOn": "2026-06-29"},
        {"organization": "SICK", "url": "https://www.sick.com/cz/en/products/detection-sensors/inductive-proximity-sensors/c/g253054", "accessedOn": "2026-06-29"}
    ]
}
```

Also cover source 14 changing `capacity proximity switch` to `capacitive
proximity switch`, and electrical source 151 changing `consistent of` to
`consist of`. Reject duplicate identities, mismatched source text, empty
reasons, non-HTTPS URLs, missing access dates, and fewer than two independent
organizations unless the evidence organization is an approved standards body.

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
python -m unittest tools.tests.test_term_corrections -v
```

Expected: import or file-not-found failure because ledger support is absent.

- [ ] **Step 3: Implement ledger parsing and validation**

Expose:

```python
def load_corrections(path: Path) -> dict[tuple[str, int], TermCorrection]: ...
def validate_correction(correction: TermCorrection) -> None: ...
def apply_correction(record: dict, correction: TermCorrection) -> dict: ...
```

`apply_correction` must verify `record["english"] == originalEnglish`, preserve
`record["id"]`, and update `english`, `primaryEnglish`, `exampleEn`, and
`audioText`.

- [ ] **Step 4: Add the reviewed initial ledger**

Use OMRON and SICK official proximity-sensor pages for the inductive/capacitive
corrections. Use both Merriam-Webster and Cambridge for `consist of`:

```text
https://www.merriam-webster.com/dictionary/consist%20of
https://dictionary.cambridge.org/dictionary/english/consist-of
```

The correction changes only the erroneous alternative inside the full source
151 English field.

- [ ] **Step 5: Run ledger tests to verify GREEN**

Run the command from Step 2.

- [ ] **Step 6: Commit the audited ledger**

```powershell
git add tools/data/term_corrections.json tools/term_corrections.py tools/tests/test_term_corrections.py
git commit -m "feat: add audited terminology corrections"
```

### Task 3: Apply Corrections During Content Generation

**Files:**
- Modify: `tools/build_content.py`
- Modify: `tools/tests/test_content_pipeline.py`
- Modify: `app/src/main/assets/content/words_v1.json`
- Verify: `output/reports/content_report.json`

- [ ] **Step 1: Add failing content-pipeline tests**

Assert that ledger corrections:

- match their original category/source text;
- preserve the stable ID;
- update the four English-derived fields;
- appear in the build report with evidence;
- leave category counts at 1,227 mechanical and 970 electrical.

- [ ] **Step 2: Run content tests to verify RED**

```powershell
python -m unittest tools.tests.test_content_pipeline -v
```

- [ ] **Step 3: Integrate the ledger after stable ID construction**

Load `tools/data/term_corrections.json` once in `build_content()`. Apply a
matching correction after `_make_record()` and before appending the record.
Include the correction identity, before/after text, reason, and evidence in the
build report. Bump `contentVersion` from `2026.06.27` to `2026.06.29` so
existing installations reseed corrected built-in content.

- [ ] **Step 4: Regenerate content deterministically**

```powershell
python tools/build_content.py
```

Expected: 2,704 records, unchanged IDs/counts, corrected English values, and an
auditable report.

- [ ] **Step 5: Run content tests to verify GREEN**

Run the command from Step 2.

- [ ] **Step 6: Commit generated offline content**

```powershell
git add tools/build_content.py tools/tests/test_content_pipeline.py app/src/main/assets/content/words_v1.json
git commit -m "feat: apply audited terminology corrections"
```

### Task 4: Synchronize Packaged Variant Audio

**Files:**
- Modify: `app/src/main/assets/content/audio_manifest_v1.json`
- Modify/Delete/Create: `app/src/main/assets/audio/*.ogg`
- Modify/Delete/Create: `app/src/main/assets/audio/variants/*.ogg`
- Test: `app/src/test/java/com/miearn/app/audio/SpeechAssetPlanTest.kt`
- Test: `tools/tests/test_audio_manifest.py`

- [ ] **Step 1: Run parity tests to verify stale segmentation fails**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.miearn.app.audio.SpeechAssetPlanTest" --no-daemon --max-workers=1
python -m unittest tools.tests.test_audio_manifest -v
```

Expected: failures identify old per-word segments.

- [ ] **Step 2: Regenerate only changed multi-variant audio**

```powershell
python tools/generate_variant_audio.py
```

The generator reuses unchanged assets, removes obsolete segments for terms that
now have one variant, and synthesizes only changed records that retain multiple
alternatives.

- [ ] **Step 3: Validate generated audio**

```powershell
python tools/validate_audio.py
python tools/validate_variant_audio.py
```

Expected: all referenced files exist, text hashes match, and pauses validate.

- [ ] **Step 4: Run parity tests to verify GREEN**

Run the commands from Step 1.

- [ ] **Step 5: Commit synchronized assets**

```powershell
git add app/src/main/assets/content/audio_manifest_v1.json app/src/main/assets/audio
git commit -m "fix: synchronize technical term audio segments"
```

### Task 5: Final Offline Verification

**Files:**
- Verify: `app/src/main/AndroidManifest.xml`
- Verify: all files changed by Tasks 1–4

- [ ] **Step 1: Verify no network permission**

```powershell
rg -n "android.permission.INTERNET" app/src/main/AndroidManifest.xml
```

Expected: no match.

- [ ] **Step 2: Run Python tests**

```powershell
python -m unittest discover -s tools/tests -v
```

- [ ] **Step 3: Run Android verification with constrained resources**

```powershell
.\gradlew.bat test lint assembleDebug --no-daemon --max-workers=1
```

- [ ] **Step 4: Verify repository scope**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and no unintended local files.
