package com.miearn.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WordEntity::class,
        ProgressEntity::class,
        ReviewEventEntity::class,
        StudySessionEntity::class,
        DailyActivityEntity::class,
        ContentMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
    abstract fun studyDao(): StudyDao
    abstract fun activityDao(): ActivityDao
    abstract fun metadataDao(): MetadataDao
    abstract fun eventDao(): EventDao
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `progress_new` (
                        `wordId` TEXT NOT NULL,
                        `easeFactor` REAL NOT NULL DEFAULT 2.5,
                        `intervalDays` INTEGER NOT NULL DEFAULT 0,
                        `repetitions` INTEGER NOT NULL DEFAULT 0,
                        `lapseCount` INTEGER NOT NULL DEFAULT 0,
                        `nextReviewEpochDay` INTEGER NOT NULL DEFAULT 0,
                        `mastered` INTEGER NOT NULL DEFAULT 0,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `wrongCount` INTEGER NOT NULL DEFAULT 0,
                        `lastStudiedEpochDay` INTEGER,
                        `lastReviewedEpochDay` INTEGER,
                        `firstLearnedEpochDay` INTEGER,
                        PRIMARY KEY(`wordId`),
                        FOREIGN KEY(`wordId`) REFERENCES `words`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `progress_new` (
                        `wordId`,
                        `easeFactor`,
                        `intervalDays`,
                        `repetitions`,
                        `lapseCount`,
                        `nextReviewEpochDay`,
                        `mastered`,
                        `isFavorite`,
                        `wrongCount`,
                        `lastStudiedEpochDay`,
                        `lastReviewedEpochDay`,
                        `firstLearnedEpochDay`
                    )
                    SELECT
                        `wordId`,
                        2.5,
                        CASE
                            WHEN `lastStudiedEpochDay` IS NULL THEN 0
                            ELSE MAX(0, `nextReviewEpochDay` - `lastStudiedEpochDay`)
                        END,
                        `consecutiveKnown`,
                        0,
                        `nextReviewEpochDay`,
                        `mastered`,
                        `isFavorite`,
                        `wrongCount`,
                        `lastStudiedEpochDay`,
                        `lastStudiedEpochDay`,
                        `lastStudiedEpochDay`
                    FROM `progress`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `progress`")
                db.execSQL("ALTER TABLE `progress_new` RENAME TO `progress`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_progress_nextReviewEpochDay` ON `progress` (`nextReviewEpochDay`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_progress_mastered` ON `progress` (`mastered`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_progress_isFavorite` ON `progress` (`isFavorite`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `review_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `wordId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `epochMillis` INTEGER NOT NULL,
                        `epochDay` INTEGER NOT NULL,
                        `phase` TEXT NOT NULL,
                        `firstCorrect` INTEGER NOT NULL,
                        `quality` INTEGER NOT NULL,
                        `responseMillis` INTEGER NOT NULL,
                        `scheduledIntervalDays` INTEGER NOT NULL,
                        `nextReviewEpochDay` INTEGER NOT NULL,
                        FOREIGN KEY(`wordId`) REFERENCES `words`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_events_wordId` ON `review_events` (`wordId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_events_epochDay` ON `review_events` (`epochDay`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_session` (
                        `slot` INTEGER NOT NULL,
                        `epochDay` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `reviewIdsJson` TEXT NOT NULL,
                        `newIdsJson` TEXT NOT NULL,
                        `reinforcementIdsJson` TEXT NOT NULL,
                        `index` INTEGER NOT NULL,
                        `completedNew` INTEGER NOT NULL,
                        `completedReview` INTEGER NOT NULL,
                        `correctFirstTry` INTEGER NOT NULL,
                        `answeredFirstTry` INTEGER NOT NULL,
                        `cardExpanded` INTEGER NOT NULL,
                        `pendingFirstCorrect` INTEGER,
                        PRIMARY KEY(`slot`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "miearn.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
