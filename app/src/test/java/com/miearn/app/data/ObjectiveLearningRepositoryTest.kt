package com.miearn.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ProgressEntity
import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.LearningPhase
import com.miearn.app.domain.LearningSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ObjectiveLearningRepositoryTest {
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
    fun firstWrongReviewUpdatesSm2WrongBookEventActivityAndSessionOnce() = runTest {
        val word = word("r1")
        database.wordDao().upsertAll(listOf(word))
        database.progressDao().upsert(
            ProgressEntity(
                wordId = word.id,
                wrongCount = 1,
                repetitions = 2,
                intervalDays = 6,
                nextReviewEpochDay = 200,
                firstLearnedEpochDay = 190,
            ),
        )
        val answered = LearningSession.start(listOf(word.id), emptyList())
            .submitAnswer(firstCorrect = false)

        repository.recordFirstAnswer(
            word = word,
            phase = LearningPhase.REVIEW,
            firstCorrect = false,
            responseMillis = 1_200,
            today = 200,
            epochMillis = 17_280_000_000,
            session = answered,
        )
        repository.recordFirstAnswer(
            word = word,
            phase = LearningPhase.REVIEW,
            firstCorrect = false,
            responseMillis = 1_200,
            today = 200,
            epochMillis = 17_280_000_000,
            session = answered,
        )

        val progress = database.progressDao().getByWordId(word.id)!!
        assertEquals(0, progress.repetitions)
        assertEquals(1, progress.intervalDays)
        assertEquals(1, progress.lapseCount)
        assertEquals(2, progress.wrongCount)
        assertEquals(201, progress.nextReviewEpochDay)
        assertEquals(1, database.eventDao().countForWord(word.id))
        assertEquals(1, database.activityDao().get(200)?.reviewCount)
        assertEquals(false, repository.loadSavedSession()?.pendingFirstCorrect)

        val reinforcement = answered.continueAfterAnswer().submitAnswer(firstCorrect = true)
        repository.recordReinforcementAnswer(reinforcement)

        assertEquals(1, database.eventDao().countForWord(word.id))
        assertEquals(1, database.activityDao().get(200)?.reviewCount)
        assertEquals(1, database.progressDao().getByWordId(word.id)?.lapseCount)
    }

    @Test
    fun firstCorrectNewWordCreatesLearnedProgressAndTomorrowReview() = runTest {
        val word = word("n1")
        database.wordDao().upsertAll(listOf(word))
        val answered = LearningSession.start(emptyList(), listOf(word.id))
            .nextBrowse()
            .submitAnswer(firstCorrect = true)

        repository.recordFirstAnswer(
            word = word,
            phase = LearningPhase.CONSOLIDATE,
            firstCorrect = true,
            responseMillis = 800,
            today = 300,
            epochMillis = 25_920_000_000,
            session = answered,
        )

        val progress = database.progressDao().getByWordId(word.id)!!
        assertEquals(1, progress.repetitions)
        assertEquals(1, progress.intervalDays)
        assertEquals(300L, progress.firstLearnedEpochDay)
        assertEquals(301L, progress.nextReviewEpochDay)
        assertEquals(1, database.activityDao().get(300)?.newCount)
        val event = database.eventDao().getByEpochDayRange(300, 300).single()
        assertEquals(5, event.quality)
        assertEquals("CONSOLIDATE", event.phase)
    }

    @Test
    fun correctAnswerReducesWrongWeightWithoutGoingBelowZero() = runTest {
        val word = word("r1")
        database.wordDao().upsertAll(listOf(word))
        database.progressDao().upsert(
            ProgressEntity(
                wordId = word.id,
                wrongCount = 1,
                firstLearnedEpochDay = 100,
                nextReviewEpochDay = 101,
            ),
        )
        val answered = LearningSession.start(listOf(word.id), emptyList())
            .submitAnswer(firstCorrect = true)

        repository.recordFirstAnswer(
            word,
            LearningPhase.REVIEW,
            true,
            500,
            101,
            8_726_400_000,
            answered,
        )

        assertEquals(0, database.progressDao().getByWordId(word.id)?.wrongCount)
    }

    @Test
    fun sessionRoundTripsAllStateIncludingPendingAnswer() = runTest {
        val state = LearningSession.start(listOf("r1"), listOf("n1", "n2"))
            .submitAnswer(firstCorrect = false)

        repository.saveSession(state, epochDay = 400, category = "mechanical")

        assertEquals(state, repository.loadSavedSession())
        assertNotNull(database.sessionDao().get())
        repository.clearSavedSession()
        assertEquals(null, repository.loadSavedSession())
    }

    @Test
    fun favoriteOnlyProgressRemainsEligibleAsNewWord() = runTest {
        val word = word("n1")
        database.wordDao().upsertAll(listOf(word))
        repository.toggleFavorite(word.id)

        val session = repository.createSession("mechanical", dailyGoal = 20, today = 500)

        assertEquals(listOf(word.id), session.newIds)
    }

    @Test
    fun insightsAggregateEventsProgressRetentionAndFutureDueDays() = runTest {
        val words = listOf(word("review"), word("new"), word("mastered"))
        database.wordDao().upsertAll(words)
        database.progressDao().upsert(
            ProgressEntity(
                wordId = "review",
                intervalDays = 10,
                nextReviewEpochDay = 101,
                lastReviewedEpochDay = 95,
                firstLearnedEpochDay = 90,
            ),
        )
        database.progressDao().upsert(
            ProgressEntity(
                wordId = "new",
                intervalDays = 1,
                nextReviewEpochDay = 101,
                wrongCount = 2,
                lastReviewedEpochDay = 100,
                firstLearnedEpochDay = 100,
            ),
        )
        database.progressDao().upsert(
            ProgressEntity(
                wordId = "mastered",
                intervalDays = 30,
                nextReviewEpochDay = 130,
                mastered = true,
                lastReviewedEpochDay = 99,
                firstLearnedEpochDay = 70,
            ),
        )
        database.eventDao().insert(
            event(
                wordId = "new",
                phase = LearningPhase.CONSOLIDATE,
                epochDay = 99,
                correct = false,
            ),
        )
        database.eventDao().insert(
            event(
                wordId = "review",
                phase = LearningPhase.REVIEW,
                epochDay = 100,
                correct = true,
            ),
        )

        val snapshot = repository.insightsSnapshot(today = 100, days = 7)

        assertEquals(3, snapshot.learned)
        assertEquals(1, snapshot.mastered)
        assertEquals(1, snapshot.wrong)
        assertEquals(0.5f, snapshot.firstTryAccuracy)
        assertEquals(1, snapshot.days.single { it.epochDay == 99L }.newCount)
        assertEquals(1, snapshot.days.single { it.epochDay == 100L }.reviewCount)
        assertEquals(2, snapshot.futureDue[101])
        assertEquals(31, snapshot.retentionCurve.size)
        assertTrue(snapshot.averageRetention in 0.0..1.0)
    }

    private fun event(
        wordId: String,
        phase: LearningPhase,
        epochDay: Long,
        correct: Boolean,
    ) = com.miearn.app.data.local.ReviewEventEntity(
        wordId = wordId,
        category = "mechanical",
        epochMillis = epochDay * 86_400_000,
        epochDay = epochDay,
        phase = phase.name,
        firstCorrect = correct,
        quality = if (correct) 5 else 2,
        responseMillis = 700,
        scheduledIntervalDays = 1,
        nextReviewEpochDay = epochDay + 1,
    )

    private fun word(id: String) = WordEntity(
        id = id,
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = id.hashCode(),
        kind = "TERM",
        section = "",
        english = id,
        primaryEnglish = id,
        phonetic = "/test/",
        chinese = "中文",
        note = "",
        exampleEn = "Example $id",
        exampleZh = "例句",
        audioText = id,
        audioAsset = "audio/$id.ogg",
    )
}
