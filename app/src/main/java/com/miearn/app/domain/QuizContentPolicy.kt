package com.miearn.app.domain

import com.miearn.app.data.local.WordEntity

/**
 * Keeps every quiz mode focused on the same complete primary expression used
 * by the study card, while leaving full expressions available in details.
 */
object QuizContentPolicy {
    fun primaryEnglish(word: WordEntity): String =
        EnglishPresentation.from(word).primary.text
}
