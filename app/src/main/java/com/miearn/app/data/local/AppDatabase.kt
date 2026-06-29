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
        SourceEntity::class,
        WordSourceCrossRef::class,
        ImportJobEntity::class,
        ImportDraftEntity::class,
    ],
    version = 3,
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
    abstract fun sourceDao(): SourceDao
    abstract fun importDao(): ImportDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `words` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_words_category_sourceIndex`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_category_sourceIndex` ON `words` (`category`, `sourceIndex`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sources` (
                        `sourceId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `originalFileName` TEXT,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        `wordCount` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `word_source` (
                        `sourceId` TEXT NOT NULL,
                        `wordId` TEXT NOT NULL,
                        `importOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`, `wordId`),
                        FOREIGN KEY(`sourceId`) REFERENCES `sources`(`sourceId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`wordId`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_source_wordId` ON `word_source` (`wordId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_jobs` (
                        `jobId` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `originalFileName` TEXT NOT NULL,
                        `internalFilePath` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `processedRows` INTEGER NOT NULL,
                        `totalRows` INTEGER NOT NULL,
                        `validRows` INTEGER NOT NULL,
                        `invalidRows` INTEGER NOT NULL,
                        `duplicateRows` INTEGER NOT NULL,
                        `mappingJson` TEXT NOT NULL,
                        `headersJson` TEXT NOT NULL,
                        `previewRowsJson` TEXT NOT NULL,
                        `conflictPolicy` TEXT,
                        `errorMessage` TEXT,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`jobId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_drafts` (
                        `jobId` TEXT NOT NULL,
                        `rowIndex` INTEGER NOT NULL,
                        `normalizedEnglish` TEXT NOT NULL,
                        `english` TEXT NOT NULL,
                        `primaryEnglish` TEXT NOT NULL,
                        `phonetic` TEXT NOT NULL,
                        `chinese` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `exampleEn` TEXT NOT NULL,
                        `exampleZh` TEXT NOT NULL,
                        `existingWordId` TEXT,
                        `validationError` TEXT,
                        PRIMARY KEY(`jobId`, `rowIndex`),
                        FOREIGN KEY(`jobId`) REFERENCES `import_jobs`(`jobId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_drafts_jobId` ON `import_drafts` (`jobId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_drafts_normalizedEnglish` ON `import_drafts` (`normalizedEnglish`)")
                db.execSQL(
                    """
                    INSERT INTO `sources` (
                        `sourceId`, `displayName`, `type`, `originalFileName`,
                        `createdAtEpochMillis`, `updatedAtEpochMillis`, `wordCount`
                    )
                    SELECT `category`, MIN(`categoryLabel`), 'BUILTIN', NULL, 0, 0, COUNT(*)
                    FROM `words`
                    GROUP BY `category`
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `word_source` (`sourceId`, `wordId`, `importOrder`)
                    SELECT `category`, `id`, `sourceIndex` FROM `words`
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
