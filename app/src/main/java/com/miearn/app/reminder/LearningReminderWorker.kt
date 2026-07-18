package com.miearn.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class LearningReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        ReminderCoordinator.create(applicationContext).onReminderAlarm()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
