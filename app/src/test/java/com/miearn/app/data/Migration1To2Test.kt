package com.miearn.app.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ReviewEventEntity
import com.miearn.app.data.local.StudySessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class Migration1To2Test {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "migration-${System.nanoTime()}.db"
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesTrueV1SchemaWithoutLosingProgressAndCreatesV2Tables() = runTest {
        createVersion1Database()

        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val migrated = database!!.progressDao().getByWordId("one")
        assertNotNull(migrated)
        assertEquals(2.5, migrated!!.easeFactor, 0.0001)
        assertEquals(5, migrated.intervalDays)
        assertEquals(3, migrated.repetitions)
        assertEquals(0, migrated.lapseCount)
        assertEquals(20_005L, migrated.nextReviewEpochDay)
        assertTrue(migrated.mastered)
        assertTrue(migrated.isFavorite)
        assertEquals(2, migrated.wrongCount)
        assertEquals(20_000L, migrated.lastStudiedEpochDay)
        assertEquals(20_000L, migrated.lastReviewedEpochDay)
        assertEquals(20_000L, migrated.firstLearnedEpochDay)
        assertFalse(database!!.wordDao().getById("one")!!.isCustom)
        assertEquals(
            "mechanical",
            database!!.sourceDao().getById("mechanical")?.sourceId,
        )
        assertEquals(
            listOf("one"),
            database!!.sourceDao().wordIds("mechanical"),
        )

        val progressColumns = database!!.openHelper.readableDatabase
            .query("PRAGMA table_info(`progress`)")
            .use { cursor ->
                buildSet {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
        assertFalse("consecutiveKnown" in progressColumns)
        assertFalse("lastRating" in progressColumns)

        database!!.eventDao().insert(
            ReviewEventEntity(
                wordId = "one",
                category = "mechanical",
                epochMillis = 1_728_000_000_000,
                epochDay = 20_000,
                phase = "REVIEW",
                firstCorrect = false,
                quality = 2,
                responseMillis = 1_200,
                scheduledIntervalDays = 1,
                nextReviewEpochDay = 20_001,
            ),
        )
        assertEquals(1, database!!.eventDao().countForWord("one"))

        database!!.sessionDao().upsert(
            StudySessionEntity(
                epochDay = 20_000,
                category = "mechanical",
                phase = "REVIEW",
                reviewIdsJson = """["one"]""",
                newIdsJson = "[]",
                reinforcementIdsJson = "[]",
                index = 0,
                completedNew = 0,
                completedReview = 0,
                correctFirstTry = 0,
                answeredFirstTry = 0,
                cardExpanded = false,
            ),
        )
        assertEquals("mechanical", database!!.sessionDao().get()?.category)
    }

    private fun createVersion1Database() {
        val sqlite = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        sqlite.execSQL(
            """
            CREATE TABLE words (
                id TEXT NOT NULL PRIMARY KEY,
                category TEXT NOT NULL,
                categoryLabel TEXT NOT NULL,
                sourceIndex INTEGER NOT NULL,
                kind TEXT NOT NULL,
                section TEXT NOT NULL,
                english TEXT NOT NULL,
                primaryEnglish TEXT NOT NULL,
                phonetic TEXT NOT NULL,
                chinese TEXT NOT NULL,
                note TEXT NOT NULL,
                exampleEn TEXT NOT NULL,
                exampleZh TEXT NOT NULL,
                audioText TEXT NOT NULL,
                audioAsset TEXT NOT NULL
            )
            """.trimIndent(),
        )
        sqlite.execSQL("CREATE UNIQUE INDEX index_words_category_sourceIndex ON words(category, sourceIndex)")
        sqlite.execSQL("CREATE INDEX index_words_english ON words(english)")
        sqlite.execSQL("CREATE INDEX index_words_chinese ON words(chinese)")
        sqlite.execSQL(
            """
            CREATE TABLE progress (
                wordId TEXT NOT NULL PRIMARY KEY,
                consecutiveKnown INTEGER NOT NULL,
                nextReviewEpochDay INTEGER NOT NULL,
                mastered INTEGER NOT NULL,
                lastRating TEXT,
                isFavorite INTEGER NOT NULL,
                wrongCount INTEGER NOT NULL,
                lastStudiedEpochDay INTEGER,
                FOREIGN KEY(wordId) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        sqlite.execSQL("CREATE INDEX index_progress_nextReviewEpochDay ON progress(nextReviewEpochDay)")
        sqlite.execSQL("CREATE INDEX index_progress_mastered ON progress(mastered)")
        sqlite.execSQL("CREATE INDEX index_progress_isFavorite ON progress(isFavorite)")
        sqlite.execSQL(
            "CREATE TABLE daily_activity (epochDay INTEGER NOT NULL PRIMARY KEY, newCount INTEGER NOT NULL, reviewCount INTEGER NOT NULL)",
        )
        sqlite.execSQL(
            "CREATE TABLE content_metadata (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
        )
        sqlite.execSQL(
            """
            INSERT INTO words VALUES (
                'one', 'mechanical', 'Mechanical', 1, 'TERM', '', 'one', 'one',
                '/wun/', '一', '', '', '', 'one', 'audio/one.ogg'
            )
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            INSERT INTO progress VALUES (
                'one', 3, 20005, 1, 'KNOWN', 1, 2, 20000
            )
            """.trimIndent(),
        )
        sqlite.version = 1
        sqlite.close()
    }
}
