package com.miearn.app.domain

object EnglishVariantParser {
    private val termSeparators = Regex("""(?:[;；/\\]|\s)+""")
    private val phraseSemicolons = Regex("""[;；]+""")
    private val sentenceBoundary = Regex("""(?<=[.!?])(?:\s+(?=["']?[A-Z])|(?=["']?[A-Z]))""")
    private val englishWord = Regex("""[A-Za-z]+(?:'[A-Za-z]+)?""")
    private val englishOrNumber = Regex("""[A-Za-z0-9]""")
    private val unitLeft = Regex("""\d+(?:\.\d+)?[A-Za-z]*$""")
    private val unitRight = Regex("""^[smh](?:\b|$)""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")
    private val sentencePunctuation = Regex("""[!?]|[.]\s*$|[.!?]\s+["']?[A-Z]""")
    private val sentenceStarters = setOf(
        "i", "we", "you", "he", "she", "they", "it",
        "can", "could", "would", "should", "will", "do", "does", "did",
        "what", "when", "where", "which", "who", "why", "how",
    )

    fun parse(english: String, kind: String = "TERM"): List<String> =
        if (kind.equals("PHRASE", ignoreCase = true)) {
            parsePhrase(english)
        } else {
            parseTerm(english)
        }

    fun toSpeechText(text: String): String =
        text
            .replace('/', ' ')
            .replace('\\', ' ')
            .replace(whitespace, " ")
            .trim()

    fun inferKind(english: String): String {
        val words = englishWord.findAll(english).map { it.value }.toList()
        val startsLikeSentence = words.firstOrNull()?.lowercase() in sentenceStarters
        return if (
            sentencePunctuation.containsMatchIn(english) ||
            (startsLikeSentence && words.size >= 5)
        ) {
            "PHRASE"
        } else {
            "TERM"
        }
    }

    private fun parseTerm(english: String): List<String> =
        english
            .split(termSeparators)
            .map(String::trim)
            .filter(englishOrNumber::containsMatchIn)

    private fun parsePhrase(english: String): List<String> =
        english
            .split(phraseSemicolons)
            .asSequence()
            .flatMap(::splitPhraseSlashes)
            .flatMap { it.split(sentenceBoundary).asSequence() }
            .map(String::trim)
            .filter(::containsEnglishWord)
            .toList()

    private fun splitPhraseSlashes(text: String): Sequence<String> = sequence {
        val current = StringBuilder()
        text.forEachIndexed { index, char ->
            if (char != '/' && char != '\\') {
                current.append(char)
                return@forEachIndexed
            }
            val previous = text.getOrNull(index - 1)
            val next = text.getOrNull(index + 1)
            val leftClause = current.substringAfterLastSentenceBoundary()
            val rightClause = text
                .substring(index + 1)
                .substringBeforeAny('/', '\\', '.', '!', '?')
            val unitSlash = unitLeft.containsMatchIn(text.substring(0, index)) &&
                unitRight.containsMatchIn(text.substring(index + 1))
            val isBoundary = !unitSlash && (
                previous?.isWhitespace() == true ||
                    next?.isWhitespace() == true ||
                    previous in setOf('.', '!', '?') ||
                    (
                        englishWord.findAll(leftClause).count() >= 3 &&
                            englishWord.findAll(rightClause).count() >= 3
                        )
                )
            if (isBoundary) {
                current.toString().trim().takeIf(String::isNotEmpty)?.let { yield(it) }
                current.setLength(0)
            } else {
                current.append(char)
            }
        }
        current.toString().trim().takeIf(String::isNotEmpty)?.let { yield(it) }
    }

    private fun StringBuilder.substringAfterLastSentenceBoundary(): String {
        val value = toString()
        val boundary = maxOf(
            value.lastIndexOf('.'),
            value.lastIndexOf('!'),
            value.lastIndexOf('?'),
        )
        return value.substring(boundary + 1)
    }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val boundary = delimiters
            .map(::indexOf)
            .filter { it >= 0 }
            .minOrNull()
        return if (boundary == null) this else substring(0, boundary)
    }

    private fun containsEnglishWord(text: String): Boolean = englishWord.containsMatchIn(text)
}
