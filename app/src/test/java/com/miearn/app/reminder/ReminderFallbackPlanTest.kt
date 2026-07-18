package com.miearn.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderFallbackPlanTest {
    @Test
    fun fallbackRunsFifteenMinutesAfterPrimaryAlarm() {
        assertEquals(
            75 * 60 * 1_000L,
            ReminderFallbackPlan.delayMillis(
                nowMillis = 1_000L,
                triggerAtMillis = 1_000L + 60 * 60 * 1_000L,
            ),
        )
    }

    @Test
    fun overdueFallbackRunsImmediately() {
        assertEquals(
            0L,
            ReminderFallbackPlan.delayMillis(
                nowMillis = 1_000L + 16 * 60 * 1_000L,
                triggerAtMillis = 1_000L,
            ),
        )
    }
}
