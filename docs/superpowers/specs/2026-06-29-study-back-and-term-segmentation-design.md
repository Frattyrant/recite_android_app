# Study Back Navigation and Technical Term Segmentation Design

## Goal

Improve two parts of the MIearn experience:

1. System back, including full-screen edge-swipe navigation, closes the active
   study flow and returns to the learning home instead of exiting the app.
2. Mechanical and electrical multiword terms remain intact while clear
   alternatives stay individually clickable and pronounceable.

Online terminology comparison occurs only during development. The shipped app
remains offline and does not request network permission.

## Scope

This project has two independently testable workstreams:

- study back navigation;
- build-time terminology segmentation and evidence-backed English corrections.

The terminology work corrects segmentation boundaries and obvious English
errors. It does not broadly rewrite Chinese translations or phonetics.

## Current Findings

`MIearnApp` renders `StudyScreen` directly whenever `studyState` is not idle,
but no Compose system back handler is registered. The system therefore handles
the gesture at the Activity level and exits the app.

`EnglishVariantParser` and `tools/generate_variant_audio.py` classify
mechanical and electrical content as `TERM`. Their TERM separator treats every
whitespace run as a boundary. This breaks valid terms such as `clamp arm`,
`limit switch`, `Electrical Control Module`, and `power factor` into isolated
words.

The packaged corpus contains 1,227 mechanical and 970 electrical records.
There are 1,681 multiword records in those categories. A comparison of the
current and proposed delimiter rules indicates that about 1,678 records have
different logical segmentation.

## Study Back Navigation

When `studyState` is anything other than `StudyUiState.Idle`, `MIearnApp`
registers a Compose `BackHandler`. The handler calls the same
`MainViewModel.closeStudy()` function used by the visible close action.

This applies to loading, resume prompt, active study, and completion states.
The result is consistent for:

- the Android full-screen edge-swipe gesture;
- the system back button;
- the study close button.

Closing stops active audio and returns to the learning home. Existing session
persistence behavior remains unchanged.

Back handling is limited to the study flow in this project. Other full-screen
destinations can adopt the same pattern separately without expanding this
change.

## Canonical Segmentation Contract

Whitespace inside a TERM is content, not a delimiter. Mechanical and electrical
terms split only at explicit alternative boundaries:

- ASCII and full-width semicolons;
- slash or backslash when it separates alternatives;
- never at ordinary whitespace.

For TERM records, slash and backslash are alternative delimiters unless they
match the existing numeric-unit pattern, such as `300mm/s`. Ambiguous source
uses must be resolved through an evidence-backed ledger override rather than a
new heuristic. Annotation-only fragments and punctuation-only fragments remain
non-pronounceable and are discarded. PHRASE sentence segmentation retains its
current behavior.

Examples:

| Source text | Logical variants |
| --- | --- |
| `clamp arm` | `clamp arm` |
| `support and clamp block; NC block` | `support and clamp block`, `NC block` |
| `laser sensor; optical sensor` | `laser sensor`, `optical sensor` |
| `Read at 300mm/s.` | `Read at 300mm/s.` |

The Kotlin runtime parser and Python audio generator implement the same
contract. Existing full-corpus parity tests ensure display, TTS requests, and
packaged segment audio agree.

## Content and Database Strategy

No Room schema field is added. The canonical `english` field continues to
store the source text, with explicit punctuation delimiting alternatives and
spaces preserved inside terms. This avoids a database migration and preserves
custom-import compatibility.

Content generation applies approved corrections after stable IDs are computed.
As a result, corrected built-in terminology keeps the same word IDs and user
progress records remain associated with the same content.

The build-time audit emits explicit variant lists for review and for audio
manifest validation. These lists are derived data and are not duplicated in
the Room entity.

## Online Terminology Audit

Online lookup is a separate development tool, not part of a normal content
build. Network failure cannot make the ordinary build non-reproducible.

The audit process:

1. scans mechanical and electrical source records for suspicious spelling,
   annotation contamination, unusual punctuation, or ambiguous alternatives;
2. emits candidates without modifying the corpus;
3. records approved corrections in a committed correction ledger;
4. applies only ledger entries during deterministic content generation.

Each ledger entry records:

- category and source index;
- original English;
- corrected English;
- correction reason;
- evidence URL or URLs;
- source organization;
- access date.

A correction requires either:

- one applicable standards-body terminology source; or
- two independent official manufacturer or technical authority sources.

Electrical terminology prioritizes the IEC International Electrotechnical
Vocabulary:

<https://www.electropedia.org/>

Mechanical terminology prioritizes official equipment, automation, pneumatic,
robotics, and manufacturing documentation from relevant manufacturers or
technical authorities. General search popularity is not accepted as evidence.

The project stores corrections, citations, and short reasons. It does not copy
external definitions or bulk terminology datasets. This follows IEC's stated
permission to reference Electropedia for identifying or clarifying terms while
avoiding bulk duplication.

Evidence that does not meet the threshold leaves the original content
unchanged and marks the candidate for later review. The tool never guesses.

## Audio Synchronization

After the segmentation contract changes:

- records that become one complete multiword term reuse their existing full
  audio asset;
- obsolete per-word segment metadata and stale variant clips are removed;
- records that still contain multiple alternatives regenerate only the
  affected alternative clips;
- the full audio asset remains the complete-entry pronunciation.

The audio manifest validator must reject differences among UI variants, TTS
segments, manifest segment text, and variant asset paths.

## Error Handling and Safety

- System back is ignored by this feature when no study flow is active, allowing
  normal Activity behavior.
- Empty or punctuation-only variants are discarded.
- A correction ledger entry with missing evidence, mismatched original text,
  duplicate identity, or unknown category fails validation.
- Online lookup errors produce an audit failure or unresolved candidate; they
  never silently change content.
- Generated content keeps stable IDs, category counts, and required-field
  invariants.
- The Android manifest remains free of the `INTERNET` permission.

## Testing

Implementation follows test-driven development.

Navigation tests cover:

- active study back resolves to `closeStudy`;
- resume, loading, and completion states use the same behavior;
- idle home does not intercept system back;
- closing study stops audio and leaves the selected learning tab visible.

Segmentation tests cover:

- multiword mechanical and electrical terms remain intact;
- semicolon-separated multiword alternatives split correctly;
- slash-separated alternatives split while units remain intact;
- PHRASE sentence behavior does not regress;
- Kotlin and Python implementations return matching variants.

Content and audio tests cover:

- correction ledger schema and evidence requirements;
- stable IDs before and after approved English corrections;
- all 2,704 content records satisfy parser/audio-manifest parity;
- obsolete segment assets are removed;
- no Android network permission is introduced.

Repository verification runs the focused Kotlin and Python tests first, then
the full unit test suite, lint, Debug assembly, and signed Release verification
when local signing is available.

## Acceptance Criteria

- Edge-swipe back from every study state returns to the learning home without
  terminating the app.
- The close button and system back share one behavior.
- Multiword terms display and pronounce as complete units.
- Explicit alternatives remain individually clickable.
- Evidence-backed English corrections are present in the generated offline
  corpus and are traceable to the correction ledger.
- Ambiguous candidates remain unchanged.
- Existing study progress survives the content update.
- The app starts and functions without network access or network permission.
