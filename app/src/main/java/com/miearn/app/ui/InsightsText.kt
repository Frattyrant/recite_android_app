package com.miearn.app.ui

import kotlin.math.roundToInt

internal fun firstTryAccuracyLabel(accuracy: Float, hasSamples: Boolean): String =
    if (!hasSamples) {
        "—"
    } else {
        "${(accuracy.coerceIn(0f, 1f) * 100).roundToInt()}%"
    }
