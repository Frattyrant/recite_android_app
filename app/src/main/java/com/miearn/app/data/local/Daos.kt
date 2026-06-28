package com.miearn.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Upsert
    suspend fun upsertAll(words: List<WordEntity>)

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: String): WordEntity?

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WordEntity>

    @Query(
        """
        SELECT * FROM words
        WHERE category = :category
          AND (:query = '' OR INSTR(LOWER(english), LOWER(:query)) > 0
               OR INSTR(chinese, :query) > 0)
        ORDER BY sourceIndex
        """,
    )
    fun search(category: String, query: String): Flow<List<WordEntity>>

    @Query(
        """
        SELECT * FROM words
        WHERE :query != ''
          AND (INSTR(LOWER(english), LOWER(:query)) > 0
               OR INSTR(chinese, :query) > 0)
        ORDER BY rowid
        """,
    )
    fun searchAll(query: String): Flow<List<WordEntity>>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Query("SELECT * FROM words WHERE category = :category ORDER BY sourceIndex LIMIT :limit")
    suspend fun categoryWords(category: String, limit: Int): List<WordEntity>

    @Query(
        """
        SELECT w.* FROM words w
        JOIN progress p ON p.wordId = w.id
        WHERE w.category = :category
          AND p.firstLearnedEpochDay IS NOT NULL
        ORDER BY w.sourceIndex
        LIMIT :limit
        """,
    )
    suspend fun learnedWords(category: String, limit: Int): List<WordEntity>

    @Query(
        """
        SELECT w.category AS category,
               w.categoryLabel AS categoryLabel,
               COUNT(*) AS total,
               SUM(CASE WHEN p.firstLearnedEpochDay IS NOT NULL THEN 1 ELSE 0 END) AS learned,
               SUM(CASE WHEN p.mastered = 1 THEN 1 ELSE 0 END) AS mastered
        FROM words w
        LEFT JOIN progress p ON p.wordId = w.id
        GROUP BY w.category, w.categoryLabel
        ORDER BY MIN(w.rowid)
        """,
    )
    fun observeCategoryStats(): Flow<List<CategoryStats>>

    @Query(
        """
        SELECT w.* FROM words w
        JOIN progress p ON p.wordId = w.id
        WHERE p.isFavorite = 1
        ORDER BY w.category, w.sourceIndex
        """,
    )
    fun favorites(): Flow<List<WordEntity>>

    @Query(
        """
        SELECT w.* FROM words w
        JOIN progress p ON p.wordId = w.id
        WHERE p.wrongCount > 0
        ORDER BY p.wrongCount DESC, w.category, w.sourceIndex
        """,
    )
    fun wrongWords(): Flow<List<WordEntity>>

    @Query(
        """
        SELECT w.* FROM words w
        JOIN progress p ON p.wordId = w.id
        WHERE p.mastered = 1
        ORDER BY w.category, w.sourceIndex
        """,
    )
    fun masteredWords(): Flow<List<WordEntity>>
}

@Dao
interface ProgressDao {
    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE wordId = :wordId")
    suspend fun getByWordId(wordId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE wordId IN (:wordIds)")
    suspend fun getByWordIds(wordIds: List<String>): List<ProgressEntity>

    @Query("UPDATE progress SET isFavorite = NOT isFavorite WHERE wordId = :wordId")
    suspend fun toggleFavorite(wordId: String)

    @Query("UPDATE progress SET wrongCount = wrongCount + 1 WHERE wordId = :wordId")
    suspend fun incrementWrong(wordId: String)

    @Query(
        """
        UPDATE progress
        SET wrongCount = CASE WHEN wrongCount > 0 THEN wrongCount - 1 ELSE 0 END
        WHERE wordId = :wordId
        """,
    )
    suspend fun decrementWrong(wordId: String)

    @Query("SELECT COUNT(*) FROM progress WHERE mastered = 1")
    fun observeMasteredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM progress WHERE mastered = 1")
    suspend fun masteredCount(): Int

    @Query("SELECT COUNT(*) FROM progress WHERE wrongCount > 0")
    suspend fun wrongCount(): Int

    @Query("SELECT COUNT(*) FROM progress WHERE firstLearnedEpochDay IS NOT NULL")
    suspend fun learnedCount(): Int

    @Query(
        """
        SELECT lastReviewedEpochDay, intervalDays
        FROM progress
        WHERE firstLearnedEpochDay IS NOT NULL
          AND lastReviewedEpochDay IS NOT NULL
        """,
    )
    suspend fun retentionRows(): List<RetentionRow>

    @Query(
        """
        SELECT nextReviewEpochDay AS epochDay, COUNT(*) AS count
        FROM progress
        WHERE mastered = 0
          AND firstLearnedEpochDay IS NOT NULL
          AND nextReviewEpochDay BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY nextReviewEpochDay
        ORDER BY nextReviewEpochDay
        """,
    )
    suspend fun dueCounts(
        startEpochDay: Long,
        endEpochDay: Long,
    ): List<DueDayCount>

    @Query("DELETE FROM progress WHERE wordId = :wordId")
    suspend fun reset(wordId: String)
}

@Dao
interface StudyDao {
    @Query(
        """
        SELECT w.id FROM words w
        JOIN progress p ON p.wordId = w.id
        WHERE w.category = :category
          AND p.firstLearnedEpochDay IS NOT NULL
          AND p.mastered = 0
          AND p.nextReviewEpochDay <= :today
        ORDER BY p.wrongCount DESC, p.nextReviewEpochDay, w.sourceIndex
        """,
    )
    suspend fun dueWordIds(category: String, today: Long): List<String>

    @Query(
        """
        SELECT w.id FROM words w
        LEFT JOIN progress p ON p.wordId = w.id
        WHERE w.category = :category
          AND p.firstLearnedEpochDay IS NULL
        ORDER BY w.sourceIndex
        LIMIT :limit
        """,
    )
    suspend fun newWordIds(category: String, limit: Int): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM progress p
        JOIN words w ON w.id = p.wordId
        WHERE w.category = :category
          AND p.firstLearnedEpochDay IS NOT NULL
          AND p.mastered = 0
          AND p.nextReviewEpochDay <= :today
        """,
    )
    fun observeDueCount(category: String, today: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM words w
        LEFT JOIN progress p ON p.wordId = w.id
        WHERE w.category = :category
          AND p.firstLearnedEpochDay IS NULL
        """,
    )
    fun observeUnseenCount(category: String): Flow<Int>
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DailyActivityEntity?

    @Query("SELECT * FROM daily_activity ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DailyActivityEntity>>
}

@Dao
interface MetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(metadata: ContentMetadataEntity)

    @Query("SELECT value FROM content_metadata WHERE `key` = :key")
    suspend fun get(key: String): String?
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: ReviewEventEntity): Long

    @Query("SELECT COUNT(*) FROM review_events WHERE wordId = :wordId")
    suspend fun countForWord(wordId: String): Int

    @Query(
        """
        SELECT * FROM review_events
        WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY epochDay, epochMillis, id
        """,
    )
    suspend fun getByEpochDayRange(
        startEpochDay: Long,
        endEpochDay: Long,
    ): List<ReviewEventEntity>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM study_session WHERE slot = 1")
    suspend fun get(): StudySessionEntity?

    @Upsert
    suspend fun upsert(session: StudySessionEntity)

    @Query("DELETE FROM study_session")
    suspend fun delete()
}
