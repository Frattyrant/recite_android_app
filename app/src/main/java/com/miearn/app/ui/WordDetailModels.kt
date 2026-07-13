package com.miearn.app.ui

import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.EnglishVariantParser

data class WordDetailRequest(
    val word: WordEntity,
    val variantIndex: Int?,
    val displayEnglish: String,
)

data class PhoneticDisplay(
    val text: String,
    val isWholeEntry: Boolean,
)

fun wordDetailRequest(
    word: WordEntity,
    variantIndex: Int?,
): WordDetailRequest {
    val variants = EnglishVariantParser.parse(word.english, word.kind)
    require(variants.isNotEmpty()) { "word has no displayable English expression" }
    if (variantIndex != null) {
        require(variantIndex in variants.indices) { "variant index is out of range" }
    }
    return WordDetailRequest(
        word = word,
        variantIndex = variantIndex,
        displayEnglish = variantIndex?.let(variants::get) ?: word.english,
    )
}

fun resolveVariantPhonetic(
    word: WordEntity,
    variantIndex: Int?,
): PhoneticDisplay? {
    val phonetic = word.phonetic.trim()
    if (phonetic.isEmpty()) return null

    val variants = EnglishVariantParser.parse(word.english, word.kind)
    if (variantIndex != null) {
        require(variantIndex in variants.indices) { "variant index is out of range" }
    }
    if (variantIndex == null || variants.size == 1) {
        return PhoneticDisplay(phonetic, isWholeEntry = false)
    }

    val phoneticVariants = phonetic
        .split(Regex("[;；]+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
    return if (phoneticVariants.size == variants.size) {
        PhoneticDisplay(
            text = phoneticVariants[variantIndex],
            isWholeEntry = false,
        )
    } else {
        PhoneticDisplay(
            text = phonetic,
            isWholeEntry = true,
        )
    }
}
