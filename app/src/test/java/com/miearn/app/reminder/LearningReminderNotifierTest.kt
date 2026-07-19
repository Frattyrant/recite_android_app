package com.miearn.app.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LearningReminderNotifierTest {
    @Test
    fun createsANewAudibleDefaultImportanceChannelAndRemovesLegacyChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "learning_reminder",
                "legacy",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        LearningReminderNotifier(context).ensureChannel()

        val channel = manager.getNotificationChannel(LearningReminderNotifier.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertTrue(channel.name.toString().contains("学习"))
        assertNull(manager.getNotificationChannel("learning_reminder"))
    }
}
