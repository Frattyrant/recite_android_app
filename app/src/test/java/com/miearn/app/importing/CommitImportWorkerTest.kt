package com.miearn.app.importing

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.miearn.app.MIearnApplication
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportDraftEntity
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MIearnApplication::class)
class CommitImportWorkerTest {
    private val application =
        ApplicationProvider.getApplicationContext<MIearnApplication>()

    @Before
    fun clearDatabase() = runTest {
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
        withContext(Dispatchers.IO) {
            application.container.database.clearAllTables()
        }
    }

    @Test
    fun successfulCommitDeletesOriginalCopyAndDrafts() = runTest {
        withContext(Dispatchers.IO) {
            val file = File(application.cacheDir, "commit-worker-import.csv")
            file.writeText("temporary source")
            val job = job(
                id = "commit-worker-job",
                file = file,
                policy = ImportConflictPolicy.KEEP_EXISTING.name,
            )
            application.container.database.importDao().upsertJob(job)
            application.container.database.importDao().upsertDrafts(listOf(draft(job.jobId)))
            val worker = worker(job.jobId)

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(
                ImportJobStatus.COMPLETED.name,
                application.container.database.importDao().getJob(job.jobId)?.status,
            )
            assertFalse(file.exists())
            assertTrue(application.container.database.importDao().drafts(job.jobId).isEmpty())
        }
    }

    @Test
    fun invalidCommitPolicyFailsClosedAndDeletesOriginalCopy() = runTest {
        withContext(Dispatchers.IO) {
            val file = File(application.cacheDir, "invalid-policy-import.csv")
            file.writeText("temporary source")
            val job = job(id = "invalid-policy-job", file = file, policy = null)
            application.container.database.importDao().upsertJob(job)
            application.container.database.importDao().upsertDrafts(listOf(draft(job.jobId)))
            val worker = worker(job.jobId)

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
            assertEquals(
                ImportJobStatus.FAILED.name,
                application.container.database.importDao().getJob(job.jobId)?.status,
            )
            assertFalse(file.exists())
            assertTrue(application.container.database.importDao().drafts(job.jobId).isEmpty())
        }
    }

    private fun worker(jobId: String) =
        TestListenableWorkerBuilder<CommitImportWorker>(application)
            .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to jobId))
            .build()

    private fun job(id: String, file: File, policy: String?) = ImportJobEntity(
        jobId = id,
        sourceId = "source-$id",
        sourceName = "测试词库",
        originalFileName = file.name,
        internalFilePath = file.absolutePath,
        status = ImportJobStatus.COMMITTING.name,
        conflictPolicy = policy,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun draft(jobId: String) = ImportDraftEntity(
        jobId = jobId,
        rowIndex = 1,
        normalizedEnglish = "fixture",
        english = "fixture",
        primaryEnglish = "fixture",
        phonetic = "/ˈfɪkstʃər/",
        chinese = "夹具",
        note = "",
        exampleEn = "The technician checked the fixture.",
        exampleZh = "技术员检查了夹具。",
    )
}
