package com.miearn.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderSystemEvent.shouldReconcile(intent.action)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ReminderCoordinator.create(context.applicationContext).reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal object ReminderSystemEvent {
    fun shouldReconcile(action: String?): Boolean = action in setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )
}
