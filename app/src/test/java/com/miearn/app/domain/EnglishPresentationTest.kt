package com.miearn.app.domain

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishPresentationTest {
    @Test
    fun primaryEnglishSelectsOneCompleteVariantAndPreservesAlternativeOrder() {
        val presentation = EnglishPresentation.from(
            word(
                english = "fixture;jig;checking fixture",
                primaryEnglish = "jig",
            ),
        )

        assertEquals(EnglishPresentation.Variant(1, "jig"), presentation.primary)
        assertEquals(
            listOf(
                EnglishPresentation.Variant(0, "fixture"),
                EnglishPresentation.Variant(2, "checking fixture"),
            ),
            presentation.alternatives,
        )
    }

    @Test
    fun missingPrimaryEnglishFallsBackToFirstCompleteVariant() {
        val presentation = EnglishPresentation.from(
            word(
                english = "support and clamp block;NC block",
                primaryEnglish = "support",
            ),
        )

        assertEquals(
            EnglishPresentation.Variant(0, "support and clamp block"),
            presentation.primary,
        )
        assertEquals(
            listOf(EnglishPresentation.Variant(1, "NC block")),
            presentation.alternatives,
        )
    }

    @Test
    fun phraseKeepsFirstSentenceAsPrimaryAndLabelsTheRestAsRelatedPhrases() {
        val presentation = EnglishPresentation.from(
            word(
                english = "Can you repeat that? Could you say it again?",
                primaryEnglish = "Can you repeat that?",
                kind = "PHRASE",
            ),
        )

        assertEquals("Can you repeat that?", presentation.primary.text)
        assertEquals(
            EnglishPresentation.AlternativeKind.RELATED_PHRASES,
            presentation.alternativeKind,
        )
        assertEquals(
            listOf("Could you say it again?"),
            presentation.alternatives.map(EnglishPresentation.Variant::text),
        )
    }

    @Test
    fun singleExpressionHasNoAlternatives() {
        val presentation = EnglishPresentation.from(
            word(
                english = "clamp arm",
                primaryEnglish = "clamp arm",
            ),
        )

        assertEquals("clamp arm", presentation.primary.text)
        assertEquals(emptyList<EnglishPresentation.Variant>(), presentation.alternatives)
    }

    private fun word(
        english: String,
        primaryEnglish: String,
        kind: String = "TERM",
    ) = WordEntity(
        id = "test",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 1,
        kind = kind,
        section = "",
        english = english,
        primaryEnglish = primaryEnglish,
        phonetic = "",
        chinese = "测试",
        note = "",
        exampleEn = "",
        exampleZh = "",
        audioText = english,
        audioAsset = "",
    )
}
