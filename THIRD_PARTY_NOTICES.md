# Third-party notices

MIearn uses open-source Android libraries distributed through their respective
Maven artifacts:

- Android Gradle Plugin and Jetpack Compose — Android Open Source Project,
  Apache License 2.0.
- AndroidX Room, Lifecycle, DataStore, Navigation3 and Test — Android Open
  Source Project, Apache License 2.0.
- AndroidX Media3 — Android Open Source Project, Apache License 2.0.
- Kotlin and kotlinx.coroutines — JetBrains and contributors, Apache License
  2.0.
- Robolectric — Robolectric contributors, MIT License.
- JUnit 4 — Eclipse Foundation and contributors, Eclipse Public License 1.0.
MIearn packages a reduced derivative of ECDICT for offline phonetic and
Chinese-definition completion:

- Project: https://github.com/skywind3000/ECDICT
- License: MIT
- Pinned source revision: `bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b`
- Source CSV SHA-256:
  `1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf`
- The packaged derivative contains 120,000 selected entries and omits the
  upstream definition, detail, audio, ranking and tag fields.

The generated launcher foreground uses the user-provided illustration as its
visual source.

The packaged offline Ogg files were synthesized with `piper-tts` 1.4.2 using
the `en_US-lessac-high` voice at 40 kbps Opus:

- Piper generation tool: https://github.com/OHF-Voice/piper1-gpl,
  GPL-3.0-or-later. The tool is used during asset generation and is not
  packaged in the application.
- Voice model source:
  https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/high.
  The `rhasspy/piper-voices` repository declares the MIT License. The ONNX
  model and its JSON configuration are generation inputs and are not packaged
  in the application.
- The voice model card identifies the Lessac Blizzard 2013 dataset and links
  its research license:
  https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html.

The adopted production voice uses the `en_US-lessac-high` model from the same
Piper voice repository. Its ONNX SHA-256 is
`4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09`.
The model is stored outside the repository, used only at build time, and is
not packaged in the application. MIearn packages only the generated Ogg/Opus
audio assets.

General American IPA is generated from the pinned `en_US` data in
`open-dict-data/ipa-dict`:

- Project: https://github.com/open-dict-data/ipa-dict
- License: MIT
- Pinned revision: `43c3570eb3553bdd19fccd2bd0091534889af023`
- Source file SHA-256:
  `a20b0131b6e98d30fc4f40bc3f82184fe40105ee4a87525a7fe417ef67420f78`
- The English US data is based on the CMU Pronouncing Dictionary conversion
  credited by the upstream project. Terms absent from this snapshot use the
  bundled eSpeak-ng en-US phonemizer and are recorded in the V2.31 IPA audit.

Seven exact U.S.-English pronunciation recordings sourced from Wikimedia
Commons are used for `bench`, `bin`, `flimsy`, `I`, `label`, `O`, and `thread`.
Each source page declares the recording public domain. Source URL, description
page, uploader, original file hash, and accent evidence are preserved in
`app/src/main/assets/content/audio_attributions_v231.json`. All other bundled
pronunciation audio uses the Piper fallback described above.
These notices identify the generation tool, model provenance, and upstream
licenses; they do not relicense upstream recordings, the model, or generated
audio.
