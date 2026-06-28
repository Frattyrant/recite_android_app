package com.miearn.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceType { BUILTIN, CUSTOM }

enum class ImportJobStatus {
    COPYING, PREPARING, AWAITING_MAPPING, AWAITING_CONFIRMATION,
    COMMITTING, COMPLETED, FAILED, CANCELLED,
}

enum class ImportConflictPolicy { KEEP_EXISTING, UPDATE_NON_EMPTY }

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val sourceId: String,
    val displayName: String,
    val type: String,
    val originalFileName: String? = null,
    val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
    val wordCount: Int = 0,
)

@Entity(
    tableName = "word_source",
    primaryKeys = ["sourceId", "wordId"],
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["sourceId"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wordId")],
)
data class WordSourceCrossRef(
    val sourceId: String,
    val wordId: String,
    val importOrder: Int,
)

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey val jobId: String,
    val sourceId: String,
    val sourceName: String,
    val originalFileName: String,
    val internalFilePath: String,
    val status: String,
    val processedRows: Int = 0,
    val totalRows: Int = 0,
    val validRows: Int = 0,
    val invalidRows: Int = 0,
    val duplicateRows: Int = 0,
    val mappingJson: String = "{}",
    val headersJson: String = "[]",
    val previewRowsJson: String = "[]",
    val conflictPolicy: String? = null,
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "import_drafts",
    primaryKeys = ["jobId", "rowIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ImportJobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId"), Index("normalizedEnglish")],
)
data class ImportDraftEntity(
    val jobId: String,
    val rowIndex: Int,
    val normalizedEnglish: String,
    val english: String,
    val primaryEnglish: String,
    val phonetic: String,
    val chinese: String,
    val note: String,
    val exampleEn: String,
    val exampleZh: String,
    val existingWordId: String? = null,
    val validationError: String? = null,
)