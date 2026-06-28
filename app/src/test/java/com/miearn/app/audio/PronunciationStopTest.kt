package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationStopTest {
    @Test
    fun stopCancelsCurrentAttemptAndIgnoresLateCallbacks() {
        val driver = RecordingDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(
            SpeechRequest("w1", "fixture", "audio/w1.ogg"),
            assetAvailable = false,
        )
        val utteranceId = driver.utteranceId
        executor.onTtsStarted(utteranceId)

        executor.stop()
        executor.onTtsFinished(utteranceId)
        executor.onTtsStarted(utteranceId)

        assertEquals(listOf("stop", "speak", "stop"), driver.events)
        assertEquals(PronunciationStatus.READY, executor.status.value)
    }

    private class RecordingDriver : AudioPlaybackDriver {
        val events = mutableListOf<String>()
        lateinit var utteranceId: String

        override fun stopPlayback() {
            events += "stop"
        }

        override fun playAsset(assetPath: String, token: Long) = Unit

        override fun speak(text: String, utteranceId: String): Boolean {
            events += "speak"
            this.utteranceId = utteranceId
            return true
        }

        override fun release() = Unit
    }
}
