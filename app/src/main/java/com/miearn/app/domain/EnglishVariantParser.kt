package com.miearn.app.domain

object EnglishVariantParser {
    private val separators = Regex("""(?:[;；/\\]|\s)+""")

    fun parse(english: String): List<String> =
        english
            .split(separators)
            .map(String::trim)
            .filter(String::isNotEmpty)
}