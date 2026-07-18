package com.miearn.app.ui

import com.miearn.app.reminder.ReminderDeliveryStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderUiTextTest {
    @Test
    fun testNotificationResultIsExplicit() {
        assertEquals("测试提醒已发送，请查看通知栏。", ReminderUiText.testResult(true))
        assertEquals("测试提醒发送失败，请检查通知设置。", ReminderUiText.testResult(false))
    }

    @Test
    fun nextReminderUsesTheUsersLocalTime() {
        val label = ReminderUiText.nextReminder(
            triggerAtMillis = Instant.parse("2026-07-15T02:05:00Z").toEpochMilli(),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertTrue(label.contains("7月15日 10:05"))
        assertTrue(label.contains("可能稍有延迟"))
    }

    @Test
    fun blockedChannelExplainsTheRecoveryAction() {
        assertEquals(
            "系统已关闭学习提醒通道，请前往通知设置开启。",
            ReminderUiText.status(ReminderDeliveryStatus.CHANNEL_DISABLED),
        )
    }
}
