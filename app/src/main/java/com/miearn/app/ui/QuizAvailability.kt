package com.miearn.app.ui

import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.LearningContentPolicy
import com.miearn.app.domain.QuizContentPolicy

internal object QuizAvailability {
    fun hasEnoughChoiceAnswers(mode: QuizMode, words: List<WordEntity>): Boolean {
        if (mode !in CHOICE_MODES) return true
        val distinctAnswers = words
            .map { word ->
                when (mode) {
                    QuizMode.EN_TO_ZH -> LearningContentPolicy.displayChinese(word.chinese)
                    else -> QuizContentPolicy.primaryEnglish(word)
                }
            }
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .distinct()
        return distinctAnswers.size >= 2
    }

    private val CHOICE_MODES = setOf(
        QuizMode.EN_TO_ZH,
        QuizMode.ZH_TO_EN,
        QuizMode.LISTENING,
    )
}
