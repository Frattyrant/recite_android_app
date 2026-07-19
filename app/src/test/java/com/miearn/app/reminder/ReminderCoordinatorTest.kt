package com.miearn.app.reminder

import com.miearn.app.data.settings.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCoordinatorTest {
    @Test
    fun alarmPostsWhenTodayIsIncompleteAndAlwaysSchedulesTomorrow() = runTest {
        val scheduler = RecordingScheduleGateway()
        val work = RecordingWorkGateway()
        val notifications = RecordingNotificationGateway(throwWhenShown = true)
        val coordinator = ReminderCoordinator(
            loadSettings = {
                UserSettings(
                    reminderEnabled = true,
                    lastCompletedEpochDay = 99,
                )
            },
            scheduler = scheduler,
            work = work,
            notifications = notifications,
            todayEpochDay = { 100 },
        )

        coordinator.onReminderAlarm()

        assertEquals(1, notifications.showCount)
        assertEquals(1, scheduler.scheduleNextCount)
        assertTrue(work.enabled)
        assertEquals(123L, work.triggerAtMillis)
    }

    @Test
    fun completedDaySkipsNotificationButKeepsTheDailyChainAlive() = runTest {
        val scheduler = RecordingScheduleGateway()
        val notifications = RecordingNotificationGateway()
        val coordinator = ReminderCoordinator(
            loadSettings = {
                UserSettings(
                    reminderEnabled = true,
                    lastCompletedEpochDay = 100,
                )
            },
            scheduler = scheduler,
            work = RecordingWorkGateway(),
            notifications = notifications,
            todayEpochDay = { 100 },
        )

        coordinator.onReminderAlarm()

        assertEquals(0, notifications.showCount)
        assertEquals(1, scheduler.scheduleNextCount)
    }

    @Test
    fun reconcileUsesPersistedSettingsAndStartsOrStopsSelfHealing() = runTest {
        var settings = UserSettings(reminderEnabled = true)
        val scheduler = RecordingScheduleGateway()
        val work = RecordingWorkGateway()
        val coordinator = ReminderCoordinator(
            loadSettings = { settings },
            scheduler = scheduler,
            work = work,
            notifications = RecordingNotificationGateway(),
            todayEpochDay = { 100 },
        )

        coordinator.reconcile()
        assertTrue(scheduler.lastApplied?.reminderEnabled == true)
        assertTrue(work.enabled)
        assertEquals(123L, work.triggerAtMillis)

        settings = settings.copy(reminderEnabled = false)
        coordinator.reconcile()
        assertFalse(scheduler.lastApplied?.reminderEnabled == true)
        assertFalse(work.enabled)
        assertEquals(null, work.triggerAtMillis)
    }

    private class RecordingScheduleGateway : ReminderScheduleGateway {
        var lastApplied: UserSettings? = null
        var scheduleNextCount = 0

        override fun apply(settings: UserSettings) {
            lastApplied = settings
        }

        override fun scheduleNext(settings: UserSettings) {
            scheduleNextCount += 1
        }

        override fun nextTriggerAtMillis(settings: UserSettings): Long? = 123L
    }

    private class RecordingWorkGateway : ReminderWorkGateway {
        var enabled = false
        var triggerAtMillis: Long? = null

        override fun apply(enabled: Boolean, triggerAtMillis: Long?) {
            this.enabled = enabled
            this.triggerAtMillis = triggerAtMillis
        }
    }

    private class RecordingNotificationGateway(
        private val throwWhenShown: Boolean = false,
    ) : ReminderNotificationGateway {
        var showCount = 0

        override fun show(isTest: Boolean): Boolean {
            showCount += 1
            if (throwWhenShown) error("notification manager failed")
            return true
        }

        override fun deliveryStatus(): ReminderDeliveryStatus = ReminderDeliveryStatus.AVAILABLE
    }
}
