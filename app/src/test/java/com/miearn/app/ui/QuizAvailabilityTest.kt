package com.miearn.app.ui

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizAvailabilityTest {
    @Test
    fun choiceModeNeedsTwoDistinctNormalizedAnswers() {
        val first = word("fixture;jig", "fixture", "夹具")
        val sameMeaning = word("clamp", "clamp", " 夹具 ")
        val different = word("sensor", "sensor", "传感器")

        assertFalse(
            QuizAvailability.hasEnoughChoiceAnswers(
                QuizMode.EN_TO_ZH,
                listOf(first, sameMeaning),
            ),
        )
        assertTrue(
            QuizAvailability.hasEnoughChoiceAnswers(
                QuizMode.EN_TO_ZH,
                listOf(first, different),
            ),
        )
        assertTrue(
            QuizAvailability.hasEnoughChoiceAnswers(
                QuizMode.SPELLING,
                listOf(first),
            ),
        )
    }

    private fun word(english: String, primary: String, chinese: String) = WordEntity(
        id = english,
        category = "custom",
        categoryLabel = "测试词库",
        sourceIndex = 1,
        kind = "TERM",
        section = "",
        english = english,
        primaryEnglish = primary,
        phonetic = "",
        chinese = chinese,
        note = "",
        exampleEn = "",
        exampleZh = "",
        audioText = english,
        audioAsset = "",
    )
}
