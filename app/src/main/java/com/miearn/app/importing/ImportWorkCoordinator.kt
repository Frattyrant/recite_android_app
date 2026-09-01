package com.miearn.app.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ImportWorkCoordinator(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val workManager = WorkManager.getInstance(context)
    // WorkManager may resume after the process is killed or much later when the
    // device is under storage pressure. Cache files can be evicted in that gap;
    // noBackupFilesDir remains private and durable without entering device backup.
    private val fileStore = ImportFileStore(File(context.noBackupFilesDir, "imports"))

    suspend fun createAndPrepare(
        uri: Uri,
        sourceName: String,
        onJobCreated: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        // Some SAF providers throw while querying metadata. The job must still be
        // created so the UI can surface the actual open/copy failure instead of
        // silently losing the import request.
        val fileName = runCatching { queryFileName(uri) }
            .getOrDefault(uri.lastPathSegment?.substringAfterLast('/') ?: "vocabulary.txt")
        createAndPrepareInput(
            fileName = fileName,
            sourceName = sourceName,
            inputProvider = { context.contentResolver.openInputStream(uri) },
            onJobCreated = onJobCreated,
        )
    }

    suspend fun createAndPrepareText(
        text: String,
        sourceName: String,
        onJobCreated: (String) -> Unit = {},
    ): String {
        if (text.isBlank()) throw EmptyVocabularyFileException()
        return createAndPrepareInput(
            fileName = "pasted.txt",
            sourceName = sourceName,
            inputProvider = { text.byteInputStream() },
            onJobCreated = onJobCreated,
        )
    }

    private suspend fun createAndPrepareInput(
        fileName: String,
        sourceName: String,
        inputProvider: () -> InputStream?,
        onJobCreated: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val copyContext = currentCoroutineContext()
        val jobId = UUID.randomUUID().toString()
        val sourceId = "custom-${UUID.randomUUID()}"
        val target = fileStore.target(jobId)
        val now = System.currentTimeMillis()
        val job = ImportJobEntity(
            jobId = jobId,
            sourceId = sourceId,
            sourceName = sourceName.trim().ifBlank { fileName.substringBeforeLast('.') },
            originalFileName = fileName,
            internalFilePath = target.absolutePath,
            status = ImportJobStatus.COPYING.name,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        // Persist before touching SAF. This makes a slow/cancelled copy observable immediately.
        database.importDao().upsertJob(job)
        onJobCreated(jobId)
        try {
            val copied = inputProvider()?.use { input ->
                fileStore.copy(jobId, input) { copyContext.ensureActive() }
            } ?: throw VocabularyImportException(
                message = "无法读取所选文件",
                code = ImportFailureCode.COPY_FAILED,
                recoveryHint = "文件授权可能已失效，请重新选择文件。",
                retryable = true,
            )
            val request = OneTimeWorkRequestBuilder<PrepareImportWorker>()
                .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to jobId))
                .build()
            // Cancellation can arrive while the copied file is being
            // published. The conditional update prevents a concurrent cancel
            // from being overwritten back to COPYING.
            copyContext.ensureActive()
            val published = database.importDao().publishCopiedFile(
                jobId = jobId,
                path = copied.absolutePath,
                now = System.currentTimeMillis(),
            )
            if (published == 0) {
                cleanupSourceFiles(job)
                return@withContext jobId
            }
            val workName = "prepare-import-$jobId"
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            // Close the remaining tiny race where cancellation happens after
            // the status check but before enqueueing the WorkManager request.
            if (database.importDao().getJob(jobId)?.status == ImportJobStatus.CANCELLED.name) {
                workManager.cancelUniqueWork(workName)
                cleanupSourceFiles(job)
            }
            jobId
        } catch (error: CancellationException) {
            cleanupSourceFiles(job)
            throw error
        } catch (error: Exception) {
            val failure = error.toImportFailure(
                fallbackCode = ImportFailureCode.COPY_FAILED,
                fallbackMessage = "无法读取所选文件",
                fallbackHint = "请重新选择文件并重试。",
            )
            if (!failure.retryable) target.delete()
            database.importDao().markCopyFailed(
                jobId = jobId,
                errorCode = failure.code.name,
                errorMessage = failure.message,
                recoveryHint = failure.recoveryHint,
                now = System.currentTimeMillis(),
            )
            jobId
        }
    }

    fun observeJob(jobId: String): Flow<ImportJobEntity?> = database.importDao().observeJob(jobId)

    fun observeLatestActiveJob(): Flow<ImportJobEntity?> = database.importDao().observeLatestActiveJob()

    /**
     * Converts copy jobs left by a dead process into an actionable failure.
     * Import copying is intentionally performed while the SAF URI is still
     * available, so there is no valid stream to resume after process death.
     */
    suspend fun recoverInterruptedCopies() = withContext(Dispatchers.IO) {
        val dao = database.importDao()
        val interrupted = dao.jobsWithStatus(ImportJobStatus.COPYING.name)
        interrupted.forEach { job ->
            cleanupSourceFiles(job)
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.FAILED.name,
                    errorCode = ImportFailureCode.COPY_FAILED.name,
                    errorMessage = "导入在复制文件时被中断",
                    recoveryHint = "文件没有完整保存，请重新选择原文件后重试。",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun retry(jobId: String): Boolean = withContext(Dispatchers.IO) {
        val dao = database.importDao()
        val job = dao.getJob(jobId) ?: return@withContext false
        if (!File(job.internalFilePath).isFile) return@withContext false
        dao.upsertJob(
            job.copy(
                status = ImportJobStatus.PREPARING.name,
                errorCode = null,
                errorMessage = null,
                recoveryHint = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        val request = OneTimeWorkRequestBuilder<PrepareImportWorker>()
            .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to jobId))
            .build()
        workManager.enqueueUniqueWork(
            "prepare-import-$jobId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        true
    }

    suspend fun commit(jobId: String, policy: ImportConflictPolicy) {
        database.importDao().setConflictPolicy(
            jobId = jobId,
            policy = policy.name,
            status = ImportJobStatus.COMMITTING.name,
            now = System.currentTimeMillis(),
        )
        val request = OneTimeWorkRequestBuilder<CommitImportWorker>()
            .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to jobId))
            .build()
        workManager.enqueueUniqueWork("commit-import-$jobId", ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun resumeWithMapping(jobId: String, mapping: ImportColumnMapping) {
        val job = requireNotNull(database.importDao().getJob(jobId))
        database.importDao().upsertJob(
            job.copy(
                mappingJson = ImportMappingCodec.encode(mapping),
                status = ImportJobStatus.PREPARING.name,
                errorCode = null,
                errorMessage = null,
                recoveryHint = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        val request = OneTimeWorkRequestBuilder<PrepareImportWorker>()
            .setInputData(workDataOf(PrepareImportWorker.KEY_JOB_ID to jobId))
            .build()
        workManager.enqueueUniqueWork(
            "prepare-import-$jobId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun cancel(jobId: String) = withContext(Dispatchers.IO) {
        workManager.cancelUniqueWork("prepare-import-$jobId")
        workManager.cancelUniqueWork("commit-import-$jobId")
        val dao = database.importDao()
        val job = dao.getJob(jobId) ?: return@withContext
        dao.deleteDrafts(jobId)
        cleanupSourceFiles(job)
        dao.upsertJob(
            job.copy(
                status = ImportJobStatus.CANCELLED.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun cleanupSourceFiles(job: ImportJobEntity) {
        // ImportFileStore writes to a sibling .partial file and only renames
        // it after a complete copy. A killed process can leave that file.
        fileStore.cleanup(job.jobId)
        // Keep cleanup safe if a job was created by an older build with a
        // different private directory layout.
        File(job.internalFilePath).delete()
    }

    private fun queryFileName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0).orEmpty().ifBlank { "vocabulary.csv" }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "vocabulary.csv"
    }

}
