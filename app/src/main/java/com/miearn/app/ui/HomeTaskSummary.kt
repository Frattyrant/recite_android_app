package com.miearn.app.ui

import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import com.miearn.app.domain.LearningPhase
import com.miearn.app.importing.ImportFailureCode

internal fun estimateStudyMinutes(wordCount: Int): Int =
    if (wordCount <= 0) 0 else maxOf(1, (wordCount + 2) / 3)

internal fun hasDailyStudyTask(todayNew: Int, todayReview: Int): Boolean =
    todayNew > 0 || todayReview > 0

internal fun dailyStudyActionLabel(todayNew: Int, todayReview: Int): String =
    if (hasDailyStudyTask(todayNew, todayReview)) "开始学习" else "今日已完成"

internal fun remainingDailyNewCount(
    dailyGoal: Int,
    learnedToday: Int,
    unseen: Int,
): Int = minOf(
    unseen.coerceAtLeast(0),
    (dailyGoal - learnedToday).coerceAtLeast(0),
)

internal fun shouldRefreshStudyDay(previous: Long, current: Long): Boolean =
    previous != current

internal data class DailyTaskCounts(
    val newCount: Int,
    val reviewCount: Int,
)

internal data class DailyTaskSnapshot(
    val epochDay: Long,
    val counts: DailyTaskCounts,
)

internal fun remainingSessionTaskCounts(
    phase: LearningPhase,
    index: Int,
    phaseTotal: Int,
): DailyTaskCounts {
    val remaining = (phaseTotal - index).coerceAtLeast(0)
    return when (phase) {
        LearningPhase.BROWSE, LearningPhase.CONSOLIDATE ->
            DailyTaskCounts(newCount = remaining, reviewCount = 0)
        LearningPhase.REVIEW, LearningPhase.REINFORCEMENT ->
            DailyTaskCounts(newCount = 0, reviewCount = remaining)
        LearningPhase.COMPLETE -> DailyTaskCounts(newCount = 0, reviewCount = 0)
    }
}

internal fun shouldShowHomeImportJob(job: ImportJobEntity): Boolean =
    job.status !in setOf(
        ImportJobStatus.COMPLETED.name,
        ImportJobStatus.CANCELLED.name,
    )

internal fun importHomeStatusText(job: ImportJobEntity): String = when (job.status) {
    ImportJobStatus.FAILED.name ->
        job.errorMessage?.takeIf(String::isNotBlank)?.let { "导入失败：$it" }
            ?: "导入失败，点击查看详情"
    ImportJobStatus.AWAITING_MAPPING.name -> "需要确认文件列"
    ImportJobStatus.AWAITING_CONFIRMATION.name -> "校验完成，等待确认"
    ImportJobStatus.COMMITTING.name -> "正在保存词库…"
    else -> if (job.totalRows > 0) {
        "正在校验第 ${job.processedRows}/${job.totalRows} 个词…"
    } else {
        "正在读取 ${job.originalFileName}…"
    }
}

internal fun canRetryImport(job: ImportJobEntity): Boolean =
    job.status == ImportJobStatus.FAILED.name && when (job.errorCode) {
        ImportFailureCode.SECURITY_ACCESS.name,
        ImportFailureCode.COPY_FAILED.name,
        ImportFailureCode.COMMIT_FAILED.name,
        -> true
        else -> false
    }
