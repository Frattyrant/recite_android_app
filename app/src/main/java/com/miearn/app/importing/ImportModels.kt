package com.miearn.app.importing

import java.io.InputStream

enum class ColumnRole { ENGLISH, CHINESE, PHONETIC, EXAMPLE_EN, EXAMPLE_ZH, NOTE, IGNORE }

data class ImportColumnMapping(val byIndex: Map<Int, ColumnRole>) {
    init { require(byIndex.values.count { it == ColumnRole.ENGLISH } == 1) }
}

data class RawVocabularyRow(val rowIndex: Int, val cells: List<String>)

data class ImportedVocabularyRow(
    val rowIndex: Int,
    val english: String,
    val chinese: String = "",
    val phonetic: String = "",
    val exampleEn: String = "",
    val exampleZh: String = "",
    val note: String = "",
)

interface VocabularyRowReader { fun rows(input: InputStream): Sequence<RawVocabularyRow> }

open class VocabularyImportException(
    message: String,
    cause: Throwable? = null,
    open val code: ImportFailureCode = ImportFailureCode.PARSE_FAILED,
    open val recoveryHint: String = "请检查文件格式后重试，或重新选择文件。",
    // Import format/content failures are permanent by default. I/O paths that
    // can be retried must opt in explicitly at the call site.
    open val retryable: Boolean = false,
) : Exception(message, cause)

class ImportLimitException(val limit: Int) : VocabularyImportException(
    message = "词库最多支持 $limit 行",
    code = ImportFailureCode.FILE_TOO_LARGE,
    recoveryHint = "请拆分文件后分别导入（每个文件最多 $limit 行）。",
    retryable = false,
)

class FileSizeLimitException(val limitBytes: Long) : VocabularyImportException(
    message = "文件不能超过 ${limitBytes / (1024 * 1024)} MB",
    code = ImportFailureCode.FILE_TOO_LARGE,
    recoveryHint = "请压缩词库内容、拆分文件，或另存为更小的 CSV/TXT 文件后重试。",
    retryable = false,
)

class EmptyVocabularyFileException : VocabularyImportException(
    message = "文件中没有可导入的数据",
    code = ImportFailureCode.EMPTY_FILE,
    recoveryHint = "请确认文件包含至少一行英文词条。",
    retryable = false,
)
enum class ImportFailureCode {
    FILE_TOO_LARGE,
    EMPTY_FILE,
    UNSUPPORTED_LEGACY_XLS,
    UNSUPPORTED_ENCRYPTED_WORKBOOK,
    UNSUPPORTED_XLSM,
    UNSUPPORTED_ZIP,
    UNKNOWN_BINARY,
    UNKNOWN_FORMAT,
    UNSUPPORTED_TEXT_ENCODING,
    CORRUPT_FILE,
    COPY_FAILED,
    SECURITY_ACCESS,
    PARSE_FAILED,
    COMMIT_FAILED,
}

open class ActionableVocabularyImportException(
    message: String,
    override val code: ImportFailureCode,
    override val recoveryHint: String,
    override val retryable: Boolean = false,
    cause: Throwable? = null,
) : VocabularyImportException(message, cause)

class UnsupportedVocabularyFileException(
    message: String = "请选择 .xlsx、.csv、.tsv 或 .txt 文件",
    code: ImportFailureCode = ImportFailureCode.UNKNOWN_FORMAT,
    recoveryHint: String = "请重新选择受支持的词库文件。",
) : ActionableVocabularyImportException(message, code, recoveryHint)

class CorruptVocabularyFileException(cause: Throwable? = null) : VocabularyImportException(
    message = "文件损坏或格式无法识别",
    cause = cause,
    retryable = false,
)

data class ImportFailure(
    val code: ImportFailureCode,
    val message: String,
    val recoveryHint: String,
    val retryable: Boolean,
)

fun Throwable.toImportFailure(
    fallbackCode: ImportFailureCode,
    fallbackMessage: String,
    fallbackHint: String,
    fallbackRetryable: Boolean = true,
): ImportFailure = when (this) {
    is VocabularyImportException -> ImportFailure(code, message.orEmpty(), recoveryHint, retryable)
    is SecurityException -> ImportFailure(
        ImportFailureCode.SECURITY_ACCESS,
        "无法读取所选文件",
        "文件授权已失效，请重新选择文件并允许访问。",
        true,
    )
    else -> ImportFailure(
        fallbackCode,
        message ?: fallbackMessage,
        fallbackHint,
        fallbackRetryable,
    )
}
