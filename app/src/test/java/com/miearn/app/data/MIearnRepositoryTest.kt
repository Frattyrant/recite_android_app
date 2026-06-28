package com.miearn.app.data

import com.miearn.app.data.local.DailyActivityEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MIearnRepositoryTest {
    @Test
    fun streakContinuesFromYesterdayWhenTodayHasNoActivity() {
        val activities = listOf(
            DailyActivityEntity(epochDay = 100, newCount = 2),
            DailyActivityEntity(epochDay = 99, reviewCount = 3),
            DailyActivityEntity(epochDay = 98, newCount = 1),
            DailyActivityEntity(epochDay = 96, newCount = 8),
        )

        assertEquals(3, MIearnRepository.calculateStreak(activities, today = 101))
    }

    @Test
    fun streakIncludesTodayAndStopsAtFirstGap() {
        val activities = listOf(
            DailyActivityEntity(epochDay = 101, newCount = 1),
            DailyActivityEntity(epochDay = 100, reviewCount = 1),
            DailyActivityEntity(epochDay = 98, newCount = 1),
        )

        assertEquals(2, MIearnRepository.calculateStreak(activities, today = 101))
    }
}
