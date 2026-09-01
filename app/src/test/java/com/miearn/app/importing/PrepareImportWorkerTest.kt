package com.miearn.app.importing

import androidx.test.core.app.ApplicationProvider
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.miearn.app.MIearnApplication
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
class PrepareImportWorkerTest {
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
    fun parsesEnrichesAndStagesCsvWithoutNetwork() = runTest {
        withContext(Dispatchers.IO) {
        val file = File(application.filesDir, "worker-import.csv")
        file.writeText("英文,中文\nfixture,\nactuator,执行器\n", Charsets.UTF_8)
        val job = ImportJobEntity(
            jobId = "worker-job",
            sourceId = "worker-source",
            sourceName = "Worker 词库",
            originalFileName = "worker-import.csv",
            internalFilePath = file.absolutePath,
            status = ImportJobStatus.COPYING.name,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        application.container.database.importDao().upsertJob(job)
        val worker = TestListenableWorkerBuilder<PrepareImportWorker>(application)
            .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to job.jobId))
            .build()

        worker.doWork()

        val prepared = application.container.database.importDao().getJob(job.jobId)!!
        assertEquals(ImportJobStatus.AWAITING_CONFIRMATION.name, prepared.status)
        assertEquals(2, prepared.validRows)
        assertEquals(
            listOf("fixture", "actuator"),
            application.container.database.importDao().drafts(job.jobId).map { it.english },
        )
        }
    }

    @Test
    fun failedPreparationDeletesCopiedFileAndDraftRows() = runTest {
        withContext(Dispatchers.IO) {
            val file = File(application.cacheDir, "failed-worker-import.csv")
            file.writeText("英文,中文\n123,无效\n", Charsets.UTF_8)
            val job = importJob(
                jobId = "failed-worker-job",
                file = file,
                status = ImportJobStatus.COPYING,
            )
            application.container.database.importDao().upsertJob(job)
            application.container.database.importDao().upsertDrafts(
                listOf(importDraft(job.jobId)),
            )
            val worker = TestListenableWorkerBuilder<PrepareImportWorker>(application)
                .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to job.jobId))
                .build()

            val result = worker.doWork()

            assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
            assertEquals(
                ImportJobStatus.FAILED.name,
                application.container.database.importDao().getJob(job.jobId)?.status,
            )
            assertFalse(file.exists())
            assertTrue(application.container.database.importDao().drafts(job.jobId).isEmpty())
        }
    }

    @Test
    fun cancellationDeletesCopiedFileAndDraftRows() = runTest {
        withContext(Dispatchers.IO) {
            val file = File(application.cacheDir, "cancelled-worker-import.csv")
            file.writeText("英文,中文\nfixture,夹具\n", Charsets.UTF_8)
            val job = importJob(
                jobId = "cancelled-worker-job",
                file = file,
                status = ImportJobStatus.PREPARING,
            )
            application.container.database.importDao().upsertJob(job)
            application.container.database.importDao().upsertDrafts(
                listOf(importDraft(job.jobId)),
            )
            val coordinator = ImportWorkCoordinator(
                application,
                application.container.database,
            )

            coordinator.cancel(job.jobId)

            assertEquals(
                ImportJobStatus.CANCELLED.name,
                application.container.database.importDao().getJob(job.jobId)?.status,
            )
            assertFalse(file.exists())
            assertTrue(application.container.database.importDao().drafts(job.jobId).isEmpty())
        }
    }

    @Test
    fun pastedTextUsesTheSameObservableImportJobPipeline() = runTest {
        withContext(Dispatchers.IO) {
            val coordinator = ImportWorkCoordinator(
                application,
                application.container.database,
            )
            var callbackJobId: String? = null

            val jobId = coordinator.createAndPrepareText(
                text = "英文,中文\nfixture,夹具\n",
                sourceName = "粘贴词库",
            ) { callbackJobId = it }

            assertEquals(jobId, callbackJobId)
            val job = application.container.database.importDao().getJob(jobId)!!
            assertEquals("pasted.txt", job.originalFileName)
            assertTrue(
                job.status in setOf(
                    ImportJobStatus.COPYING.name,
                    ImportJobStatus.PREPARING.name,
                    ImportJobStatus.AWAITING_MAPPING.name,
                    ImportJobStatus.AWAITING_CONFIRMATION.name,
                ),
            )
            assertTrue(File(job.internalFilePath).isFile)
            assertEquals(
                "英文,中文\nfixture,夹具\n",
                File(job.internalFilePath).readText(Charsets.UTF_8),
            )

            coordinator.cancel(jobId)
        }
    }

    @Test
    fun workerDoesNotResurrectCancelledJob() = runTest {
        withContext(Dispatchers.IO) {
            val file = File(application.cacheDir, "cancelled-before-worker.csv")
            file.writeText("not a valid import", Charsets.UTF_8)
            val job = importJob(
                jobId = "cancelled-before-worker",
                file = file,
                status = ImportJobStatus.CANCELLED,
            )
            application.container.database.importDao().upsertJob(job)

            val result = TestListenableWorkerBuilder<PrepareImportWorker>(application)
                .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to job.jobId))
                .build()
                .doWork()

            assertTrue(result is androidx.work.ListenableWorker.Result.Success)
            assertEquals(
                ImportJobStatus.CANCELLED.name,
                application.container.database.importDao().getJob(job.jobId)?.status,
            )
        }
    }

    private fun importJob(
        jobId: String,
        file: File,
        status: ImportJobStatus,
    ) = ImportJobEntity(
        jobId = jobId,
        sourceId = "source-$jobId",
        sourceName = "测试词库",
        originalFileName = file.name,
        internalFilePath = file.absolutePath,
        status = status.name,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun importDraft(jobId: String) = ImportDraftEntity(
        jobId = jobId,
        rowIndex = 1,
        normalizedEnglish = "fixture",
        english = "fixture",
        primaryEnglish = "fixture",
        phonetic = "/fixture/",
        chinese = "夹具",
        note = "",
        exampleEn = "",
        exampleZh = "",
    )
}
