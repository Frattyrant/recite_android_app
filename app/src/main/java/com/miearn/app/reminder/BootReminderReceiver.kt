package com.miearn.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miearn.app.MIearnApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? MIearnApplication ?: return@launch
                val settings = app.container.settings.settings.first()
                app.container.reminderScheduler.apply(settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
