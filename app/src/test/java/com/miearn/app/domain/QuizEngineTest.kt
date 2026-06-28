package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {
    @Test
    fun spellingIgnoresCaseWhitespaceAndSmartApostrophes() {
        assertTrue(QuizEngine.isSpellingCorrect("  I'M ON BOARD ", "I’m on board"))
    }

    @Test
    fun blankQuestionHidesPrimaryEnglishFromExample() {
        val prompt = QuizEngine.blankExample(
            example = "The engineer verified the limit switch during commissioning.",
            primaryEnglish = "limit switch",
        )

        assertEquals("The engineer verified the ______ during commissioning.", prompt)
    }

    @Test
    fun chineseOptionsAreUniqueDeterministicAndContainAnswer() {
        val first = QuizEngine.chineseOptions(
            answer = "正确",
            candidates = listOf("错误一", "错误一", "正确", "错误二", "错误三", "错误四"),
            seed = 42,
        )
        val second = QuizEngine.chineseOptions(
            answer = "正确",
            candidates = listOf("错误一", "错误一", "正确", "错误二", "错误三", "错误四"),
            seed = 42,
        )

        assertEquals(4, first.size)
        assertEquals(4, first.distinct().size)
        assertTrue("正确" in first)
        assertEquals(first, second)
    }

    @Test
    fun genericChoiceOptionsRemoveDuplicateEnglishDistractors() {
        val options = QuizEngine.choiceOptions(
            answer = "limit switch",
            candidates = listOf(
                "sensor",
                "sensor",
                "limit switch",
                "relay",
                "fixture",
            ),
            seed = 7,
        )

        assertEquals(4, options.size)
        assertEquals(4, options.distinct().size)
        assertTrue("limit switch" in options)
    }
}
