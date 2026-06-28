package com.miearn.app.reminder

import java.time.LocalDateTime

object ReminderTime {
    fun next(
        now: LocalDateTime,
        hour: Int,
        minute: Int,
    ): LocalDateTime {
        require(hour in 0..23)
        require(minute in 0..59)
        val today = now.toLocalDate().atTime(hour, minute)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }
}
