package com.miearn.app.importing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.miearn.app.MIearnApplication
import com.miearn.app.data.local.ImportJobStatus
import java.io.File

class PrepareImportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as MIearnApplication).container
        val dao = container.database.importDao()
        val job = dao.getJob(jobId) ?: return Result.failure()
        return try {
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.PREPARING.name,
                    errorMessage = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            val raw = File(job.internalFilePath).inputStream().buffered().use { input ->
                container.vocabularyFileReader.rows(job.originalFileName, input).toList()
            }
            val suppliedMapping = job.mappingJson
                .takeUnless { it.isBlank() || it == "{}" }
                ?.let(ImportMappingCodec::decodeMapping)
            when (val plan = ImportFilePlanner.plan(raw, suppliedMapping)) {
                is ImportFilePlan.MappingRequired -> {
                    dao.upsertJob(
                        job.copy(
                            status = ImportJobStatus.AWAITING_MAPPING.name,
                            totalRows = raw.size - 1,
                            headersJson = ImportMappingCodec.encodeHeaders(plan.headers),
                            previewRowsJson = ImportMappingCodec.encodePreview(plan.preview),
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                    return Result.success()
                }

                is ImportFilePlan.Ready -> {
                    if (plan.dataRows.isEmpty()) throw EmptyVocabularyFileException()
                    val preparedJob = job.copy(
                        mappingJson = ImportMappingCodec.encode(plan.mapping),
                        headersJson = ImportMappingCodec.encodeHeaders(plan.headers),
                        previewRowsJson = ImportMappingCodec.encodePreview(plan.preview),
                    )
                    val mapped = plan.dataRows.map { ImportSanitizer.map(it, plan.mapping) }
                    container.importRepository.prepare(preparedJob, mapped) { processed, total ->
                if (processed == total || processed % 25 == 0) {
                    setProgress(
                        Data.Builder()
                            .putInt(KEY_PROCESSED, processed)
                            .putInt(KEY_TOTAL, total)
                            .build(),
                    )
                    dao.updateProgress(
                        jobId = jobId,
                        status = ImportJobStatus.PREPARING.name,
                        processedRows = processed,
                        totalRows = total,
                        validRows = 0,
                        invalidRows = 0,
                        duplicateRows = 0,
                        errorMessage = null,
                        now = System.currentTimeMillis(),
                    )
                }
            }
                }
            }
            Result.success()
        } catch (error: Exception) {
            dao.upsertJob(
                job.copy(
                    status = ImportJobStatus.FAILED.name,
                    errorMessage = error.message ?: "文件解析失败",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
    }
}
