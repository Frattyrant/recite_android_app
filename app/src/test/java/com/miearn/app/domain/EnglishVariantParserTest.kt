package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishVariantParserTest {
    @Test
    fun splitsAllConfiguredSeparators() {
        assertEquals(
            listOf("fixture", "jig", "checking fixture"),
            EnglishVariantParser.parse(" fixture；jig; checking fixture "),
        )
        assertEquals(
            listOf("support, pad", "net"),
            EnglishVariantParser.parse("support, pad/net"),
        )
    }

    @Test
    fun termKeepsMultiwordVariantsAndUnitsTogether() {
        assertEquals(
            listOf("support and clamp block", "NC block"),
            EnglishVariantParser.parse("support and clamp block; NC block"),
        )
        assertEquals(listOf("clamp arm"), EnglishVariantParser.parse("clamp arm"))
        assertEquals(listOf("Read at 300mm/s"), EnglishVariantParser.parse("Read at 300mm/s"))
    }
    @Test
    fun removesEmptySegmentsAndPreservesOrder() {
        assertEquals(
            listOf("first", "second"),
            EnglishVariantParser.parse("； first ;; second ；"),
        )
    }

    @Test
    fun phraseKeepsWordsTogetherAndSplitsCompleteSentences() {
        assertEquals(
            listOf(
                "For all robots, use only 300mm/s for gluing.",
                "With 600mm/s, the quality will not be fine.",
                "We have to reduce the speed.",
            ),
            EnglishVariantParser.parse(
                "For all robots, use only 300mm/s for gluing. " +
                    "With 600mm/s, the quality will not be fine. We have to reduce the speed.",
                kind = "PHRASE",
            ),
        )
    }

    @Test
    fun phraseSplitsSlashSeparatedSentenceAlternativesButNotUnits() {
        assertEquals(
            listOf(
                "Sorry, come again?",
                "I didn't follow you, could you repeat?",
                "Read at 300mm/s.",
            ),
            EnglishVariantParser.parse(
                "Sorry, come again?/I didn't follow you, could you repeat?/Read at 300mm/s.",
                kind = "PHRASE",
            ),
        )
    }

    @Test
    fun speechTextNeverContainsSpokenSlashCharacters() {
        assertEquals("300mm s", EnglishVariantParser.toSpeechText("300mm/s"))
        assertEquals("gun gripper", EnglishVariantParser.toSpeechText("gun\\gripper"))
    }

    @Test
    fun termDropsAnnotationOnlyAndPunctuationOnlyFragments() {
        assertEquals(
            listOf("fixture", "jig", "242"),
            EnglishVariantParser.parse("fixture / （中文注释） / - / jig / 242"),
        )
    }

    @Test
    fun infersImportedSentencesWithoutTreatingMultiwordTermsAsSentences() {
        assertEquals("TERM", EnglishVariantParser.inferKind("fixture"))
        assertEquals("TERM", EnglishVariantParser.inferKind("support and clamp block"))
        assertEquals("PHRASE", EnglishVariantParser.inferKind("Can you repeat that?"))
        assertEquals(
            "PHRASE",
            EnglishVariantParser.inferKind("We need to inspect the fixture before production"),
        )
    }
}
