package com.miearn.app.audio

data class VoiceCandidate(
    val name: String,
    val languageTag: String,
    val requiresNetwork: Boolean,
)

object VoiceSelectionPolicy {
    fun select(voices: Iterable<VoiceCandidate>): VoiceCandidate? {
        val localEnglish = voices.filter { candidate ->
            val languageTag = candidate.normalizedLanguageTag()
            !candidate.requiresNetwork && (languageTag == "en" || languageTag.startsWith("en-"))
        }
        return localEnglish.firstOrNull { it.normalizedLanguageTag() == "en-us" }
            ?: localEnglish.firstOrNull()
    }

    private fun VoiceCandidate.normalizedLanguageTag(): String =
        languageTag.replace('_', '-').lowercase()
}
