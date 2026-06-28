package com.miearn.app.domain

object EnglishVariantParser {
    private val separators = Regex("[;；]")

    fun parse(english: String): List<String> =
        english
            .split(separators)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf(english.trim()) }
}
