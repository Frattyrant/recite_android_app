package com.miearn.app.importing

import java.io.InputStream

/** Reads CSV/TSV with the same quote rules, or one complete vocabulary row per TXT line. */
class DelimitedTextVocabularyReader(
    private val type: VocabularyFileType,
    private val maxRows: Int = 20_000,
) : VocabularyRowReader {
    override fun rows(input: InputStream): Sequence<RawVocabularyRow> {
        val text = TextDocumentDecoder.decode(input.readBytes()).text.removePrefix("\uFEFF")
        val parsed = when (type) {
            VocabularyFileType.TXT -> parseTxt(text)
            VocabularyFileType.TSV -> parseDelimited(text, '\t')
            VocabularyFileType.CSV -> parseDelimited(text, ',')
            VocabularyFileType.XLSX -> error("XLSX must be handled by XlsxVocabularyReader")
        }
        if (parsed.isEmpty()) throw EmptyVocabularyFileException()
        return parsed.asSequence()
    }

    private fun parseTxt(text: String): List<RawVocabularyRow> {
        val lines = text.split(Regex("\\r\\n|\\n|\\r"))
            .filter(String::isNotBlank)
        if (lines.isEmpty()) return emptyList()
        val delimiter = inferTxtDelimiter(lines)
        return if (delimiter != null) parseDelimited(text, delimiter) else {
            lines.take(maxRows + 1).mapIndexed { index, line ->
                if (index >= maxRows) throw ImportLimitException(maxRows)
                RawVocabularyRow(index + 1, listOf(line))
            }
        }
    }

    /**
     * TXT is commonly exported as a plain line list, but spreadsheet tools also
     * produce comma-, pipe-, or tab-separated text while retaining a .txt suffix.
     * Infer only a stable delimiter across every non-empty line; semicolons are
     * intentionally excluded because they represent English variants in MIearn.
     */
    private fun inferTxtDelimiter(lines: List<String>): Char? {
        val candidates = listOf('\t', ',', '|')
        return candidates.firstOrNull { delimiter ->
            val counts = lines.map { line -> line.count { it == delimiter } }
            counts.firstOrNull()?.let { first -> first > 0 && counts.all { it == first } } == true
        }
    }

    private fun parseDelimited(text: String, delimiter: Char): List<RawVocabularyRow> {
        val rows = mutableListOf<RawVocabularyRow>()
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        var logicalRow = 1

        fun finishCell() {
            cells += cell.toString()
            cell.setLength(0)
        }

        fun finishRow() {
            finishCell()
            if (cells.any { it.isNotBlank() }) {
                if (rows.size >= maxRows) throw ImportLimitException(maxRows)
                rows += RawVocabularyRow(logicalRow, cells.toList())
            }
            cells.clear()
            logicalRow++
        }

        while (index < text.length) {
            val ch = text[index]
            if (inQuotes) {
                when {
                    ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        cell.append('"')
                        index += 2
                    }
                    ch == '"' -> {
                        inQuotes = false
                        index++
                    }
                    else -> {
                        cell.append(ch)
                        index++
                    }
                }
            } else {
                when (ch) {
                    '"' -> {
                        inQuotes = true
                        index++
                    }
                    delimiter -> {
                        finishCell()
                        index++
                    }
                    '\r' -> {
                        finishRow()
                        index++
                        if (index < text.length && text[index] == '\n') index++
                    }
                    '\n' -> {
                        finishRow()
                        index++
                    }
                    else -> {
                        cell.append(ch)
                        index++
                    }
                }
            }
        }
        if (inQuotes) throw CorruptVocabularyFileException()
        if (cell.isNotEmpty() || cells.isNotEmpty()) finishRow()
        return rows
    }
}
