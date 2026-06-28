package com.miearn.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["category", "sourceIndex"]),
        Index(value = ["english"]),
        Index(value = ["chinese"]),
    ],
)
data class WordEntity(
    @PrimaryKey val id: String,
    val category: String,
    val categoryLabel: String,
    val sourceIndex: Int,
    val kind: String,
    val section: String,
    val english: String,
    val primaryEnglish: String,
    val phonetic: String,
    val chinese: String,
    val note: String,
    val exampleEn: String,
    val exampleZh: String,
    val audioText: String,
    val audioAsset: String,
    val isCustom: Boolean = false,
)

@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["nextReviewEpochDay"]),
        Index(value = ["mastered"]),
        Index(value = ["isFavorite"]),
    ],
)
data class ProgressEntity(
    @PrimaryKey val wordId: String,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapseCount: Int = 0,
    val nextReviewEpochDay: Long = 0,
    val mastered: Boolean = false,
    val isFavorite: Boolean = false,
    val wrongCount: Int = 0,
    val lastStudiedEpochDay: Long? = null,
    val lastReviewedEpochDay: Long? = null,
    val firstLearnedEpochDay: Long? = null,
)

@Entity(
    tableName = "review_events",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["wordId"]),
        Index(value = ["epochDay"]),
    ],
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val category: String,
    val epochMillis: Long,
    val epochDay: Long,
    val phase: String,
    val firstCorrect: Boolean,
    val quality: Int,
    val responseMillis: Long,
    val scheduledIntervalDays: Int,
    val nextReviewEpochDay: Long,
)

@Entity(tableName = "study_session")
data class StudySessionEntity(
    @PrimaryKey val slot: Int = 1,
    val epochDay: Long,
    val category: String,
    val phase: String,
    val reviewIdsJson: String,
    val newIdsJson: String,
    val reinforcementIdsJson: String,
    val index: Int,
    val completedNew: Int,
    val completedReview: Int,
    val correctFirstTry: Int,
    val answeredFirstTry: Int,
    val cardExpanded: Boolean,
    val pendingFirstCorrect: Boolean? = null,
)

@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val epochDay: Long,
    val newCount: Int = 0,
    val reviewCount: Int = 0,
)

@Entity(tableName = "content_metadata")
data class ContentMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class CategoryStats(
    val category: String,
    val categoryLabel: String,
    val total: Int,
    val learned: Int,
    val mastered: Int,
)

data class DueDayCount(
    val epochDay: Long,
    val count: Int,
)

data class RetentionRow(
    val lastReviewedEpochDay: Long,
    val intervalDays: Int,
)
