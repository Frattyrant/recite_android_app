package com.miearn.app.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizInputStateTest {
    @Test
    fun onlyTextInputModesRequestKeyboardFocus() {
        assertEquals(true, quizModeUsesTextInput(QuizMode.SPELLING))
        assertEquals(true, quizModeUsesTextInput(QuizMode.FILL_BLANK))
        assertEquals(false, quizModeUsesTextInput(QuizMode.EN_TO_ZH))
        assertEquals(false, quizModeUsesTextInput(QuizMode.ZH_TO_EN))
        assertEquals(false, quizModeUsesTextInput(QuizMode.LISTENING))
    }

    @Test
    fun inputKeySeparatesModeWordAndQuestionPosition() {
        val spelling = quizInputKey(QuizMode.SPELLING, "word-1", 0)
        assertEquals(spelling, quizInputKey(QuizMode.SPELLING, "word-1", 0))
        assertNotEquals(spelling, quizInputKey(QuizMode.FILL_BLANK, "word-1", 0))
        assertNotEquals(spelling, quizInputKey(QuizMode.SPELLING, "word-2", 0))
        assertNotEquals(spelling, quizInputKey(QuizMode.SPELLING, "word-1", 1))
    }
}
