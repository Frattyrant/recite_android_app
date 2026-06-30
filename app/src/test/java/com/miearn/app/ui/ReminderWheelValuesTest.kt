package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderWheelValuesTest {
    @Test
    fun hourWheelUsesFullTwentyFourHourRange() {
        assertEquals((0..23).toList(), ReminderWheelValues.hours)
    }

    @Test
    fun minuteWheelUsesOnlyValidClockMinutes() {
        assertEquals((0..59).toList(), ReminderWheelValues.minutes)
    }

    @Test
    fun wheelLabelsAlwaysUseTwoDigits() {
        assertEquals("00", ReminderWheelValues.label(0))
        assertEquals("09", ReminderWheelValues.label(9))
        assertEquals("59", ReminderWheelValues.label(59))
    }
}
