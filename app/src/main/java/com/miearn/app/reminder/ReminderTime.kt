package com.miearn.app.reminder

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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

    fun nextInstant(
        now: Instant,
        zoneId: ZoneId,
        hour: Int,
        minute: Int,
    ): Instant {
        require(hour in 0..23)
        require(minute in 0..59)
        val today = now.atZone(zoneId).toLocalDate()
        val todayTrigger = today.atTime(hour, minute).atZone(zoneId).toInstant()
        return if (now.isBefore(todayTrigger)) {
            todayTrigger
        } else {
            today.plusDays(1).atTime(hour, minute).atZone(zoneId).toInstant()
        }
    }
}
