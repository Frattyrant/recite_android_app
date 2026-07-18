package com.miearn.app.reminder

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationAccessTest {
    @Test
    fun missingRuntimePermissionIsReportedFirst() {
        assertEquals(
            ReminderDeliveryStatus.PERMISSION_REQUIRED,
            ReminderNotificationAccess.evaluate(
                runtimePermissionGranted = false,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    @Test
    fun blockedAppAndChannelAreDistinguished() {
        assertEquals(
            ReminderDeliveryStatus.APP_NOTIFICATIONS_DISABLED,
            ReminderNotificationAccess.evaluate(
                runtimePermissionGranted = true,
                appNotificationsEnabled = false,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        assertEquals(
            ReminderDeliveryStatus.CHANNEL_DISABLED,
            ReminderNotificationAccess.evaluate(
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_NONE,
            ),
        )
    }

    @Test
    fun lowImportanceIsReportedAsSilentInsteadOfHealthy() {
        assertEquals(
            ReminderDeliveryStatus.CHANNEL_SILENT,
            ReminderNotificationAccess.evaluate(
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_LOW,
            ),
        )
        assertEquals(
            ReminderDeliveryStatus.AVAILABLE,
            ReminderNotificationAccess.evaluate(
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    @Test
    fun silentChannelsStillReceiveNotificationsButBlockedStatesDoNot() {
        assertTrue(ReminderNotificationAccess.canDeliver(ReminderDeliveryStatus.AVAILABLE))
        assertTrue(ReminderNotificationAccess.canDeliver(ReminderDeliveryStatus.CHANNEL_SILENT))
        assertFalse(
            ReminderNotificationAccess.canDeliver(ReminderDeliveryStatus.CHANNEL_DISABLED),
        )
        assertFalse(
            ReminderNotificationAccess.canDeliver(ReminderDeliveryStatus.PERMISSION_REQUIRED),
        )
    }
}
