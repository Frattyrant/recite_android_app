package com.miearn.app.domain

data class EnglishPresentation(
    val primary: Variant,
    val alternatives: List<Variant>,
    val alternativeKind: AlternativeKind,
) {
    data class Variant(
        val index: Int,
        val text: String,
    )

    enum class AlternativeKind {
        SYNONYMS,
        RELATED_PHRASES,
    }

    companion object {
        fun from(
            english: String,
            primaryEnglish: String,
            kind: String,
        ): EnglishPresentation {
            val parsed = EnglishVariantParser.parse(english, kind)
                .ifEmpty {
                    listOf(primaryEnglish.trim().ifBlank(english::trim))
                }
            val primaryIndex = parsed.indexOfFirst {
                it.equals(primaryEnglish.trim(), ignoreCase = true)
            }.takeIf { it >= 0 } ?: 0
            val variants = parsed.mapIndexed(::Variant)

            return EnglishPresentation(
                primary = variants[primaryIndex],
                alternatives = variants.filterNot { it.index == primaryIndex },
                alternativeKind = if (kind.equals("PHRASE", ignoreCase = true)) {
                    AlternativeKind.RELATED_PHRASES
                } else {
                    AlternativeKind.SYNONYMS
                },
            )
        }
    }
}
