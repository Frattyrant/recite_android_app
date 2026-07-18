package com.miearn.app.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class ReminderWorkCoordinator(context: Context) : ReminderWorkGateway {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun apply(enabled: Boolean, triggerAtMillis: Long?) {
        if (enabled && triggerAtMillis != null) {
            workManager.enqueueUniqueWork(
                WATCHDOG_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<LearningReminderWorker>()
                    .setInitialDelay(
                        ReminderFallbackPlan.delayMillis(
                            nowMillis = System.currentTimeMillis(),
                            triggerAtMillis = triggerAtMillis,
                        ),
                        TimeUnit.MILLISECONDS,
                    )
                    .addTag(WATCHDOG_WORK_NAME)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(WATCHDOG_WORK_NAME)
        }
    }

    private companion object {
        const val WATCHDOG_WORK_NAME = "learning-reminder-watchdog-v2"
    }
}

internal object ReminderFallbackPlan {
    private const val GRACE_MILLIS = 15 * 60 * 1_000L

    fun delayMillis(nowMillis: Long, triggerAtMillis: Long): Long =
        max(0L, triggerAtMillis - nowMillis + GRACE_MILLIS)
}
