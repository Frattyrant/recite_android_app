package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTaskSummaryTest {
    @Test
    fun estimatesACompactDailyStudyDuration() {
        assertEquals(0, estimateStudyMinutes(0))
        assertEquals(1, estimateStudyMinutes(1))
        assertEquals(7, estimateStudyMinutes(20))
        assertEquals(9, estimateStudyMinutes(26))
    }
}
