package com.miearn.app.ui

import com.miearn.app.reminder.ReminderDeliveryStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object ReminderUiText {
    fun testResult(sent: Boolean): String = if (sent) {
        "测试提醒已发送，请查看通知栏。"
    } else {
        "测试提醒发送失败，请检查通知设置。"
    }

    fun status(status: ReminderDeliveryStatus): String = when (status) {
        ReminderDeliveryStatus.AVAILABLE -> "通知通道正常，可以发送测试提醒。"
        ReminderDeliveryStatus.PERMISSION_REQUIRED -> "尚未授予通知权限，提醒不会送达。"
        ReminderDeliveryStatus.APP_NOTIFICATIONS_DISABLED ->
            "系统已关闭 MIearn 通知，请前往通知设置开启。"
        ReminderDeliveryStatus.CHANNEL_DISABLED ->
            "系统已关闭学习提醒通道，请前往通知设置开启。"
        ReminderDeliveryStatus.CHANNEL_SILENT ->
            "学习提醒通道当前为静默，可在通知设置中开启声音。"
    }

    fun nextReminder(
        triggerAtMillis: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = if (triggerAtMillis == null) {
        "开启后会在这里显示下一次提醒。"
    } else {
        val time = Instant.ofEpochMilli(triggerAtMillis).atZone(zoneId)
        "下次预计：${time.format(FORMATTER)}（省电模式下可能稍有延迟）"
    }

    private val FORMATTER = DateTimeFormatter.ofPattern("M月d日 HH:mm")
}
