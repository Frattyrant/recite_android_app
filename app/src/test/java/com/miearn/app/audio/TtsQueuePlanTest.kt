package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsQueuePlanTest {
    @Test
    fun insertsFiveHundredMillisecondsBetweenSegments() {
        assertEquals(
            listOf(
                TtsQueueItem.Speak("fixture", isFirst = true, isFinal = false),
                TtsQueueItem.Silence(500),
                TtsQueueItem.Speak("jig", isFirst = false, isFinal = true),
            ),
            TtsQueuePlan.create(listOf("fixture", "jig")),
        )
    }

    @Test
    fun singleSegmentHasNoSilence() {
        assertEquals(
            listOf(TtsQueueItem.Speak("fixture", isFirst = true, isFinal = true)),
            TtsQueuePlan.create(listOf("fixture")),
        )
    }
}
