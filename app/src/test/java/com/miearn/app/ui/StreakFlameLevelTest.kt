package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakFlameLevelTest {
    @Test
    fun flameSizeFollowsOneThreeAndSevenDayThresholds() {
        assertEquals(StreakFlameLevel.NONE, StreakFlameLevel.fromDays(0))
        assertEquals(StreakFlameLevel.SMALL, StreakFlameLevel.fromDays(1))
        assertEquals(StreakFlameLevel.SMALL, StreakFlameLevel.fromDays(2))
        assertEquals(StreakFlameLevel.MEDIUM, StreakFlameLevel.fromDays(3))
        assertEquals(StreakFlameLevel.MEDIUM, StreakFlameLevel.fromDays(6))
        assertEquals(StreakFlameLevel.LARGE, StreakFlameLevel.fromDays(7))
        assertEquals(StreakFlameLevel.LARGE, StreakFlameLevel.fromDays(30))
    }
}
