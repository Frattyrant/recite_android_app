package com.miearn.app.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.miearn.app.data.settings.UserSettings
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun apply(settings: UserSettings) {
        if (!settings.reminderEnabled) {
            cancel()
            return
        }
        enqueue(settings, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleNext(settings: UserSettings) {
        if (!settings.reminderEnabled) return
        enqueue(settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(
        settings: UserSettings,
        policy: ExistingWorkPolicy,
    ) {
        val now = LocalDateTime.now()
        val next = ReminderTime.next(
            now = now,
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
        )
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<LearningReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "miearn_daily_reminder"
    }
}
