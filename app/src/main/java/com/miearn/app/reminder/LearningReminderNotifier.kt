package com.miearn.app.reminder

import android.Manifest
import android.annotation.SuppressLint
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
import com.miearn.app.MainActivity
import com.miearn.app.R

class LearningReminderNotifier(context: Context) : ReminderNotificationGateway {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun show(isTest: Boolean): Boolean {
        ensureChannel()
        if (!ReminderNotificationAccess.canDeliver(deliveryStatus())) return false
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            if (isTest) TEST_NOTIFICATION_ID else NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (isTest) "MIearn 提醒测试" else "MIearn 今日学习")
            .setContentText(
                if (isTest) {
                    "通知已正常送达，之后会按设定时间提醒你。"
                } else {
                    "新词与到期复习已经准备好，花几分钟继续积累。"
                },
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(appContext).notify(
            if (isTest) TEST_NOTIFICATION_ID else NOTIFICATION_ID,
            notification,
        )
        return true
    }

    override fun deliveryStatus(): ReminderDeliveryStatus {
        ensureChannel()
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return ReminderNotificationAccess.evaluate(
            runtimePermissionGranted = runtimePermissionGranted,
            appNotificationsEnabled = NotificationManagerCompat.from(appContext)
                .areNotificationsEnabled(),
            channelImportance = manager.getNotificationChannel(CHANNEL_ID)?.importance
                ?: NotificationManager.IMPORTANCE_NONE,
        )
    }

    internal fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "学习任务提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每天提醒完成 MIearn 新词与复习任务"
                setShowBadge(false)
            },
        )
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    companion object {
        const val CHANNEL_ID = "learning_reminder_v2"
        private const val LEGACY_CHANNEL_ID = "learning_reminder"
        private const val NOTIFICATION_ID = 10_001
        private const val TEST_NOTIFICATION_ID = 10_002
    }
}

internal object ReminderNotificationAccess {
    fun evaluate(
        runtimePermissionGranted: Boolean,
        appNotificationsEnabled: Boolean,
        channelImportance: Int,
    ): ReminderDeliveryStatus = when {
        !runtimePermissionGranted -> ReminderDeliveryStatus.PERMISSION_REQUIRED
        !appNotificationsEnabled -> ReminderDeliveryStatus.APP_NOTIFICATIONS_DISABLED
        channelImportance == NotificationManager.IMPORTANCE_NONE ->
            ReminderDeliveryStatus.CHANNEL_DISABLED
        channelImportance <= NotificationManager.IMPORTANCE_LOW ->
            ReminderDeliveryStatus.CHANNEL_SILENT
        else -> ReminderDeliveryStatus.AVAILABLE
    }

    fun canDeliver(status: ReminderDeliveryStatus): Boolean =
        status == ReminderDeliveryStatus.AVAILABLE ||
            status == ReminderDeliveryStatus.CHANNEL_SILENT
}
