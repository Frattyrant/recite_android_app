package com.miearn.app.reminder

import android.content.Context
import com.miearn.app.data.settings.SettingsRepository
import com.miearn.app.data.settings.UserSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.first

enum class ReminderDeliveryStatus {
    AVAILABLE,
    PERMISSION_REQUIRED,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    CHANNEL_SILENT,
}

data class ReminderUiState(
    val deliveryStatus: ReminderDeliveryStatus = ReminderDeliveryStatus.PERMISSION_REQUIRED,
    val nextTriggerAtMillis: Long? = null,
)

internal interface ReminderNotificationGateway {
    fun show(isTest: Boolean = false): Boolean
    fun deliveryStatus(): ReminderDeliveryStatus
}

internal interface ReminderWorkGateway {
    fun apply(enabled: Boolean, triggerAtMillis: Long?)
}

class ReminderCoordinator internal constructor(
    private val loadSettings: suspend () -> UserSettings,
    private val scheduler: ReminderScheduleGateway,
    private val work: ReminderWorkGateway,
    private val notifications: ReminderNotificationGateway,
    private val todayEpochDay: () -> Long,
) {
    suspend fun reconcile(): ReminderUiState {
        val settings = loadSettings()
        return apply(settings)
    }

    fun apply(settings: UserSettings): ReminderUiState {
        scheduler.apply(settings)
        work.apply(
            enabled = settings.reminderEnabled,
            triggerAtMillis = if (settings.reminderEnabled) {
                scheduler.nextTriggerAtMillis(settings)
            } else {
                null
            },
        )
        return snapshot(settings)
    }

    suspend fun onReminderAlarm() {
        val settings = loadSettings()
        if (!settings.reminderEnabled) {
            apply(settings)
            return
        }
        try {
            if (settings.lastCompletedEpochDay != todayEpochDay()) {
                runCatching { notifications.show() }
            }
        } finally {
            scheduler.scheduleNext(settings)
            work.apply(
                enabled = true,
                triggerAtMillis = scheduler.nextTriggerAtMillis(settings),
            )
        }
    }

    fun snapshot(settings: UserSettings): ReminderUiState = ReminderUiState(
        deliveryStatus = notifications.deliveryStatus(),
        nextTriggerAtMillis = scheduler.nextTriggerAtMillis(settings),
    )

    fun showTest(): Boolean = runCatching {
        notifications.show(isTest = true)
    }.getOrDefault(false)

    companion object {
        fun create(context: Context): ReminderCoordinator {
            val appContext = context.applicationContext
            val settings = SettingsRepository(appContext)
            return ReminderCoordinator(
                loadSettings = { settings.settings.first() },
                scheduler = ReminderScheduler(appContext),
                work = ReminderWorkCoordinator(appContext),
                notifications = LearningReminderNotifier(appContext),
                todayEpochDay = { LocalDate.now().toEpochDay() },
            )
        }
    }
}
