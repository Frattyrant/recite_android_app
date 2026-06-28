package com.miearn.app.reminder

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeTest {
    @Test
    fun beforeTenSchedulesToday() {
        val now = LocalDateTime.of(2026, 6, 27, 9, 30)

        assertEquals(
            LocalDateTime.of(2026, 6, 27, 10, 0),
            ReminderTime.next(now, 10, 0),
        )
    }

    @Test
    fun atOrAfterTenSchedulesTomorrow() {
        val now = LocalDateTime.of(2026, 6, 27, 10, 0)

        assertEquals(
            LocalDateTime.of(2026, 6, 28, 10, 0),
            ReminderTime.next(now, 10, 0),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimeIsRejected() {
        ReminderTime.next(LocalDateTime.now(), 24, 0)
    }
}
