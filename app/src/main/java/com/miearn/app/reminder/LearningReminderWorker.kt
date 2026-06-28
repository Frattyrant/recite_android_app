package com.miearn.app.reminder

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miearn.app.MIearnApplication
import com.miearn.app.MainActivity
import com.miearn.app.R
import com.miearn.app.data.MIearnRepository
import kotlinx.coroutines.flow.first

class LearningReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as? MIearnApplication ?: return Result.failure()
        val settings = app.container.settings.settings.first()
        val today = MIearnRepository.todayEpochDay()
        if (
            !settings.reminderEnabled ||
            settings.lastCompletedEpochDay == today
        ) {
            if (settings.reminderEnabled) {
                ReminderScheduler(applicationContext).scheduleNext(settings)
            }
            return Result.success()
        }
        if (notificationsAllowed()) {
            runCatching {
                createChannel()
                val intent = Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    10,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("MIearn 今日学习")
                    .setContentText("新词与到期复习已经准备好，花几分钟继续积累。")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                if (
                    Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(applicationContext)
                        .notify(NOTIFICATION_ID, notification)
                }
            }
        }
        ReminderScheduler(applicationContext).scheduleNext(settings)
        return Result.success()
    }

    private fun notificationsAllowed(): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "学习任务提醒",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "每天提醒完成 MIearn 新词与复习任务"
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "learning_reminder"
        const val NOTIFICATION_ID = 10_001
    }
}
