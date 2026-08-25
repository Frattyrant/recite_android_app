package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsTextTest {
    @Test
    fun accuracyLabelDistinguishesNoSamplesFromZeroPercent() {
        assertEquals("—", firstTryAccuracyLabel(0f, hasSamples = false))
        assertEquals("0%", firstTryAccuracyLabel(0f, hasSamples = true))
        assertEquals("75%", firstTryAccuracyLabel(0.75f, hasSamples = true))
    }
}
