package com.miearn.app.importing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miearn.app.MIearnApplication
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportJobStatus

class CommitImportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(PrepareImportWorker.KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as MIearnApplication).container
        val dao = container.database.importDao()
        val job = dao.getJob(jobId) ?: return Result.failure()
        val policy = runCatching {
            ImportConflictPolicy.valueOf(checkNotNull(job.conflictPolicy))
        }.getOrElse { return Result.failure() }
        return try {
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.COMMITTING.name,
                    errorMessage = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            container.importRepository.commit(jobId, policy)
            java.io.File(job.internalFilePath).delete()
            Result.success()
        } catch (error: Exception) {
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.FAILED.name,
                    errorMessage = error.message ?: "保存词库失败",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            Result.failure()
        }
    }
}
