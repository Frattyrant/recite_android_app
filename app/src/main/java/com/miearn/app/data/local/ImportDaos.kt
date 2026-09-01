package com.miearn.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY type, createdAtEpochMillis, displayName")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY type, createdAtEpochMillis, displayName")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE sourceId = :sourceId")
    suspend fun getById(sourceId: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE type = 'CUSTOM' AND displayName = :displayName LIMIT 1")
    suspend fun customByName(displayName: String): SourceEntity?

    @Query(
        """
        SELECT w.id FROM words w
        JOIN word_source x ON x.wordId = w.id
        WHERE x.sourceId = :sourceId
        ORDER BY x.importOrder
        """,
    )
    suspend fun wordIds(sourceId: String): List<String>

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Upsert
    suspend fun upsertAll(sources: List<SourceEntity>)

    @Upsert
    suspend fun upsertLinks(links: List<WordSourceCrossRef>)

    @Query("UPDATE sources SET displayName = :name, updatedAtEpochMillis = :now WHERE sourceId = :sourceId AND type = 'CUSTOM'")
    suspend fun rename(sourceId: String, name: String, now: Long): Int

    @Query("DELETE FROM sources WHERE sourceId = :sourceId AND type = 'CUSTOM'")
    suspend fun deleteCustom(sourceId: String): Int

    @Query("UPDATE sources SET wordCount = (SELECT COUNT(*) FROM word_source WHERE sourceId = :sourceId), updatedAtEpochMillis = :now WHERE sourceId = :sourceId")
    suspend fun refreshWordCount(sourceId: String, now: Long)

    @Query("SELECT COUNT(*) FROM word_source WHERE wordId = :wordId")
    suspend fun membershipCount(wordId: String): Int

    @Query(
        """
        SELECT s.* FROM sources s
        JOIN word_source x ON x.sourceId = s.sourceId
        WHERE x.wordId = :wordId
        ORDER BY CASE s.type WHEN 'BUILTIN' THEN 0 ELSE 1 END,
            s.createdAtEpochMillis, s.displayName
        """,
    )
    suspend fun sourcesForWord(wordId: String): List<SourceEntity>
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_jobs WHERE jobId = :jobId")
    suspend fun getJob(jobId: String): ImportJobEntity?

    @Query("SELECT * FROM import_jobs WHERE jobId = :jobId")
    fun observeJob(jobId: String): Flow<ImportJobEntity?>

    @Query("SELECT * FROM import_jobs ORDER BY createdAtEpochMillis DESC")
    fun observeJobs(): Flow<List<ImportJobEntity>>

    @Query(
        "SELECT * FROM import_jobs WHERE status IN ('COPYING', 'PREPARING', 'AWAITING_MAPPING', 'AWAITING_CONFIRMATION', 'COMMITTING') " +
            "ORDER BY updatedAtEpochMillis DESC LIMIT 1",
    )
    fun observeLatestActiveJob(): Flow<ImportJobEntity?>

    /**
     * Jobs in COPYING cannot be resumed after the app process is killed: the
     * SAF stream belongs to the old process and the source file is not yet
     * published. Keep this query separate so recovery never touches jobs that
     * WorkManager can safely resume (PREPARING/COMMITTING).
     */
    @Query("SELECT * FROM import_jobs WHERE status = :status")
    suspend fun jobsWithStatus(status: String): List<ImportJobEntity>

    @Upsert
    suspend fun upsertJob(job: ImportJobEntity)

    @Query(
        "UPDATE import_jobs SET internalFilePath = :path, updatedAtEpochMillis = :now " +
            "WHERE jobId = :jobId AND status = 'COPYING'",
    )
    suspend fun publishCopiedFile(jobId: String, path: String, now: Long): Int

    @Query(
        "UPDATE import_jobs SET status = 'FAILED', errorCode = :errorCode, " +
            "errorMessage = :errorMessage, recoveryHint = :recoveryHint, " +
            "updatedAtEpochMillis = :now " +
            "WHERE jobId = :jobId AND status IN ('COPYING', 'PREPARING')",
    )
    suspend fun markPreparationFailed(
        jobId: String,
        errorCode: String,
        errorMessage: String,
        recoveryHint: String,
        now: Long,
    ): Int

    @Query(
        "UPDATE import_jobs SET status = 'FAILED', errorCode = :errorCode, " +
            "errorMessage = :errorMessage, recoveryHint = :recoveryHint, " +
            "updatedAtEpochMillis = :now " +
            "WHERE jobId = :jobId AND status = 'COMMITTING'",
    )
    suspend fun markCommitFailed(
        jobId: String,
        errorCode: String,
        errorMessage: String,
        recoveryHint: String,
        now: Long,
    ): Int

    @Query(
        "UPDATE import_jobs SET status = 'FAILED', errorCode = :errorCode, " +
            "errorMessage = :errorMessage, recoveryHint = :recoveryHint, " +
            "updatedAtEpochMillis = :now " +
            "WHERE jobId = :jobId AND status = 'COPYING'",
    )
    suspend fun markCopyFailed(
        jobId: String,
        errorCode: String,
        errorMessage: String,
        recoveryHint: String,
        now: Long,
    ): Int

    @Query("UPDATE import_jobs SET status = :status, processedRows = :processedRows, totalRows = :totalRows, validRows = :validRows, invalidRows = :invalidRows, duplicateRows = :duplicateRows, errorMessage = :errorMessage, updatedAtEpochMillis = :now WHERE jobId = :jobId")
    suspend fun updateProgress(
        jobId: String,
        status: String,
        processedRows: Int,
        totalRows: Int,
        validRows: Int,
        invalidRows: Int,
        duplicateRows: Int,
        errorMessage: String?,
        now: Long,
    )

    @Query("UPDATE import_jobs SET conflictPolicy = :policy, status = :status, updatedAtEpochMillis = :now WHERE jobId = :jobId")
    suspend fun setConflictPolicy(jobId: String, policy: String, status: String, now: Long)

    @Query("UPDATE import_jobs SET sourceName = :name, updatedAtEpochMillis = :now WHERE sourceId = :sourceId")
    suspend fun renameSourceJobs(sourceId: String, name: String, now: Long): Int

    @Upsert
    suspend fun upsertDrafts(rows: List<ImportDraftEntity>)

    @Query("SELECT * FROM import_drafts WHERE jobId = :jobId ORDER BY rowIndex")
    suspend fun drafts(jobId: String): List<ImportDraftEntity>

    @Query("SELECT * FROM import_drafts WHERE jobId = :jobId ORDER BY rowIndex LIMIT :limit")
    suspend fun preview(jobId: String, limit: Int): List<ImportDraftEntity>

    @Query("DELETE FROM import_drafts WHERE jobId = :jobId")
    suspend fun deleteDrafts(jobId: String)

    @Query("DELETE FROM import_jobs WHERE jobId = :jobId")
    suspend fun deleteJob(jobId: String)

    @Transaction
    suspend fun replaceDrafts(jobId: String, rows: List<ImportDraftEntity>) {
        deleteDrafts(jobId)
        if (rows.isNotEmpty()) upsertDrafts(rows)
    }
}
