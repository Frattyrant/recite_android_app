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

open class VocabularyImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ImportLimitException(val limit: Int) : VocabularyImportException("词库最多支持 $limit 行")
class EmptyVocabularyFileException : VocabularyImportException("文件中没有可导入的数据")
class UnsupportedVocabularyFileException : VocabularyImportException("请选择 .csv 或 .xlsx 文件")
class CorruptVocabularyFileException(cause: Throwable? = null) : VocabularyImportException("文件损坏或格式无法识别", cause)