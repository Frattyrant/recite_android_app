package com.miearn.app.domain

object EnglishVariantParser {
    private val termSemicolons = Regex("""[;\uFF1B]+""")
    private val phraseSemicolons = Regex("""[;；]+""")
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
    private val sentenceAbbreviations = setOf(
        "approx", "capt", "dept", "dr", "e", "etc", "fig", "g", "i", "inc",
        "jr", "mr", "mrs", "ms", "no", "prof", "rev", "sr", "st", "vs",
    )
    private val sentenceTerminators = setOf('.', '!', '?', '。', '！', '？')

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
            .split(termSemicolons)
            .asSequence()
            .flatMap(::splitTermSlashes)
            .map(String::trim)
            .filter(englishOrNumber::containsMatchIn)
            .toList()

    private fun splitTermSlashes(text: String): Sequence<String> = sequence {
        val current = StringBuilder()
        text.forEachIndexed { index, char ->
            if (char != '/' && char != '\\') {
                current.append(char)
                return@forEachIndexed
            }
            val unitSlash = unitLeft.containsMatchIn(current.toString()) &&
                unitRight.containsMatchIn(text.substring(index + 1))
            val initialismSlash = text.getOrNull(index - 1)?.isUpperCase() == true &&
                text.getOrNull(index + 1)?.isUpperCase() == true &&
                text.getOrNull(index - 2)?.isLetter() != true &&
                text.getOrNull(index + 2)?.isLetter() != true
            if (unitSlash || initialismSlash) {
                current.append(char)
            } else {
                current.toString().trim().takeIf(String::isNotEmpty)?.let { yield(it) }
                current.setLength(0)
            }
        }
        current.toString().trim().takeIf(String::isNotEmpty)?.let { yield(it) }
    }
    private fun parsePhrase(english: String): List<String> =
        english
            .split(phraseSemicolons)
            .asSequence()
            .flatMap(::splitPhraseSlashes)
            .flatMap(::splitSentences)
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

    /**
     * Splits complete phrases without requiring an uppercase next character.
     * Chinese sentence punctuation is supported while decimals, unit values,
     * ellipses and common abbreviations remain intact.
     */
    private fun splitSentences(text: String): Sequence<String> = sequence {
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] in sentenceTerminators && isSentenceBoundary(text, index)) {
                text.substring(start, index + 1)
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?.let { yield(it) }
                index += 1
                while (index < text.length && text[index].isWhitespace()) index += 1
                start = index
            } else {
                index += 1
            }
        }
        text.substring(start).trim().takeIf(String::isNotEmpty)?.let { yield(it) }
    }

    private fun isSentenceBoundary(text: String, index: Int): Boolean {
        val punctuation = text[index]
        if (punctuation != '.' && punctuation != '。') {
            val nextNonWhitespace = text.indexOfFirstNonWhitespace(index + 1)
            // Keep punctuation runs such as "? . ?" attached to the
            // sentence instead of creating punctuation-only fragments.
            return nextNonWhitespace < 0 || text[nextNonWhitespace] !in sentenceTerminators
        }
        if (punctuation == '.' &&
            (text.getOrNull(index - 1) == '.' || text.getOrNull(index + 1) == '.')
        ) return false

        val previous = text.getOrNull(index - 1)
        val next = text.getOrNull(index + 1)
        if (previous?.isDigit() == true && next?.isDigit() == true) return false

        val token = text.substring(0, index)
            .takeLastWhile { it.isLetter() }
            .lowercase()
        if (punctuation == '.' && token in sentenceAbbreviations) return false

        val nextNonWhitespace = text.indexOfFirstNonWhitespace(index + 1)
        if (nextNonWhitespace < 0) return true
        val nextChar = text[nextNonWhitespace]
        val nextAfterQuote = if (nextChar == '"' || nextChar == '\'') {
            text.indexOfFirstNonWhitespace(nextNonWhitespace + 1)
                .takeIf { it >= 0 }
                ?.let(text::get)
        } else {
            nextChar
        }
        if (nextAfterQuote == null ||
            !(nextAfterQuote.isLetter() || nextAfterQuote in "（(［[")
        ) return false

        // Keep compact initials such as U.S. together. A whitespace boundary
        // remains valid for a normal sentence beginning with one letter.
        return punctuation != '.' ||
            text.getOrNull(index + 1)?.isWhitespace() == true ||
            token.length > 2
    }

    private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
        for (index in startIndex until length) {
            if (!this[index].isWhitespace()) return index
        }
        return -1
    }

    private fun containsEnglishWord(text: String): Boolean = englishWord.containsMatchIn(text)

}
