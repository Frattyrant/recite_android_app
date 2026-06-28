package com.miearn.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "miearn_settings")

data class UserSettings(
    val activeCategory: String = "mechanical",
    val dailyGoal: Int = 20,
    val autoPlay: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 10,
    val reminderMinute: Int = 0,
    val reminderPromptShown: Boolean = false,
    val lastCompletedEpochDay: Long? = null,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { values ->
        UserSettings(
            activeCategory = values[ACTIVE_CATEGORY] ?: "mechanical",
            dailyGoal = values[DAILY_GOAL] ?: 20,
            autoPlay = values[AUTO_PLAY] ?: true,
            reminderEnabled = values[REMINDER_ENABLED] ?: false,
            reminderHour = values[REMINDER_HOUR] ?: 10,
            reminderMinute = values[REMINDER_MINUTE] ?: 0,
            reminderPromptShown = values[REMINDER_PROMPT_SHOWN] ?: false,
            lastCompletedEpochDay = values[LAST_COMPLETED_EPOCH_DAY],
        )
    }

    suspend fun setActiveCategory(category: String) {
        context.settingsDataStore.edit { it[ACTIVE_CATEGORY] = category }
    }

    suspend fun setDailyGoal(goal: Int) {
        require(goal in 5..200 && goal % 5 == 0)
        context.settingsDataStore.edit { it[DAILY_GOAL] = goal }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_PLAY] = enabled }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        context.settingsDataStore.edit {
            it[REMINDER_HOUR] = hour
            it[REMINDER_MINUTE] = minute
        }
    }

    suspend fun markReminderPromptShown() {
        context.settingsDataStore.edit { it[REMINDER_PROMPT_SHOWN] = true }
    }

    suspend fun setLastCompletedEpochDay(epochDay: Long) {
        context.settingsDataStore.edit { it[LAST_COMPLETED_EPOCH_DAY] = epochDay }
    }

    private companion object {
        val ACTIVE_CATEGORY = stringPreferencesKey("active_category")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_PROMPT_SHOWN = booleanPreferencesKey("reminder_prompt_shown")
        val LAST_COMPLETED_EPOCH_DAY = longPreferencesKey("last_completed_epoch_day")
    }
}
