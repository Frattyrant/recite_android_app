# MIearn V2 Audio Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every card autoplay reliably, package 2,704 offline pronunciations, and retain a
diagnosable Android TTS fallback.

**Architecture:** A pure Kotlin pronunciation state machine owns initialization races, retries, and
latest-request wins behavior. `AudioPronouncer` executes its actions through Media3 and Android TTS.
Every dictionary entry receives an Ogg asset, so system TTS is a fallback rather than a prerequisite.

**Tech Stack:** Kotlin, Media3 1.10.1, Android TextToSpeech, JUnit 4, Python validation, ffmpeg.

---

The workspace is not a Git repository. After every task, run the named verification checkpoint and
record the result in `output/reports/v2_verification_report.md`.

### Task 1: Reproduce the TTS initialization race

**Files:**
- Create: `app/src/test/java/com/miearn/app/audio/PronunciationStateMachineTest.kt`
- Create: `app/src/main/java/com/miearn/app/audio/PronunciationStateMachine.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationStateMachineTest {
    private val request = SpeechRequest("w1", "fixture", "audio/w1.ogg")

    @Test
    fun requestDuringTtsInitializationIsReplayedWhenTtsBecomesReady() {
        val machine = PronunciationStateMachine()
        assertEquals(PlaybackAction.None, machine.request(request, assetAvailable = false))
        assertEquals(PlaybackAction.Speak(request, retry = false), machine.onTtsReady())
    }

    @Test
    fun onlyLatestInitializationRequestIsReplayed() {
        val machine = PronunciationStateMachine()
        machine.request(request, assetAvailable = false)
        val latest = SpeechRequest("w2", "clamp", "audio/w2.ogg")
        machine.request(latest, assetAvailable = false)
        assertEquals(PlaybackAction.Speak(latest, retry = false), machine.onTtsReady())
    }

    @Test
    fun assetFailureFallsBackToTtsAndTtsFailureRetriesOnce() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        assertEquals(PlaybackAction.PlayAsset(request), machine.request(request, assetAvailable = true))
        assertEquals(PlaybackAction.Speak(request, retry = false), machine.onAssetFailure(request.id))
        assertEquals(PlaybackAction.Speak(request, retry = true), machine.onTtsFailure(request.id))
        assertEquals(PlaybackAction.MarkUnavailable, machine.onTtsFailure(request.id))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.miearn.app.audio.PronunciationStateMachineTest" --offline
```

Expected: compilation fails because `PronunciationStateMachine`, `SpeechRequest`, and
`PlaybackAction` do not exist.

- [ ] **Step 3: Implement the minimal state machine**

```kotlin
package com.miearn.app.audio

data class SpeechRequest(val id: String, val text: String, val assetPath: String)

sealed interface PlaybackAction {
    data object None : PlaybackAction
    data object MarkUnavailable : PlaybackAction
    data class PlayAsset(val request: SpeechRequest) : PlaybackAction
    data class Speak(val request: SpeechRequest, val retry: Boolean) : PlaybackAction
}

enum class PronunciationStatus { INITIALIZING, READY, SPEAKING, UNAVAILABLE }

class PronunciationStateMachine {
    var status = PronunciationStatus.INITIALIZING
        private set
    private var latest: SpeechRequest? = null
    private var ttsFailures = 0

    fun request(request: SpeechRequest, assetAvailable: Boolean): PlaybackAction {
        latest = request
        ttsFailures = 0
        return if (assetAvailable) PlaybackAction.PlayAsset(request)
        else if (status == PronunciationStatus.READY) PlaybackAction.Speak(request, false)
        else PlaybackAction.None
    }

    fun onTtsReady(): PlaybackAction {
        status = PronunciationStatus.READY
        return latest?.let { PlaybackAction.Speak(it, false) } ?: PlaybackAction.None
    }

    fun onAssetFailure(id: String): PlaybackAction =
        latest?.takeIf { it.id == id }?.let { PlaybackAction.Speak(it, false) }
            ?: PlaybackAction.None

    fun onTtsFailure(id: String): PlaybackAction {
        val current = latest?.takeIf { it.id == id } ?: return PlaybackAction.None
        ttsFailures += 1
        return if (ttsFailures == 1) PlaybackAction.Speak(current, true) else {
            status = PronunciationStatus.UNAVAILABLE
            PlaybackAction.MarkUnavailable
        }
    }

    fun onStarted(id: String) {
        if (latest?.id == id) status = PronunciationStatus.SPEAKING
    }

    fun onFinished(id: String) {
        if (latest?.id == id) status = PronunciationStatus.READY
    }
}
```

- [ ] **Step 4: Run the focused and complete unit suites**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.miearn.app.audio.PronunciationStateMachineTest" --offline
.\gradlew.bat testDebugUnitTest --offline
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

### Task 2: Integrate Media3 and Android TTS without silent failures

**Files:**
- Modify: `app/src/main/java/com/miearn/app/audio/AudioPronouncer.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/UiModels.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/StudyScreen.kt`
- Test: `app/src/test/java/com/miearn/app/audio/PronunciationStateMachineTest.kt`

- [ ] **Step 1: Add a failing stale-callback regression test**

```kotlin
@Test
fun callbackFromOldRequestCannotChangeCurrentStatus() {
    val machine = PronunciationStateMachine().apply { onTtsReady() }
    machine.request(request, assetAvailable = false)
    val latest = SpeechRequest("w2", "clamp", "audio/w2.ogg")
    machine.request(latest, assetAvailable = false)
    machine.onStarted("w1")
    assertEquals(PronunciationStatus.READY, machine.status)
}
```

Run the focused test. Expected: FAIL if an old callback changes current status.

- [ ] **Step 2: Make request identity guard every callback**

Keep `onStarted`, `onFinished`, `onAssetFailure`, and `onTtsFailure` conditional on the latest
request ID. Run the focused test until it passes.

- [ ] **Step 3: Replace `AudioPronouncer` with an action executor**

The production class must:

```kotlin
class AudioPronouncer(context: Context) : TextToSpeech.OnInitListener, Player.Listener {
    val status = MutableStateFlow(PronunciationStatus.INITIALIZING)
    private val machine = PronunciationStateMachine()
    private var current: SpeechRequest? = null
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        addListener(this@AudioPronouncer)
    }
    private val tts = TextToSpeech(context.applicationContext, this)

    fun play(word: WordEntity) {
        val request = SpeechRequest(word.id, word.audioText, word.audioAsset)
        current = request
        player.stop()
        tts.stop()
        execute(machine.request(request, assetExists(request.assetPath)))
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS || !selectEnglishVoice()) {
            status.value = PronunciationStatus.UNAVAILABLE
            return
        }
        tts.setSpeechRate(0.92f)
        tts.setPitch(1.0f)
        execute(machine.onTtsReady())
    }
}
```

`selectEnglishVoice()` chooses a non-network `Locale.US` voice first, then any non-network English
voice, then falls back to `setLanguage(Locale.US)`. `UtteranceProgressListener` forwards start,
done, and error callbacks. `Player.Listener.onPlayerError` forwards asset failure. `execute()`
updates `status` and runs only the action returned by the state machine.

- [ ] **Step 4: Surface unavailable TTS only when asset playback also fails**

Add `pronunciationStatus` to `MainViewModel` and `StudyUiState.Active`. `StudyScreen` shows one
compact line below the play button:

```kotlin
if (state.pronunciationStatus == PronunciationStatus.UNAVAILABLE) {
    TextButton(onClick = onOpenTtsSettings) { Text("启用英语语音") }
}
```

Open `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA` through an activity result or guarded
`startActivity`; failure opens Android TTS settings. Do not show a modal dialog.

- [ ] **Step 5: Verify the integration compiles**

Run:

```powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin --offline
```

Expected: `BUILD SUCCESSFUL`, with all pronunciation tests passing.

### Task 3: Generate and validate the complete offline voice pack

**Files:**
- Modify: `tools/generate_audio.ps1`
- Modify: `tools/validate_audio.py`
- Create: `tools/tests/test_audio_manifest.py`
- Create: `app/src/main/assets/audio/*.ogg`
- Create: `output/reports/audio_report.json`
- Modify: `THIRD_PARTY_NOTICES.md`

- [ ] **Step 1: Write the failing manifest test**

```python
import json
import unittest
from pathlib import Path

class AudioManifestTest(unittest.TestCase):
    def test_every_word_has_one_nonempty_ogg(self):
        data = json.loads(
            Path("app/src/main/assets/content/words_v1.json").read_text(encoding="utf-8")
        )
        files = list(Path("app/src/main/assets/audio").glob("*.ogg"))
        self.assertEqual(2704, len(files))
        expected = {Path(w["audioAsset"]).name for w in data["words"]}
        self.assertEqual(expected, {p.name for p in files})
        self.assertTrue(all(p.stat().st_size > 256 for p in files))
```

Run:

```powershell
python -m unittest tools.tests.test_audio_manifest -v
```

Expected: FAIL because the 2,704 Ogg files are absent.

- [ ] **Step 2: Generate speech in deterministic batches**

Run `tools/generate_audio.ps1` in batches of 100 so progress remains visible:

```powershell
& tools\generate_audio.ps1 -Start 0 -Limit 100
& tools\generate_audio.ps1 -Start 100 -Limit 100
```

Continue with increasing `Start` values through `2700`. The script must skip valid existing files,
strip non-English annotations before synthesis, use the same voice and rate for every batch, and
write atomically through a temporary WAV.

- [ ] **Step 3: Validate count and decode every asset**

Run:

```powershell
python tools\validate_audio.py --all
python -m unittest tools.tests.test_audio_manifest -v
```

Expected: `actualCount` and `probedCount` both equal `2704`; every stream is mono Opus and both
commands exit 0.

- [ ] **Step 4: Record voice provenance**

Update `THIRD_PARTY_NOTICES.md` with the actual generator, voice name, model/source URL when
applicable, and its license. Do not claim Piper if Windows SAPI generated the files.

### Task 4: Audio verification checkpoint

**Files:**
- Modify: `output/reports/v2_verification_report.md`

- [ ] Run:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug lintDebug --offline
```

- [ ] Inspect the APK:

```powershell
& .\.android-sdk\build-tools\36.0.0\aapt2.exe dump badging `
  app\build\outputs\apk\debug\app-debug.apk
```

Expected: build succeeds; APK has package `com.miearn.app`, min SDK 29, target SDK 36, and no
`INTERNET` permission.

- [ ] Record unit-test count, audio count, APK bytes, and SHA-256 in the V2 report.

