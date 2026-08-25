package com.miearn.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayStateTest {
    @Test
    fun refreshIsNeededOnlyWhenEpochDayChanges() {
        assertFalse(shouldRefreshStudyDay(previous = 20_000, current = 20_000))
        assertTrue(shouldRefreshStudyDay(previous = 20_000, current = 20_001))
    }
}
