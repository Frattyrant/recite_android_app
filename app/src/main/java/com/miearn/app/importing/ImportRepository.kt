package com.miearn.app.importing

import androidx.room.withTransaction
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportDraftEntity
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import com.miearn.app.data.local.SourceEntity
import com.miearn.app.data.local.SourceType
import com.miearn.app.data.local.WordEntity
import com.miearn.app.data.local.WordSourceCrossRef
import com.miearn.app.domain.EnglishVariantParser
import java.security.MessageDigest

data class ImportPreparationResult(
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val duplicateRows: Int,
)

class ImportRepository(
    private val database: AppDatabase,
    private val dictionary: DictionaryLookup,
) {
    suspend fun prepare(
        job: ImportJobEntity,
        rows: List<ImportedVocabularyRow>,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportPreparationResult {
        val seen = mutableSetOf<String>()
        var duplicates = 0
        val drafts = rows.mapIndexed { index, row ->
            val normalized = ImportSanitizer.normalizeEnglish(row.english)
            val duplicate = normalized.isNotEmpty() && !seen.add(normalized)
            if (duplicate) duplicates++
            val valid = !duplicate && ImportSanitizer.isValidEnglish(row.english)
            val existing = if (valid) database.wordDao().findCanonicalWord(normalized) else null
            val local = if (valid && existing == null) dictionary.lookup(normalized) else null
            onProgress(index + 1, rows.size)
            ImportDraftEntity(
                jobId = job.jobId,
                rowIndex = row.rowIndex,
                normalizedEnglish = normalized,
                english = ImportSanitizer.clean(row.english),
                primaryEnglish = EnglishVariantParser.parse(row.english, "TERM").firstOrNull()
                    ?: ImportSanitizer.clean(row.english),
                phonetic = row.phonetic.ifBlank { existing?.phonetic ?: local?.phonetic.orEmpty() },
                chinese = row.chinese.ifBlank { existing?.chinese ?: local?.translation.orEmpty() },
                note = row.note.ifBlank { existing?.note.orEmpty() },
                exampleEn = row.exampleEn.ifBlank { existing?.exampleEn.orEmpty() },
                exampleZh = row.exampleZh.ifBlank { existing?.exampleZh.orEmpty() },
                existingWordId = existing?.id,
                validationError = when {
                    duplicate -> "文件内重复"
                    !valid -> "英文格式无效"
                    else -> null
                },
            )
        }
        val validRows = drafts.count { it.validationError == null }
        val result = ImportPreparationResult(
            totalRows = rows.size,
            validRows = validRows,
            invalidRows = rows.size - validRows - duplicates,
            duplicateRows = duplicates,
        )
        if (result.validRows == 0) {
            throw VocabularyImportException("文件中没有有效英文词条")
        }
        database.withTransaction {
            database.importDao().replaceDrafts(job.jobId, drafts)
            database.importDao().upsertJob(
                job.copy(
                    status = ImportJobStatus.AWAITING_CONFIRMATION.name,
                    processedRows = rows.size,
                    totalRows = rows.size,
                    validRows = result.validRows,
                    invalidRows = result.invalidRows,
                    duplicateRows = result.duplicateRows,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
        return result
    }

    suspend fun commit(jobId: String, policy: ImportConflictPolicy): Int =
        database.withTransaction {
            val job = requireNotNull(database.importDao().getJob(jobId))
            val drafts = database.importDao().drafts(jobId).filter { it.validationError == null }
            val links = ArrayList<WordSourceCrossRef>(drafts.size)
            drafts.forEachIndexed { order, draft ->
                val existing = draft.existingWordId?.let { database.wordDao().getById(it) }
                val word = when {
                    existing == null -> draft.toNewWord(job, order)
                    policy == ImportConflictPolicy.UPDATE_NON_EMPTY -> existing.copy(
                        english = draft.english.ifBlank { existing.english },
                        primaryEnglish = draft.primaryEnglish.ifBlank { existing.primaryEnglish },
                        phonetic = draft.phonetic.ifBlank { existing.phonetic },
                        chinese = draft.chinese.ifBlank { existing.chinese },
                        note = draft.note.ifBlank { existing.note },
                        exampleEn = draft.exampleEn.ifBlank { existing.exampleEn },
                        exampleZh = draft.exampleZh.ifBlank { existing.exampleZh },
                        audioText = draft.english.ifBlank { existing.audioText },
                    )
                    else -> existing
                }
                database.wordDao().upsert(word)
                links += WordSourceCrossRef(job.sourceId, word.id, order)
            }
            val now = System.currentTimeMillis()
            database.sourceDao().upsert(
                SourceEntity(
                    sourceId = job.sourceId,
                    displayName = job.sourceName,
                    type = SourceType.CUSTOM.name,
                    originalFileName = job.originalFileName,
                    createdAtEpochMillis = job.createdAtEpochMillis,
                    updatedAtEpochMillis = now,
                    wordCount = links.size,
                ),
            )
            if (links.isNotEmpty()) database.sourceDao().upsertLinks(links)
            database.importDao().upsertJob(
                job.copy(
                    status = ImportJobStatus.COMPLETED.name,
                    conflictPolicy = policy.name,
                    updatedAtEpochMillis = now,
                ),
            )
            database.importDao().deleteDrafts(jobId)
            links.size
        }

    suspend fun renameSource(sourceId: String, name: String): Boolean =
        database.sourceDao().rename(sourceId, name.trim(), System.currentTimeMillis()) > 0

    suspend fun deleteSource(sourceId: String): Boolean = database.withTransaction {
        val wordIds = database.sourceDao().wordIds(sourceId)
        if (database.sourceDao().deleteCustom(sourceId) == 0) return@withTransaction false
        wordIds.forEach { wordId ->
            if (database.sourceDao().membershipCount(wordId) == 0) {
                database.wordDao().deleteCustomWord(wordId)
            }
        }
        true
    }

    private fun ImportDraftEntity.toNewWord(job: ImportJobEntity, order: Int): WordEntity =
        WordEntity(
            id = stableCustomWordId(normalizedEnglish),
            category = "custom",
            categoryLabel = job.sourceName,
            sourceIndex = order + 1,
            kind = EnglishVariantParser.inferKind(english),
            section = "",
            english = english,
            primaryEnglish = primaryEnglish,
            phonetic = phonetic,
            chinese = chinese,
            note = note,
            exampleEn = exampleEn,
            exampleZh = exampleZh,
            audioText = english,
            audioAsset = "",
            isCustom = true,
        )

    companion object {
        fun stableCustomWordId(normalizedEnglish: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalizedEnglish.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "custom_${digest.take(24)}"
        }
    }
}
