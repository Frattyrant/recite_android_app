package com.miearn.app.importing

import java.io.InputStream

class CsvVocabularyReader(private val maxRows: Int = 20_000) : VocabularyRowReader {
    override fun rows(input: InputStream): Sequence<RawVocabularyRow> =
        DelimitedTextVocabularyReader(VocabularyFileType.CSV, maxRows).rows(input)
}
