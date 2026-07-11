package com.miearn.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.DailyActivityEntity
import com.miearn.app.data.local.ReviewEventEntity
import com.miearn.app.data.local.WordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MineSnapshotRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MIearnRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MIearnRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun mineSnapshotCombinesMonthCalendarWeeklyAccuracyAndStreak() = runTest {
        val monday = LocalDate.of(2026, 6, 29)
        val tuesday = LocalDate.of(2026, 6, 30)
        val today = LocalDate.of(2026, 7, 1)
        val word = word("calendar-word")
        database.wordDao().upsert(word)
        database.activityDao().upsert(DailyActivityEntity(monday.toEpochDay(), newCount = 3, reviewCount = 2))
        database.activityDao().upsert(DailyActivityEntity(tuesday.toEpochDay(), newCount = 1, reviewCount = 4))
        database.activityDao().upsert(DailyActivityEntity(today.toEpochDay(), newCount = 5))
        repeat(5) { index ->
            database.eventDao().insert(event(word.id, monday, correct = index < 4))
            database.eventDao().insert(event(word.id, tuesday, correct = index < 3))
        }

        val snapshot = repository.mineSnapshot(
            month = YearMonth.of(2026, 7),
            today = today.toEpochDay(),
        )

        val todayCell = snapshot.calendar.days.single { it.date == today }
        assertEquals(5, todayCell.summary?.newCount)
        assertEquals(15, snapshot.weekly.totalCount)
        assertEquals(0.7f, snapshot.weekly.firstTryAccuracy!!, 0.0001f)
        assertEquals(3, snapshot.weekly.streak)
        assertTrue(snapshot.calendar.canGoPrevious)
        assertFalse(snapshot.calendar.canGoNext)
    }

    @Test
    fun emptyHistoryShowsCurrentMonthWithNoAccuracy() = runTest {
        val today = LocalDate.of(2026, 7, 10)

        val snapshot = repository.mineSnapshot(
            month = YearMonth.from(today),
            today = today.toEpochDay(),
        )

        assertFalse(snapshot.calendar.canGoPrevious)
        assertFalse(snapshot.calendar.canGoNext)
        assertEquals(0, snapshot.weekly.totalCount)
        assertEquals(null, snapshot.weekly.firstTryAccuracy)
    }

    private fun event(wordId: String, date: LocalDate, correct: Boolean) = ReviewEventEntity(
        wordId = wordId,
        category = "mechanical",
        epochMillis = date.toEpochDay() * 86_400_000,
        epochDay = date.toEpochDay(),
        phase = "REVIEW",
        firstCorrect = correct,
        quality = if (correct) 5 else 2,
        responseMillis = 800,
        scheduledIntervalDays = 3,
        nextReviewEpochDay = date.plusDays(3).toEpochDay(),
    )

    private fun word(id: String) = WordEntity(
        id = id,
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 1,
        kind = "TERM",
        section = "",
        english = id,
        primaryEnglish = id,
        phonetic = "/test/",
        chinese = "测试",
        note = "",
        exampleEn = "Example $id",
        exampleZh = "测试例句",
        audioText = id,
        audioAsset = "audio/$id.ogg",
    )
}
