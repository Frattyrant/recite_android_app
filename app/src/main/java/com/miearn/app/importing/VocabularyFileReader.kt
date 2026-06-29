package com.miearn.app.importing

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

class VocabularyFileReader(
    private val csv: CsvVocabularyReader = CsvVocabularyReader(),
    private val xlsx: XlsxVocabularyReader = XlsxVocabularyReader(),
) {
    fun rows(fileName: String, input: InputStream): Sequence<RawVocabularyRow> {
        val bytes = input.readBytes()
        val lowerName = fileName.lowercase(Locale.ROOT)
        val isZip = bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()
        val reader = when {
            lowerName.endsWith(".xlsx") || isZip -> xlsx
            lowerName.endsWith(".csv") -> csv
            else -> throw UnsupportedVocabularyFileException()
        }
        return reader.rows(ByteArrayInputStream(bytes))
    }
}
