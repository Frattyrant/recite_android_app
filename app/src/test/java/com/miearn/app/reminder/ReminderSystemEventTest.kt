package com.miearn.app.reminder

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSystemEventTest {
    @Test
    fun bootUpdateClockAndTimezoneChangesTriggerReconciliation() {
        assertTrue(ReminderSystemEvent.shouldReconcile(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(ReminderSystemEvent.shouldReconcile(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(ReminderSystemEvent.shouldReconcile(Intent.ACTION_TIME_CHANGED))
        assertTrue(ReminderSystemEvent.shouldReconcile(Intent.ACTION_TIMEZONE_CHANGED))
        assertFalse(ReminderSystemEvent.shouldReconcile(Intent.ACTION_SCREEN_ON))
        assertFalse(ReminderSystemEvent.shouldReconcile(null))
    }
}
