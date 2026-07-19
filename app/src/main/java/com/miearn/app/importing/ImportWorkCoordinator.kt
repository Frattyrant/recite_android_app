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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ImportWorkCoordinator(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val workManager = WorkManager.getInstance(context)
    private val fileStore = ImportFileStore(File(context.cacheDir, "imports"))

    suspend fun createAndPrepare(uri: Uri, sourceName: String): String = withContext(Dispatchers.IO) {
        val fileName = queryFileName(uri)
        require(fileName.endsWith(".csv", true) || fileName.endsWith(".xlsx", true)) {
            "请选择 .csv 或 .xlsx 文件"
        }
        val jobId = UUID.randomUUID().toString()
        val sourceId = "custom-${UUID.randomUUID()}"
        val target = context.contentResolver.openInputStream(uri)?.use { input ->
            fileStore.copy(jobId, input)
        } ?: throw VocabularyImportException("无法读取所选文件")
        try {
            val now = System.currentTimeMillis()
            database.importDao().upsertJob(
                ImportJobEntity(
                    jobId = jobId,
                    sourceId = sourceId,
                    sourceName = sourceName.trim().ifBlank { fileName.substringBeforeLast('.') },
                    originalFileName = fileName,
                    internalFilePath = target.absolutePath,
                    status = ImportJobStatus.COPYING.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
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
            jobId
        } catch (error: Exception) {
            target.delete()
            database.importDao().deleteJob(jobId)
            throw error
        }
    }

    fun observeJob(jobId: String): Flow<ImportJobEntity?> = database.importDao().observeJob(jobId)

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
                errorMessage = null,
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
        File(job.internalFilePath).delete()
        dao.upsertJob(
            job.copy(
                status = ImportJobStatus.CANCELLED.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
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
