package com.miearn.app.ui

internal fun estimateStudyMinutes(wordCount: Int): Int =
    if (wordCount <= 0) 0 else maxOf(1, (wordCount + 2) / 3)
