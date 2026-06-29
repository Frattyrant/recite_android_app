package com.miearn.app.importing

import androidx.test.core.app.ApplicationProvider
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.miearn.app.MIearnApplication
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
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
}
