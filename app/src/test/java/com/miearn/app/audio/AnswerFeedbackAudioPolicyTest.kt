package com.miearn.app.audio

import android.media.AudioAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerFeedbackAudioPolicyTest {
    @Test
    fun feedbackUsesAssistanceSonificationVolume() {
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, AnswerFeedbackAudioPolicy.usage)
        assertEquals(
            AudioAttributes.CONTENT_TYPE_SONIFICATION,
            AnswerFeedbackAudioPolicy.contentType,
        )
    }
}
