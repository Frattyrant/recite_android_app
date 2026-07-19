package com.miearn.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.miearn.app.data.settings.UserSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal interface ReminderAlarmGateway {
    fun schedule(triggerAtMillis: Long)
    fun cancel()
}

internal interface ReminderScheduleGateway {
    fun apply(settings: UserSettings)
    fun scheduleNext(settings: UserSettings)
    fun nextTriggerAtMillis(settings: UserSettings): Long?
}

class ReminderScheduler internal constructor(
    private val clock: Clock,
    private val zoneId: () -> ZoneId,
    private val alarms: ReminderAlarmGateway,
) : ReminderScheduleGateway {
    constructor(context: Context) : this(
        clock = Clock.systemUTC(),
        zoneId = ZoneId::systemDefault,
        alarms = AndroidReminderAlarmGateway(context.applicationContext),
    )

    override fun apply(settings: UserSettings) {
        if (!settings.reminderEnabled) {
            cancel()
            return
        }
        schedule(settings)
    }

    override fun scheduleNext(settings: UserSettings) {
        if (!settings.reminderEnabled) return
        schedule(settings)
    }

    private fun schedule(settings: UserSettings) {
        val triggerAtMillis = nextTriggerAtMillis(settings) ?: return
        alarms.schedule(triggerAtMillis)
    }

    override fun nextTriggerAtMillis(settings: UserSettings): Long? {
        if (!settings.reminderEnabled) return null
        return ReminderTime.nextInstant(
            now = Instant.now(clock),
            zoneId = zoneId(),
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
        ).toEpochMilli()
    }

    fun cancel() {
        alarms.cancel()
    }

    companion object {
        const val ACTION_DAILY_REMINDER = "com.miearn.app.action.DAILY_REMINDER"
    }
}

private class AndroidReminderAlarmGateway(context: Context) : ReminderAlarmGateway {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(triggerAtMillis: Long) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            checkNotNull(reminderPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)),
        )
    }

    override fun cancel() {
        reminderPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
    }

    private fun reminderPendingIntent(extraFlag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            Intent(appContext, LearningReminderReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_DAILY_REMINDER),
            PendingIntent.FLAG_IMMUTABLE or extraFlag,
        )

    private companion object {
        private const val REQUEST_CODE = 10_001
    }
}
