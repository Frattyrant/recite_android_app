package com.miearn.app.domain

import kotlin.random.Random

object QuizEngine {
    fun isSpellingCorrect(answer: String, expected: String): Boolean =
        normalizeAnswer(answer) == normalizeAnswer(expected)

    fun blankExample(example: String, primaryEnglish: String): String {
        if (primaryEnglish.isBlank()) return example
        val token = Regex(Regex.escape(primaryEnglish), RegexOption.IGNORE_CASE)
        // A word can have multiple newline-delimited examples. Present one
        // sentence per question so a second sentence cannot reveal the answer.
        val sentence = example
            .lineSequence()
            .map(String::trim)
            .firstOrNull(token::containsMatchIn)
            ?: return example
        return token.replaceFirst(sentence, "______")
    }

    fun chineseOptions(
        answer: String,
        candidates: List<String>,
        seed: Int,
    ): List<String> = choiceOptions(answer, candidates, seed)

    fun choiceOptions(
        answer: String,
        candidates: List<String>,
        seed: Int,
    ): List<String> {
        val normalizedAnswer = answer.trim().lowercase()
        val distractors = candidates
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.lowercase() != normalizedAnswer }
            .distinctBy(String::lowercase)
            .shuffled(Random(seed))
            .take(3)
            .toList()
        return (distractors + answer).shuffled(Random(seed xor 0x5F3759DF))
    }

    private fun normalizeAnswer(value: String): String =
        value
            .replace('’', '\'')
            .replace('‘', '\'')
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
}
