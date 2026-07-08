package com.miearn.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miearn.app.MIearnApplication
import com.miearn.app.data.MIearnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LearningReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_DAILY_REMINDER) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? MIearnApplication ?: return@launch
                val settings = app.container.settings.settings.first()
                if (settings.reminderEnabled) {
                    if (settings.lastCompletedEpochDay != MIearnRepository.todayEpochDay()) {
                        LearningReminderNotifier.show(context.applicationContext)
                    }
                    app.container.reminderScheduler.scheduleNext(settings)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
