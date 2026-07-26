package com.miearn.app.domain

import com.miearn.app.data.local.WordEntity

fun EnglishPresentation.Companion.from(word: WordEntity): EnglishPresentation =
    from(
        english = word.english,
        primaryEnglish = word.primaryEnglish,
        kind = word.kind,
    )
