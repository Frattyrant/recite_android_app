package com.miearn.app.reminder

import com.miearn.app.data.settings.UserSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-07-14T01:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun enabledReminderSchedulesTheNextLocalTime() {
        val alarms = RecordingAlarmGateway()
        val scheduler = ReminderScheduler(clock, { shanghai }, alarms)

        scheduler.apply(
            UserSettings(
                reminderEnabled = true,
                reminderHour = 10,
                reminderMinute = 0,
            ),
        )

        assertEquals(Instant.parse("2026-07-14T02:00:00Z").toEpochMilli(), alarms.triggerAtMillis)
        assertFalse(alarms.cancelled)
    }

    @Test
    fun atOrAfterTheChosenTimeSchedulesTomorrow() {
        val atTime = ReminderScheduler(
            Clock.fixed(Instant.parse("2026-07-14T02:00:00Z"), ZoneId.of("UTC")),
            { shanghai },
            RecordingAlarmGateway(),
        )
        val afterTime = ReminderScheduler(
            Clock.fixed(Instant.parse("2026-07-14T02:01:00Z"), ZoneId.of("UTC")),
            { shanghai },
            RecordingAlarmGateway(),
        )
        val settings = UserSettings(
            reminderEnabled = true,
            reminderHour = 10,
            reminderMinute = 0,
        )

        assertEquals(
            Instant.parse("2026-07-15T02:00:00Z").toEpochMilli(),
            atTime.nextTriggerAtMillis(settings),
        )
        assertEquals(
            Instant.parse("2026-07-15T02:00:00Z").toEpochMilli(),
            afterTime.nextTriggerAtMillis(settings),
        )
    }

    @Test
    fun disabledReminderCancelsAnyExistingAlarm() {
        val alarms = RecordingAlarmGateway()
        val scheduler = ReminderScheduler(clock, { shanghai }, alarms)

        scheduler.apply(UserSettings(reminderEnabled = false))

        assertTrue(alarms.cancelled)
        assertNull(alarms.triggerAtMillis)
    }

    @Test
    fun schedulingAgainReplacesTheExistingAlarmIdempotently() {
        val alarms = RecordingAlarmGateway()
        val scheduler = ReminderScheduler(clock, { shanghai }, alarms)
        val settings = UserSettings(
            reminderEnabled = true,
            reminderHour = 10,
            reminderMinute = 0,
        )

        scheduler.apply(settings)
        scheduler.apply(settings)

        assertEquals(2, alarms.scheduleCount)
        assertEquals(Instant.parse("2026-07-14T02:00:00Z").toEpochMilli(), alarms.triggerAtMillis)
    }

    private class RecordingAlarmGateway : ReminderAlarmGateway {
        var triggerAtMillis: Long? = null
        var cancelled = false
        var scheduleCount = 0

        override fun schedule(triggerAtMillis: Long) {
            this.triggerAtMillis = triggerAtMillis
            cancelled = false
            scheduleCount += 1
        }

        override fun cancel() {
            triggerAtMillis = null
            cancelled = true
        }
    }
}
