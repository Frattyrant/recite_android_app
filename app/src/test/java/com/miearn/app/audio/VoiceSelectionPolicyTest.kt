package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSelectionPolicyTest {
    @Test
    fun selectsLocalUsEnglishBeforeOtherLocalEnglishVoices() {
        val british = VoiceCandidate("british", "en-GB", requiresNetwork = false)
        val american = VoiceCandidate("american", "en-US", requiresNetwork = false)

        assertEquals(
            american,
            VoiceSelectionPolicy.select(listOf(british, american)),
        )
    }

    @Test
    fun ignoresNetworkUsVoiceAndFallsBackToLocalEnglish() {
        val networkAmerican = VoiceCandidate("cloud-us", "en-US", requiresNetwork = true)
        val localBritish = VoiceCandidate("local-gb", "en-GB", requiresNetwork = false)

        assertEquals(
            localBritish,
            VoiceSelectionPolicy.select(listOf(networkAmerican, localBritish)),
        )
    }

    @Test
    fun returnsNullWhenNoLocalEnglishVoiceExists() {
        assertNull(
            VoiceSelectionPolicy.select(
                listOf(
                    VoiceCandidate("cloud-us", "en-US", requiresNetwork = true),
                    VoiceCandidate("local-cn", "zh-CN", requiresNetwork = false),
                ),
            ),
        )
    }

    @Test
    fun languageMatchingIsCaseInsensitiveAndAcceptsUnderscoreTags() {
        val american = VoiceCandidate("american", "EN_us", requiresNetwork = false)

        assertEquals(american, VoiceSelectionPolicy.select(listOf(american)))
    }

    @Test
    fun baseEnglishLocaleIsAValidLocalEnglishFallback() {
        val english = VoiceCandidate("local-english", "en", requiresNetwork = false)

        assertEquals(english, VoiceSelectionPolicy.select(listOf(english)))
    }
}
