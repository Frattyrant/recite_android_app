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

The generated launcher foreground uses the user-provided illustration as its
visual source.

The packaged offline Ogg files were synthesized with `piper-tts` 1.4.2 using
the `en_US-lessac-medium` voice:

- Piper generation tool: https://github.com/OHF-Voice/piper1-gpl,
  GPL-3.0-or-later. The tool is used during asset generation and is not
  packaged in the application.
- Voice model source:
  https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/medium.
  The `rhasspy/piper-voices` repository declares the MIT License. The ONNX
  model and its JSON configuration are generation inputs and are not packaged
  in the application.
- The voice model card identifies the Lessac Blizzard 2013 dataset and links
  its research license:
  https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html.

These notices identify the generation tool, model provenance, and upstream
licenses; they do not relicense upstream recordings, the model, or generated
audio.
