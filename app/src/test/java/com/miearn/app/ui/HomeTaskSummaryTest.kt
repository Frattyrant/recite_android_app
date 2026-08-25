package com.miearn.app.ui

import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import com.miearn.app.importing.ImportFailureCode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTaskSummaryTest {
    @Test
    fun dailyStudyActionReflectsWhetherThereIsWork() {
        assertEquals(false, hasDailyStudyTask(0, 0))
        assertEquals("今日已完成", dailyStudyActionLabel(0, 0))
        assertEquals(true, hasDailyStudyTask(1, 0))
        assertEquals("开始学习", dailyStudyActionLabel(0, 3))
    }

    @Test
    fun estimatesACompactDailyStudyDuration() {
        assertEquals(0, estimateStudyMinutes(0))
        assertEquals(1, estimateStudyMinutes(1))
        assertEquals(7, estimateStudyMinutes(20))
        assertEquals(9, estimateStudyMinutes(26))
    }

    @Test
    fun failedImportRemainsActionableOnHome() {
        val job = ImportJobEntity(
            jobId = "job",
            sourceId = "custom-source",
            sourceName = "我的词库",
            originalFileName = "words.txt",
            internalFilePath = "",
            status = ImportJobStatus.FAILED.name,
            errorCode = "UNKNOWN_FORMAT",
            errorMessage = "无法识别文件格式",
            recoveryHint = "请另存为 UTF-8 文本",
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        assertEquals("导入失败：无法识别文件格式", importHomeStatusText(job))
        assertEquals(true, shouldShowHomeImportJob(job))
        assertEquals(false, canRetryImport(job))

        assertEquals(
            true,
            canRetryImport(job.copy(errorCode = ImportFailureCode.SECURITY_ACCESS.name)),
        )
    }

    @Test
    fun completedAndCancelledImportAreNotShownOnHome() {
        listOf(ImportJobStatus.COMPLETED, ImportJobStatus.CANCELLED).forEach { status ->
            val job = ImportJobEntity(
                jobId = status.name,
                sourceId = "custom-source",
                sourceName = "我的词库",
                originalFileName = "words.txt",
                internalFilePath = "",
                status = status.name,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
            )

            assertEquals(false, shouldShowHomeImportJob(job))
        }
    }
}
