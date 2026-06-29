package com.miearn.app.data

import androidx.room.withTransaction
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.DailyActivityEntity
import com.miearn.app.data.local.ProgressEntity
import com.miearn.app.data.local.ReviewEventEntity
import com.miearn.app.data.local.StudySessionEntity
import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.LearningPhase
import com.miearn.app.domain.LearningInsights
import com.miearn.app.domain.LearningSession
import com.miearn.app.domain.RetentionInput
import com.miearn.app.domain.ReviewScheduler
import com.miearn.app.domain.StudyProgress
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.time.LocalDate

class MIearnRepository(private val database: AppDatabase) {
    val categoryStats = database.wordDao().observeCategoryStats()
    val activities = database.activityDao().observeAll()
    val masteredCount = database.progressDao().observeMasteredCount()

    fun search(category: String, query: String): Flow<List<WordEntity>> =
        database.wordDao().search(category, query.trim())

    fun searchAll(query: String): Flow<List<WordEntity>> =
        database.wordDao().searchAll(query.trim())

    fun favorites(): Flow<List<WordEntity>> = database.wordDao().favorites()
    fun wrongWords(): Flow<List<WordEntity>> = database.wordDao().wrongWords()
    fun masteredWords(): Flow<List<WordEntity>> = database.wordDao().masteredWords()

    fun dueCount(category: String, today: Long): Flow<Int> =
        database.studyDao().observeDueCount(category, today)

    fun unseenCount(category: String): Flow<Int> =
        database.studyDao().observeUnseenCount(category)

    suspend fun createSession(
        category: String,
        dailyGoal: Int,
        today: Long,
    ): LearningSession = database.withTransaction {
        val due = database.studyDao().dueWordIds(category, today)
        val fresh = database.studyDao().newWordIds(category, dailyGoal)
        val session = LearningSession.start(due, fresh)
        database.sessionDao().upsert(session.toEntity(today, category))
        session
    }

    suspend fun saveSession(
        session: LearningSession,
        epochDay: Long,
        category: String,
    ) {
        database.sessionDao().upsert(session.toEntity(epochDay, category))
    }

    suspend fun loadSavedSession(): LearningSession? =
        database.sessionDao().get()?.toDomain()

    suspend fun loadSavedSessionRecord(): SavedLearningSession? =
        database.sessionDao().get()?.let {
            SavedLearningSession(
                epochDay = it.epochDay,
                category = it.category,
                session = it.toDomain(),
            )
        }

    suspend fun clearSavedSession() {
        database.sessionDao().delete()
    }

    suspend fun wordsByIds(ids: List<String>): Map<String, WordEntity> =
        if (ids.isEmpty()) emptyMap()
        else database.wordDao().getByIds(ids).associateBy { it.id }

    suspend fun insightsSnapshot(
        today: Long,
        days: Int = 30,
    ): InsightsSnapshot {
        require(days > 0)
        val start = today - days + 1
        val events = database.eventDao().getByEpochDayRange(start, today)
        val daily = (start..today).map { day ->
            val dayEvents = events.filter { it.epochDay == day }
            InsightDay(
                epochDay = day,
                newCount = dayEvents.count { it.phase == LearningPhase.CONSOLIDATE.name },
                reviewCount = dayEvents.count { it.phase == LearningPhase.REVIEW.name },
                correctFirstTry = dayEvents.count(ReviewEventEntity::firstCorrect),
                answeredFirstTry = dayEvents.size,
            )
        }
        val retention = database.progressDao().retentionRows().map {
            RetentionInput(
                lastReviewedEpochDay = it.lastReviewedEpochDay,
                intervalDays = it.intervalDays,
            )
        }
        return InsightsSnapshot(
            days = daily,
            learned = database.progressDao().learnedCount(),
            mastered = database.progressDao().masteredCount(),
            wrong = database.progressDao().wrongCount(),
            firstTryAccuracy = LearningInsights.accuracy(
                correct = events.count(ReviewEventEntity::firstCorrect),
                total = events.size,
            ),
            averageRetention = LearningInsights.averageRetention(retention, today),
            retentionCurve = LearningInsights.retentionCurve(
                retention,
                today = today,
                horizonDays = 30,
            ),
            futureDue = database.progressDao().dueCounts(today, today + 6)
                .associate { it.epochDay to it.count },
        )
    }

    suspend fun recordFirstAnswer(
        word: WordEntity,
        phase: LearningPhase,
        firstCorrect: Boolean,
        responseMillis: Long,
        today: Long,
        epochMillis: Long = System.currentTimeMillis(),
        session: LearningSession,
    ) {
        require(phase == LearningPhase.REVIEW || phase == LearningPhase.CONSOLIDATE)
        require(session.pendingFirstCorrect != null) {
            "session must contain the submitted first answer"
        }
        database.withTransaction {
            val saved = database.sessionDao().get()
            if (
                saved != null &&
                saved.phase == phase.name &&
                saved.index == session.index &&
                saved.pendingFirstCorrect != null
            ) {
                return@withTransaction
            }

            val old = database.progressDao().getByWordId(word.id)
            val quality = if (firstCorrect) 5 else 2
            val outcome = ReviewScheduler.grade(
                current = old?.toDomain() ?: StudyProgress(),
                quality = quality,
                todayEpochDay = today,
            )
            val wrongCount = if (firstCorrect) {
                maxOf(0, (old?.wrongCount ?: 0) - 1)
            } else {
                (old?.wrongCount ?: 0) + 1
            }
            database.progressDao().upsert(
                ProgressEntity(
                    wordId = word.id,
                    easeFactor = outcome.progress.easeFactor,
                    intervalDays = outcome.progress.intervalDays,
                    repetitions = outcome.progress.repetitions,
                    lapseCount = outcome.progress.lapseCount,
                    nextReviewEpochDay = outcome.progress.nextReviewEpochDay,
                    mastered = outcome.progress.mastered,
                    isFavorite = old?.isFavorite ?: false,
                    wrongCount = wrongCount,
                    lastStudiedEpochDay = today,
                    lastReviewedEpochDay = outcome.progress.lastReviewedEpochDay,
                    firstLearnedEpochDay = outcome.progress.firstLearnedEpochDay,
                ),
            )
            database.eventDao().insert(
                ReviewEventEntity(
                    wordId = word.id,
                    category = saved?.category ?: word.category,
                    epochMillis = epochMillis,
                    epochDay = today,
                    phase = phase.name,
                    firstCorrect = firstCorrect,
                    quality = quality,
                    responseMillis = responseMillis.coerceAtLeast(0),
                    scheduledIntervalDays = outcome.progress.intervalDays,
                    nextReviewEpochDay = outcome.progress.nextReviewEpochDay,
                ),
            )
            val activity = database.activityDao().get(today) ?: DailyActivityEntity(today)
            database.activityDao().upsert(
                if (phase == LearningPhase.REVIEW) {
                    activity.copy(reviewCount = activity.reviewCount + 1)
                } else {
                    activity.copy(newCount = activity.newCount + 1)
                },
            )
            database.sessionDao().upsert(session.toEntity(today, saved?.category ?: word.category))
        }
    }

    suspend fun recordReinforcementAnswer(session: LearningSession) {
        database.withTransaction {
            val saved = database.sessionDao().get() ?: return@withTransaction
            database.sessionDao().upsert(
                session.toEntity(saved.epochDay, saved.category),
            )
        }
    }

    suspend fun toggleFavorite(wordId: String) {
        database.withTransaction {
            val old = database.progressDao().getByWordId(wordId)
            if (old == null) {
                database.progressDao().upsert(ProgressEntity(wordId = wordId, isFavorite = true))
            } else {
                database.progressDao().toggleFavorite(wordId)
            }
        }
    }

    suspend fun markWrong(wordId: String) {
        database.withTransaction {
            val old = database.progressDao().getByWordId(wordId)
            if (old == null) {
                database.progressDao().upsert(ProgressEntity(wordId = wordId, wrongCount = 1))
            } else {
                database.progressDao().incrementWrong(wordId)
            }
        }
    }

    suspend fun quizWords(category: String, learnedOnly: Boolean, limit: Int): List<WordEntity> =
        if (learnedOnly) {
            database.wordDao().learnedWords(category, limit)
        } else {
            database.wordDao().categoryWords(category, limit)
        }

    companion object {
        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

        fun calculateStreak(activities: List<DailyActivityEntity>, today: Long): Int {
            val activeDays = activities
                .filter { it.newCount + it.reviewCount > 0 }
                .map { it.epochDay }
                .toSet()
            var day = if (today in activeDays) today else today - 1
            var streak = 0
            while (day in activeDays) {
                streak += 1
                day -= 1
            }
            return streak
        }
    }
}

private fun ProgressEntity.toDomain() = StudyProgress(
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    repetitions = repetitions,
    lapseCount = lapseCount,
    nextReviewEpochDay = nextReviewEpochDay,
    mastered = mastered,
    lastReviewedEpochDay = lastReviewedEpochDay,
    firstLearnedEpochDay = firstLearnedEpochDay,
)

data class SavedLearningSession(
    val epochDay: Long,
    val category: String,
    val session: LearningSession,
)

data class InsightDay(
    val epochDay: Long,
    val newCount: Int,
    val reviewCount: Int,
    val correctFirstTry: Int,
    val answeredFirstTry: Int,
)

data class InsightsSnapshot(
    val days: List<InsightDay>,
    val learned: Int,
    val mastered: Int,
    val wrong: Int,
    val firstTryAccuracy: Float,
    val averageRetention: Double,
    val retentionCurve: List<Double>,
    val futureDue: Map<Long, Int>,
)

private fun LearningSession.toEntity(
    epochDay: Long,
    category: String,
) = StudySessionEntity(
    epochDay = epochDay,
    category = category,
    phase = phase.name,
    reviewIdsJson = reviewIds.toJson(),
    newIdsJson = newIds.toJson(),
    reinforcementIdsJson = reinforcementIds.toJson(),
    index = index,
    completedNew = completedNew,
    completedReview = completedReview,
    correctFirstTry = correctFirstTry,
    answeredFirstTry = answeredFirstTry,
    cardExpanded = cardExpanded,
    pendingFirstCorrect = pendingFirstCorrect,
)

private fun StudySessionEntity.toDomain() = LearningSession(
    reviewIds = reviewIdsJson.toStringList(),
    newIds = newIdsJson.toStringList(),
    reinforcementIds = reinforcementIdsJson.toStringList(),
    phase = LearningPhase.valueOf(phase),
    index = index,
    completedNew = completedNew,
    completedReview = completedReview,
    correctFirstTry = correctFirstTry,
    answeredFirstTry = answeredFirstTry,
    cardExpanded = cardExpanded,
    pendingFirstCorrect = pendingFirstCorrect,
)

private fun List<String>.toJson(): String =
    JSONArray().also { array -> forEach(array::put) }.toString()

private fun String.toStringList(): List<String> {
    val array = JSONArray(this)
    return List(array.length()) { index -> array.getString(index) }
}
