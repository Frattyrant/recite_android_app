package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationOverallAvailabilityTest {
    private val request = SpeechRequest(
        id = "fixture",
        text = "fixture",
        assetPath = "audio/fixture.ogg",
    )

    @Test
    fun ttsInitializationFailureAloneDoesNotMarkPackagedAudioUnavailable() {
        val machine = PronunciationStateMachine()

        machine.onTtsUnavailable()

        assertEquals(PronunciationStatus.READY, machine.status)
        assertTrue(machine.request(request, assetAvailable = true) is PlaybackAction.PlayAsset)
    }

    @Test
    fun missingAssetAfterTtsFailureMarksOverallPlaybackUnavailable() {
        val machine = PronunciationStateMachine()
        machine.onTtsUnavailable()

        val action = machine.request(request, assetAvailable = false)

        assertEquals(PlaybackAction.MarkUnavailable, action)
        assertEquals(PronunciationStatus.UNAVAILABLE, machine.status)
    }
}
