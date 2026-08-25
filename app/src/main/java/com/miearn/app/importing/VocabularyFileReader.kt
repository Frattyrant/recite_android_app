package com.miearn.app.importing

import java.io.InputStream

class VocabularyFileReader(
    private val csv: CsvVocabularyReader = CsvVocabularyReader(),
    private val xlsx: XlsxVocabularyReader = XlsxVocabularyReader(),
    private val detector: VocabularyFileDetector = VocabularyFileDetector,
) {
    data class VocabularyReadResult(
        val detected: DetectedVocabularyFile,
        val rows: Sequence<RawVocabularyRow>,
    )

    fun read(fileName: String, mimeType: String?, input: InputStream): VocabularyReadResult {
        val bytes = input.readBytes()
        val detected = detector.detect(fileName, mimeType, bytes)
        val reader = when (detected.type) {
            VocabularyFileType.XLSX -> xlsx
            VocabularyFileType.CSV -> csv
            VocabularyFileType.TSV,
            VocabularyFileType.TXT,
            -> DelimitedTextVocabularyReader(detected.type)
        }
        return VocabularyReadResult(detected, reader.rows(bytes.inputStream()))
    }

    fun rows(fileName: String, input: InputStream): Sequence<RawVocabularyRow> =
        read(fileName, null, input).rows
}
