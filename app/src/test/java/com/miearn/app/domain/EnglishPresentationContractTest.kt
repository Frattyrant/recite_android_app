package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishPresentationContractTest {
    @Test
    fun pureFactorySelectsOneCompleteVariant() {
        val presentation = EnglishPresentation.from(
            english = "fixture;jig;checking fixture",
            primaryEnglish = "jig",
            kind = "TERM",
        )

        assertEquals(EnglishPresentation.Variant(1, "jig"), presentation.primary)
        assertEquals(
            listOf("fixture", "checking fixture"),
            presentation.alternatives.map(EnglishPresentation.Variant::text),
        )
    }

    @Test
    fun phraseFactoryMarksAdditionalSentencesAsRelatedPhrases() {
        val presentation = EnglishPresentation.from(
            english = "Can you repeat that? Could you say it again?",
            primaryEnglish = "Can you repeat that?",
            kind = "PHRASE",
        )

        assertEquals(
            EnglishPresentation.AlternativeKind.RELATED_PHRASES,
            presentation.alternativeKind,
        )
    }
}
