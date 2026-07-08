package com.miearn.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.miearn.app.data.settings.UserSettings
import java.time.Duration
import java.time.LocalDateTime

class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun apply(settings: UserSettings) {
        if (!settings.reminderEnabled) {
            cancel()
            return
        }
        schedule(settings)
    }

    fun scheduleNext(settings: UserSettings) {
        if (!settings.reminderEnabled) return
        schedule(settings)
    }

    private fun schedule(settings: UserSettings) {
        val now = LocalDateTime.now()
        val next = ReminderTime.next(
            now = now,
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
        )
        val triggerAtMillis = System.currentTimeMillis() +
            Duration.between(now, next).toMillis().coerceAtLeast(0)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            reminderPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    fun cancel() {
        alarmManager.cancel(reminderPendingIntent(PendingIntent.FLAG_NO_CREATE))
    }

    private fun reminderPendingIntent(extraFlag: Int): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            Intent(appContext, LearningReminderReceiver::class.java)
                .setAction(ACTION_DAILY_REMINDER),
            PendingIntent.FLAG_IMMUTABLE or extraFlag,
        )

    companion object {
        const val ACTION_DAILY_REMINDER = "com.miearn.app.action.DAILY_REMINDER"
        private const val REQUEST_CODE = 10_001
    }
}
