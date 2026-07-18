package com.miearn.app.importing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miearn.app.MIearnApplication
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportJobStatus
import java.io.File

class CommitImportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(PrepareImportWorker.KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as MIearnApplication).container
        val dao = container.database.importDao()
        val job = dao.getJob(jobId) ?: return Result.failure()
        return try {
            val policyName = job.conflictPolicy
                ?: throw VocabularyImportException("未选择冲突处理方式")
            val policy = ImportConflictPolicy.valueOf(policyName)
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.COMMITTING.name,
                    errorMessage = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            container.importRepository.commit(jobId, policy)
            Result.success()
        } catch (error: Exception) {
            runCatching { dao.deleteDrafts(jobId) }
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.FAILED.name,
                    errorMessage = error.message ?: "保存词库失败",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            Result.failure()
        } finally {
            File(job.internalFilePath).delete()
        }
    }
}
