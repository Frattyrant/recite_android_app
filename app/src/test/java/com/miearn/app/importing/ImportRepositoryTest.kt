package com.miearn.app.importing

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ProgressEntity
import com.miearn.app.data.local.SourceEntity
import com.miearn.app.data.local.SourceType
import com.miearn.app.data.local.StudySessionEntity
import com.miearn.app.data.local.WordEntity
import com.miearn.app.data.local.WordSourceCrossRef
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ImportRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ImportRepository(
            database,
            DictionaryLookup { word ->
                if (word == "actuator") {
                    DictionaryEntry("actuator", "/ˈæktʃueɪtə/", "执行器", "")
                } else {
                    null
                }
            },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun prepareEnrichesNewWordAndKeepExistingPreservesProgress() = runTest {
        seedBuiltInFixture()
        val job = job("job-1", "source-1")
        database.importDao().upsertJob(job)

        val result = repository.prepare(
            job,
            listOf(
                ImportedVocabularyRow(2, "fixture", chinese = "导入释义"),
                ImportedVocabularyRow(3, "actuator"),
            ),
        )
        assertEquals(2, result.validRows)
        val drafts = database.importDao().drafts(job.jobId)
        assertEquals("执行器", drafts.single { it.english == "actuator" }.chinese)

        repository.commit(job.jobId, ImportConflictPolicy.KEEP_EXISTING)

        assertEquals("现有释义", database.wordDao().getById("fixture")!!.chinese)
        assertEquals(6, database.progressDao().getByWordId("fixture")!!.intervalDays)
        val imported = database.wordDao().findCanonicalWord("actuator")
        assertNotNull(imported)
        assertTrue(imported!!.isCustom)
        assertEquals("执行器", imported.chinese)
        assertEquals(
            listOf("fixture", imported.id),
            database.sourceDao().wordIds(job.sourceId),
        )
    }

    @Test
    fun deleteSourceOnlyRemovesOrphanedCustomWords() = runTest {
        seedBuiltInFixture()
        val job = job("job-2", "source-2")
        database.importDao().upsertJob(job)
        repository.prepare(
            job,
            listOf(
                ImportedVocabularyRow(2, "fixture"),
                ImportedVocabularyRow(3, "actuator"),
            ),
        )
        repository.commit(job.jobId, ImportConflictPolicy.KEEP_EXISTING)
        val customId = requireNotNull(database.wordDao().findCanonicalWord("actuator")).id
        database.progressDao().upsert(ProgressEntity(customId, intervalDays = 3))
        database.sessionDao().upsert(
            StudySessionEntity(
                epochDay = 200,
                category = job.sourceId,
                phase = "REVIEW",
                reviewIdsJson = "[\"$customId\"]",
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

        assertTrue(repository.deleteSource(job.sourceId))

        assertNotNull(database.wordDao().getById("fixture"))
        assertFalse(database.wordDao().getById("fixture")!!.isCustom)
        assertNull(database.wordDao().getById(customId))
        assertNull(database.progressDao().getByWordId(customId))
        assertNull(database.sessionDao().get())
    }

    @Test
    fun renameSourceUpdatesLabelsForItsCustomWords() = runTest {
        val job = job("job-rename", "source-rename")
        database.importDao().upsertJob(job)
        repository.prepare(
            job,
            listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")),
        )
        repository.commit(job.jobId, ImportConflictPolicy.KEEP_EXISTING)
        val customId = requireNotNull(database.wordDao().findCanonicalWord("actuator")).id

        assertTrue(repository.renameSource(job.sourceId, "设备词库"))
        assertEquals("设备词库", database.wordDao().getById(customId)?.categoryLabel)
        assertEquals("设备词库", database.sourceDao().getById(job.sourceId)?.displayName)
    }

    @Test
    fun renamingSourceToAnExistingNameReceivesReadableSuffix() = runTest {
        val first = job("job-rename-collision-1", "source-rename-collision-1")
            .copy(sourceName = "专业词库")
        database.importDao().upsertJob(first)
        repository.prepare(
            first,
            listOf(ImportedVocabularyRow(2, "fixture", chinese = "夹具")),
        )
        repository.commit(first.jobId, ImportConflictPolicy.KEEP_EXISTING)

        val second = job("job-rename-collision-2", "source-rename-collision-2")
            .copy(sourceName = "临时词库")
        database.importDao().upsertJob(second)
        repository.prepare(
            second,
            listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")),
        )
        repository.commit(second.jobId, ImportConflictPolicy.KEEP_EXISTING)

        assertTrue(repository.renameSource(second.sourceId, "专业词库"))
        assertEquals(
            "专业词库 (2)",
            database.sourceDao().getById(second.sourceId)?.displayName,
        )
        assertEquals(
            "专业词库 (2)",
            database.importDao().getJob(second.jobId)?.sourceName,
        )
    }

    @Test
    fun duplicateCustomSourceNamesReceiveReadableSuffixes() = runTest {
        val first = job("job-name-1", "source-name-1")
        database.importDao().upsertJob(first)
        repository.prepare(
            first,
            listOf(ImportedVocabularyRow(2, "fixture", chinese = "夹具")),
        )
        repository.commit(first.jobId, ImportConflictPolicy.KEEP_EXISTING)

        val second = job("job-name-2", "source-name-2")
        database.importDao().upsertJob(second)
        repository.prepare(
            second,
            listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")),
        )
        repository.commit(second.jobId, ImportConflictPolicy.KEEP_EXISTING)

        assertEquals("我的词库", database.sourceDao().getById(first.sourceId)?.displayName)
        assertEquals("我的词库 (2)", database.sourceDao().getById(second.sourceId)?.displayName)
        assertEquals("我的词库 (2)", database.importDao().getJob(second.jobId)?.sourceName)
    }

    @Test
    fun renamingOneOfSeveralSourcesDoesNotMislabelSharedCustomWord() = runTest {
        val first = job("job-shared-1", "source-shared-1")
        database.importDao().upsertJob(first)
        repository.prepare(
            first,
            listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")),
        )
        repository.commit(first.jobId, ImportConflictPolicy.KEEP_EXISTING)

        val second = job("job-shared-2", "source-shared-2")
        database.importDao().upsertJob(second)
        repository.prepare(
            second,
            listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")),
        )
        repository.commit(second.jobId, ImportConflictPolicy.KEEP_EXISTING)

        val sharedId = ImportRepository.stableCustomWordId("actuator")
        assertTrue(repository.renameSource(first.sourceId, "制造词库"))
        assertEquals("自定义词条", database.wordDao().getById(sharedId)?.categoryLabel)
    }

    @Test
    fun deletingOneSourceRestoresRemainingSourceLabelForSharedCustomWord() = runTest {
        val first = job("job-delete-shared-1", "source-delete-shared-1")
        database.importDao().upsertJob(first)
        repository.prepare(first, listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")))
        repository.commit(first.jobId, ImportConflictPolicy.KEEP_EXISTING)

        val second = job("job-delete-shared-2", "source-delete-shared-2")
        database.importDao().upsertJob(second)
        repository.prepare(second, listOf(ImportedVocabularyRow(2, "actuator", chinese = "执行器")))
        repository.commit(second.jobId, ImportConflictPolicy.KEEP_EXISTING)
        repository.renameSource(first.sourceId, "制造词库")

        val sharedId = ImportRepository.stableCustomWordId("actuator")
        assertEquals("自定义词条", database.wordDao().getById(sharedId)?.categoryLabel)
        assertTrue(repository.deleteSource(first.sourceId))
        assertEquals(
            database.sourceDao().getById(second.sourceId)?.displayName,
            database.wordDao().getById(sharedId)?.categoryLabel,
        )
    }

    @Test
    fun prepareRejectsFileWithoutAnyValidEnglishWord() = runTest {
        val job = job("job-3", "source-3")
        database.importDao().upsertJob(job)

        val failure = runCatching {
            repository.prepare(
                job,
                listOf(
                    ImportedVocabularyRow(2, "纯中文"),
                    ImportedVocabularyRow(3, "---"),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is VocabularyImportException)
        assertEquals(0, database.importDao().drafts(job.jobId).size)
    }

    @Test
    fun prepareUsesFirstSentenceAsPrimaryForImportedPhrase() = runTest {
        val job = job("job-4", "source-4")
        database.importDao().upsertJob(job)

        repository.prepare(
            job,
            listOf(
                ImportedVocabularyRow(
                    rowIndex = 2,
                    english = "Please check the fixture. Then report any issue.",
                    chinese = "请检查夹具，然后报告问题。",
                ),
            ),
        )

        val draft = database.importDao().drafts(job.jobId).single()
        assertEquals("Please check the fixture.", draft.primaryEnglish)
    }

    private suspend fun seedBuiltInFixture() {
        val word = WordEntity(
            id = "fixture",
            category = "mechanical",
            categoryLabel = "机械专业词汇",
            sourceIndex = 1,
            kind = "TERM",
            section = "",
            english = "fixture",
            primaryEnglish = "fixture",
            phonetic = "/ˈfɪkstʃə/",
            chinese = "现有释义",
            note = "",
            exampleEn = "",
            exampleZh = "",
            audioText = "fixture",
            audioAsset = "audio/fixture.ogg",
        )
        database.wordDao().upsert(word)
        database.sourceDao().upsert(
            SourceEntity("mechanical", "机械专业词汇", SourceType.BUILTIN.name),
        )
        database.sourceDao().upsertLinks(
            listOf(WordSourceCrossRef("mechanical", word.id, 1)),
        )
        database.progressDao().upsert(ProgressEntity(word.id, intervalDays = 6))
    }

    private fun job(jobId: String, sourceId: String) = ImportJobEntity(
        jobId = jobId,
        sourceId = sourceId,
        sourceName = "我的词库",
        originalFileName = "words.csv",
        internalFilePath = "unused",
        status = "PREPARING",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
