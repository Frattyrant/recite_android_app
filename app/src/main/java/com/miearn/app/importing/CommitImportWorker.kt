package com.miearn.app.importing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miearn.app.MIearnApplication
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportJobStatus
import java.io.File
import kotlinx.coroutines.CancellationException

class CommitImportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(PrepareImportWorker.KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as MIearnApplication).container
        val dao = container.database.importDao()
        val job = dao.getJob(jobId) ?: return Result.failure()
        if (job.status == ImportJobStatus.CANCELLED.name) return Result.success()
        return try {
            val policyName = job.conflictPolicy
                ?: throw VocabularyImportException(
                    message = "未选择冲突处理方式",
                    code = ImportFailureCode.COMMIT_FAILED,
                    recoveryHint = "请返回导入页面选择冲突处理方式后重试。",
                    retryable = false,
                )
            val policy = ImportConflictPolicy.valueOf(policyName)
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.COMMITTING.name,
                    errorMessage = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            container.importRepository.commit(jobId, policy)
            File(job.internalFilePath).delete()
            Result.success()
        } catch (error: CancellationException) {
            // The coordinator marks cancelled jobs and removes their drafts;
            // propagating cancellation prevents a competing FAILED update.
            throw error
        } catch (error: Exception) {
            runCatching { dao.deleteDrafts(jobId) }
            val failure = error.toImportFailure(
                fallbackCode = ImportFailureCode.COMMIT_FAILED,
                fallbackMessage = "保存词库失败",
                fallbackHint = "请稍后重试；如果仍失败，请重新选择文件。",
            )
            if (!failure.retryable) runCatching { File(job.internalFilePath).delete() }
            dao.markCommitFailed(
                jobId = jobId,
                errorCode = failure.code.name,
                errorMessage = failure.message,
                recoveryHint = failure.recoveryHint,
                now = System.currentTimeMillis(),
            )
            Result.failure()
        }
    }
}
