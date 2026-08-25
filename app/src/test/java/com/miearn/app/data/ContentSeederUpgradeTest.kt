package com.miearn.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ProgressEntity
import com.miearn.app.data.local.WordEntity
import com.miearn.app.data.seed.ContentSeed
import com.miearn.app.data.seed.ContentSeeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContentSeederUpgradeTest {
    private lateinit var database: AppDatabase
    private lateinit var seeder: ContentSeeder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = ContentSeeder(context, database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun contentUpgradeRemovesOnlyObsoleteBuiltInsAndPreservesProgressAndCustomWords() = runTest {
        database.wordDao().upsertAll(
            listOf(
                word("retained", note = "old"),
                word("invalid-heading"),
                word("custom", isCustom = true),
            ),
        )
        database.progressDao().upsert(ProgressEntity(wordId = "retained", repetitions = 4))

        seeder.syncSeed(
            ContentSeed(
                contentVersion = "2026.08.25-v2.33-examples",
                words = listOf(word("retained", note = "updated")),
            ),
        )

        assertNull(database.wordDao().getById("invalid-heading"))
        assertEquals("updated", database.wordDao().getById("retained")?.note)
        assertEquals(4, database.progressDao().getByWordId("retained")?.repetitions)
        assertNotNull(database.wordDao().getById("custom"))
    }

    private fun word(
        id: String,
        note: String = "",
        isCustom: Boolean = false,
    ) = WordEntity(
        id = id,
        category = if (isCustom) "custom-source" else "mechanical",
        categoryLabel = if (isCustom) "自定义" else "机械专业词汇",
        sourceIndex = 1,
        kind = "TERM",
        section = "",
        english = id,
        primaryEnglish = id,
        phonetic = "/test/",
        chinese = "测试",
        note = note,
        exampleEn = "Check the $id.",
        exampleZh = "检查测试项。",
        audioText = id,
        audioAsset = "audio/$id.ogg",
        isCustom = isCustom,
    )
}
