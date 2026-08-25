package com.miearn.app.ui

/** Stable identity for answer text that is valid only for one quiz question. */
internal fun quizInputKey(mode: QuizMode, wordId: String, index: Int): String =
    "${mode.name}:$wordId:$index"

internal fun quizModeUsesTextInput(mode: QuizMode): Boolean =
    mode == QuizMode.SPELLING || mode == QuizMode.FILL_BLANK
