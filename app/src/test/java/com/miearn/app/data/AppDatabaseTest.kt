package com.miearn.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ProgressEntity
import com.miearn.app.data.local.ReviewEventEntity
import com.miearn.app.data.local.StudySessionEntity
import com.miearn.app.data.local.WordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun searchesEnglishAndChineseWithinCategory() = runTest {
        database.wordDao().upsertAll(
            listOf(
                word(id = "one", english = "limit switch", chinese = "限位开关"),
                word(id = "two", english = "sensor", chinese = "传感器", sourceIndex = 2),
            ),
        )

        assertEquals(listOf("one"), database.wordDao().search("mechanical", "limit").first().map { it.id })
        assertEquals("传感器", database.wordDao().getById("two")?.chinese)
        assertEquals(listOf("two"), database.wordDao().search("mechanical", "传感").first().map { it.id })
    }

    @Test
    fun globalSearchCrossesCategoriesButBlankQueryReturnsNothing() = runTest {
        database.wordDao().upsertAll(
            listOf(
                word(id = "mechanical", english = "fixture", chinese = "夹具"),
                word(
                    id = "electrical",
                    english = "fixture sensor",
                    chinese = "夹具传感器",
                    category = "electrical",
                ),
            ),
        )

        assertEquals(
            listOf("mechanical", "electrical"),
            database.wordDao().searchAll("fixture").first().map { it.id },
        )
        assertEquals(emptyList<String>(), database.wordDao().searchAll("").first().map { it.id })
    }

    @Test
    fun contentUpsertPreservesExistingProgress() = runTest {
        val original = word(id = "one", english = "limit switch", chinese = "限位开关")
        database.wordDao().upsertAll(listOf(original))
        database.progressDao().upsert(ProgressEntity(wordId = "one", repetitions = 2))

        database.wordDao().upsertAll(listOf(original.copy(note = "updated content")))

        assertEquals("updated content", database.wordDao().getById("one")?.note)
        assertEquals(2, database.progressDao().getByWordId("one")?.repetitions)
    }

    @Test
    fun returnsDueWordsBeforeUnseenWords() = runTest {
        database.wordDao().upsertAll(
            listOf(
                word(id = "due", sourceIndex = 1),
                word(id = "future", sourceIndex = 2),
                word(id = "new", sourceIndex = 3),
            ),
        )
        database.progressDao().upsert(
            ProgressEntity(
                wordId = "due",
                nextReviewEpochDay = 20_000,
                firstLearnedEpochDay = 19_999,
            ),
        )
        database.progressDao().upsert(
            ProgressEntity(
                wordId = "future",
                nextReviewEpochDay = 20_003,
                firstLearnedEpochDay = 19_999,
            ),
        )

        assertEquals(listOf("due"), database.studyDao().dueWordIds("mechanical", 20_000))
        assertEquals(listOf("new"), database.studyDao().newWordIds("mechanical", 20))
        assertNotNull(database.wordDao().getById("new"))
    }

    @Test
    fun reviewEventsCanBeInsertedCountedAndQueriedByEpochDayRange() = runTest {
        database.wordDao().upsertAll(listOf(word(id = "one")))
        database.eventDao().insert(reviewEvent(wordId = "one", epochDay = 100))
        database.eventDao().insert(reviewEvent(wordId = "one", epochDay = 102))
        database.eventDao().insert(reviewEvent(wordId = "one", epochDay = 105))

        assertEquals(3, database.eventDao().countForWord("one"))
        assertEquals(
            listOf(100L, 102L),
            database.eventDao().getByEpochDayRange(99, 102).map { it.epochDay },
        )
    }

    @Test
    fun singletonStudySessionCanBeReplacedAndDeleted() = runTest {
        val initial = studySession(phase = "REVIEW", index = 1)
        database.sessionDao().upsert(initial)
        database.sessionDao().upsert(initial.copy(phase = "BROWSE", index = 2))

        assertEquals("BROWSE", database.sessionDao().get()?.phase)
        assertEquals(2, database.sessionDao().get()?.index)

        database.sessionDao().delete()

        assertEquals(null, database.sessionDao().get())
    }

    private fun reviewEvent(wordId: String, epochDay: Long) = ReviewEventEntity(
        wordId = wordId,
        category = "mechanical",
        epochMillis = epochDay * 86_400_000,
        epochDay = epochDay,
        phase = "REVIEW",
        firstCorrect = true,
        quality = 5,
        responseMillis = 800,
        scheduledIntervalDays = 3,
        nextReviewEpochDay = epochDay + 3,
    )

    private fun studySession(phase: String, index: Int) = StudySessionEntity(
        epochDay = 100,
        category = "mechanical",
        phase = phase,
        reviewIdsJson = """["one"]""",
        newIdsJson = "[]",
        reinforcementIdsJson = "[]",
        index = index,
        completedNew = 0,
        completedReview = 1,
        correctFirstTry = 1,
        answeredFirstTry = 1,
        cardExpanded = false,
    )

    private fun word(
        id: String,
        english: String = id,
        chinese: String = "中文",
        sourceIndex: Int = 1,
        category: String = "mechanical",
    ) = WordEntity(
        id = id,
        category = category,
        categoryLabel = if (category == "mechanical") "机械专业词汇" else "电气专业词汇",
        sourceIndex = sourceIndex,
        kind = "TERM",
        section = "",
        english = english,
        primaryEnglish = english,
        phonetic = "/test/",
        chinese = chinese,
        note = "",
        exampleEn = "The technician checked the $english.",
        exampleZh = "技术员检查了$chinese。",
        audioText = english,
        audioAsset = "audio/$id.ogg",
    )
}
