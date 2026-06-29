package com.miearn.app

import android.content.Context
import com.miearn.app.audio.AnswerFeedbackPlayer
import com.miearn.app.audio.AudioPronouncer
import com.miearn.app.data.MIearnRepository
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.seed.ContentSeeder
import com.miearn.app.data.settings.SettingsRepository
import com.miearn.app.reminder.ReminderScheduler
import com.miearn.app.importing.CompactDictionary
import com.miearn.app.importing.ImportRepository
import com.miearn.app.importing.ImportWorkCoordinator
import com.miearn.app.importing.VocabularyFileReader

class AppContainer(context: Context) {
    val database = AppDatabase.create(context)
    val seeder = ContentSeeder(context, database)
    val repository = MIearnRepository(database)
    val settings = SettingsRepository(context)
    val reminderScheduler = ReminderScheduler(context)
    val audio = AudioPronouncer(context)
    val answerFeedback = AnswerFeedbackPlayer(context)
    val compactDictionary = CompactDictionary(context)
    val vocabularyFileReader = VocabularyFileReader()
    val importRepository = ImportRepository(database, compactDictionary)
    val importCoordinator = ImportWorkCoordinator(context, database)
}
