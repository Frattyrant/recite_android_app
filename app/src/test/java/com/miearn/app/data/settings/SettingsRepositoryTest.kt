package com.miearn.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryTest {
    @Test
    fun dailyGoalAcceptsFiveStepValuesFromFiveThroughTwoHundred() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        for (goal in listOf(5, 10, 200)) {
            repository.setDailyGoal(goal)
            assertEquals(goal, repository.settings.first().dailyGoal)
        }
    }

    @Test
    fun dailyGoalRejectsOutOfRangeAndNonStepValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        for (goal in listOf(0, 6, 205)) {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { repository.setDailyGoal(goal) }
            }
        }
    }
}
