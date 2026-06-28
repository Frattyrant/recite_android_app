package com.miearn.app

import android.content.Context
import com.miearn.app.audio.AnswerFeedbackPlayer
import com.miearn.app.audio.AudioPronouncer
import com.miearn.app.data.MIearnRepository
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.seed.ContentSeeder
import com.miearn.app.data.settings.SettingsRepository
import com.miearn.app.reminder.ReminderScheduler

class AppContainer(context: Context) {
    val database = AppDatabase.create(context)
    val seeder = ContentSeeder(context, database)
    val repository = MIearnRepository(database)
    val settings = SettingsRepository(context)
    val reminderScheduler = ReminderScheduler(context)
    val audio = AudioPronouncer(context)
    val answerFeedback = AnswerFeedbackPlayer(context)
}
