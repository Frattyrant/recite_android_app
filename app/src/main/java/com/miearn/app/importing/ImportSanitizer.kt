package com.miearn.app.importing

import java.text.Normalizer
import java.util.Locale

object ImportSanitizer {
    private val whitespace = Regex("\\s+")
    private val validEnglish = Regex("^[A-Za-z0-9\\s'’\\-_/&(),.:;；\\\\]+$")

    fun clean(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim()
        .replace(whitespace, " ")

    fun normalizeEnglish(value: String): String = clean(value).lowercase(Locale.ROOT)

    fun isValidEnglish(value: String): Boolean {
        val cleaned = clean(value)
        return cleaned.any { it in 'A'..'Z' || it in 'a'..'z' } && validEnglish.matches(cleaned)
    }

    fun detectHeader(value: String): ColumnRole {
        val header = normalizeEnglish(value).replace("_", "").replace(" ", "")
        return when (header) {
            "word", "words", "english", "英文", "单词", "词汇", "英文单词" -> ColumnRole.ENGLISH
            "chinese", "translation", "meaning", "中文", "翻译", "释义", "中文释义" -> ColumnRole.CHINESE
            "phonetic", "ipa", "音标" -> ColumnRole.PHONETIC
            "example", "exampleen", "sentence", "例句", "英文例句" -> ColumnRole.EXAMPLE_EN
            "examplezh", "exampletranslation", "例句翻译", "中文例句" -> ColumnRole.EXAMPLE_ZH
            "note", "notes", "remark", "备注", "笔记" -> ColumnRole.NOTE
            else -> ColumnRole.IGNORE
        }
    }

    fun autoMappingOrNull(headers: List<String>): ImportColumnMapping? {
        val detected = headers.mapIndexedNotNull { index, header ->
            detectHeader(header).takeUnless { it == ColumnRole.IGNORE }?.let { index to it }
        }.toMap()
        return detected
            .takeIf { mapping -> mapping.values.count { it == ColumnRole.ENGLISH } == 1 }
            ?.let(::ImportColumnMapping)
    }

    fun headerlessMapping(columnCount: Int): ImportColumnMapping {
        require(columnCount > 0)
        return ImportColumnMapping(mapOf(0 to ColumnRole.ENGLISH))
    }


    fun map(row: RawVocabularyRow, mapping: ImportColumnMapping): ImportedVocabularyRow {
        fun value(role: ColumnRole): String = mapping.byIndex.entries
            .firstOrNull { it.value == role }
            ?.key?.let { row.cells.getOrNull(it) }.orEmpty().let(::clean)
        return ImportedVocabularyRow(
            rowIndex = row.rowIndex,
            english = value(ColumnRole.ENGLISH),
            chinese = value(ColumnRole.CHINESE),
            phonetic = value(ColumnRole.PHONETIC),
            exampleEn = value(ColumnRole.EXAMPLE_EN),
            exampleZh = value(ColumnRole.EXAMPLE_ZH),
            note = value(ColumnRole.NOTE),
        )
    }
}
